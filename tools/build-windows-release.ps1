param(
    [string]$Version = "0.1.1",
    [string]$PrinterDriverSource = "",
    [switch]$SkipDockerBuild
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$frontendRoot = Join-Path $projectRoot "frontend"
$tauriRoot = Join-Path $frontendRoot "src-tauri"
$driverTarget = Join-Path $tauriRoot "resources\printer-drivers"
$imageArchive = Join-Path $projectRoot "desktop\runtime\images\cafehelp-images.tar"
$tauriConfig = Get-Content (Join-Path $tauriRoot "tauri.conf.json") -Raw | ConvertFrom-Json
if ($tauriConfig.version -ne $Version) {
    throw "Версия сборки $Version не совпадает с версией Tauri $($tauriConfig.version)"
}

function Require-Command([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Не найдена обязательная команда: $Name"
    }
}

Require-Command "docker"
Require-Command "npm"

$cargoBin = Join-Path $env:USERPROFILE ".cargo\bin"
if (-not (Test-Path (Join-Path $cargoBin "cargo.exe"))) {
    throw "Rust toolchain не установлен. Установите Rustup с MSVC toolchain."
}
$env:Path = "$cargoBin;$env:Path"

if ($PrinterDriverSource) {
    $driverNames = @("RongTaDriverInstall V2.65.exe", "DriverInstallArm64_V1.03.exe")
    foreach ($driverName in $driverNames) {
        $source = Join-Path $PrinterDriverSource $driverName
        if (-not (Test-Path -LiteralPath $source)) {
            throw "Не найден драйвер: $source"
        }
        $signature = Get-AuthenticodeSignature -LiteralPath $source
        if ($signature.Status -ne "Valid") {
            throw "Цифровая подпись драйвера недействительна: $driverName"
        }
        Copy-Item -LiteralPath $source -Destination $driverTarget -Force
    }
}

$backendImage = "cafehelp-backend:$Version"
$pythonImage = "cafehelp-pymodule:$Version"
$vkBotImage = "cafehelp-vkbot:$Version"
$postgresImage = "postgres:16-alpine"
$minioImage = "minio/minio:RELEASE.2025-09-07T16-13-09Z"

if (-not $SkipDockerBuild) {
    docker build --file (Join-Path $projectRoot "Dockerfile.backend") --tag $backendImage $projectRoot
    docker build --file (Join-Path $projectRoot "PyModule\Dockerfile") --tag $pythonImage (Join-Path $projectRoot "PyModule")
    docker build --file (Join-Path $projectRoot "vkbot\Dockerfile") --tag $vkBotImage (Join-Path $projectRoot "vkbot")
    docker pull $postgresImage
    docker pull $minioImage
}

$requiredImages = @($backendImage, $pythonImage, $vkBotImage, $postgresImage, $minioImage)
foreach ($image in $requiredImages) {
    docker image inspect $image *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "Не найден Docker-образ для offline-сборки: $image"
    }
}

if (Test-Path -LiteralPath $imageArchive) {
    Remove-Item -LiteralPath $imageArchive -Force
}
docker save --output $imageArchive $requiredImages
if ($LASTEXITCODE -ne 0) {
    throw "Не удалось сформировать offline image-pack"
}

Push-Location $frontendRoot
try {
    npm ci
    npm run desktop:build
} finally {
    Pop-Location
}

$installer = Join-Path $tauriRoot "target\release\bundle\nsis\CafeHelp_${Version}_x64-setup.exe"
if (-not (Test-Path -LiteralPath $installer)) {
    throw "NSIS installer не найден после сборки: $installer"
}

Write-Host "CafeHelp offline installer: $installer"
