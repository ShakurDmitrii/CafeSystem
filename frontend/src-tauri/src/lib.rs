use chrono::{SecondsFormat, Utc};
use serde::Serialize;
use sha2::{Digest, Sha256};
use std::fs::{self, File};
use std::io::{self, BufReader, BufWriter, Read, Write};
use std::path::{Path, PathBuf};
use std::process::{Command, Output, Stdio};
use tauri::{AppHandle, Manager};
use tempfile::Builder as TempBuilder;
use uuid::Uuid;
use walkdir::WalkDir;
use zip::write::SimpleFileOptions;

const CREATE_NO_WINDOW: u32 = 0x0800_0000;
const MAIN_DB_CONTAINER: &str = "cafehelp-db";
const TAX_DB_CONTAINER: &str = "cafehelp-tax-db";
const MINIO_CONTAINER: &str = "cafehelp-minio";
const PYMODULE_CONTAINER: &str = "cafehelp-pymodule";
const VKBOT_CONTAINER: &str = "cafehelp-vkbot";
const POSTGRES_IMAGE: &str = "postgres:16-alpine";
const MINIO_IMAGE: &str = "minio/minio:RELEASE.2025-09-07T16-13-09Z";
const RUNTIME_VOLUMES: [&str; 4] = [
    "cafehelp_pg_data",
    "cafehelp_tax_pg_data",
    "cafehelp_minio_data",
    "cafehelp_pymodule_models",
];

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct CheckResult {
    available: bool,
    detail: String,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct SystemStatus {
    platform: String,
    architecture: String,
    docker_cli: CheckResult,
    docker_engine: CheckResult,
    docker_compose: CheckResult,
    wsl: CheckResult,
    services: Vec<ServiceStatus>,
    printer_driver_available: bool,
    vk_bot: VkBotSettings,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct ServiceStatus {
    name: String,
    running: bool,
    status: String,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct OperationResult {
    success: bool,
    message: String,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct VkBotSettings {
    enabled: bool,
    group_id: String,
    group_token_configured: bool,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct BackupResult {
    path: String,
    created_at: String,
    size_bytes: u64,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct BackupManifest {
    format_version: u32,
    application: &'static str,
    application_version: &'static str,
    created_at: String,
    components: Vec<&'static str>,
}

fn hidden_command(program: impl AsRef<std::ffi::OsStr>) -> Command {
    let mut command = Command::new(program);
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        command.creation_flags(CREATE_NO_WINDOW);
    }
    command
}

fn command_output(program: &str, args: &[&str]) -> io::Result<Output> {
    hidden_command(program).args(args).output()
}

fn first_non_empty_line(bytes: &[u8]) -> Option<String> {
    String::from_utf8_lossy(bytes)
        .lines()
        .map(str::trim)
        .find(|line| !line.is_empty())
        .map(ToOwned::to_owned)
}

fn read_env_value<'a>(content: &'a str, key: &str) -> Option<&'a str> {
    content.lines().find_map(|line| {
        let (name, value) = line.trim().split_once('=')?;
        (name == key).then_some(value)
    })
}

fn set_env_value(content: &str, key: &str, value: &str) -> String {
    let replacement = format!("{key}={value}");
    let mut found = false;
    let mut lines: Vec<String> = content
        .lines()
        .map(|line| {
            if line
                .trim()
                .split_once('=')
                .is_some_and(|(name, _)| name == key)
            {
                found = true;
                replacement.clone()
            } else {
                line.to_string()
            }
        })
        .collect();
    if !found {
        lines.push(replacement);
    }
    format!("{}\n", lines.join("\n"))
}

fn vk_bot_settings_from_env(content: &str) -> VkBotSettings {
    VkBotSettings {
        enabled: read_env_value(content, "VK_BOT_ENABLED")
            .is_some_and(|value| value.eq_ignore_ascii_case("true")),
        group_id: read_env_value(content, "VK_GROUP_ID")
            .unwrap_or_default()
            .trim()
            .to_string(),
        group_token_configured: read_env_value(content, "VK_GROUP_TOKEN")
            .is_some_and(|value| !value.trim().is_empty()),
    }
}

fn validate_vk_group_id(value: &str) -> Result<String, String> {
    let normalized = value.trim();
    if normalized.is_empty() || !normalized.chars().all(|character| character.is_ascii_digit()) {
        return Err("ID сообщества VK должен содержать только цифры".to_string());
    }
    if normalized == "0" {
        return Err("ID сообщества VK должен быть больше нуля".to_string());
    }
    Ok(normalized.to_string())
}

fn validate_vk_group_token(value: &str) -> Result<String, String> {
    let normalized = value.trim();
    if normalized.is_empty() {
        return Err("Укажите токен сообщества VK".to_string());
    }
    if normalized.len() > 4096
        || !normalized.chars().all(|character| {
            character.is_ascii_alphanumeric() || matches!(character, '.' | '_' | '-')
        })
    {
        return Err("Токен сообщества VK содержит недопустимые символы".to_string());
    }
    Ok(normalized.to_string())
}

fn compose_failure_message(stdout: &[u8], stderr: &[u8]) -> String {
    let stdout_text = String::from_utf8_lossy(stdout);
    let stderr_text = String::from_utf8_lossy(stderr);
    let lines: Vec<&str> = stdout_text
        .lines()
        .chain(stderr_text.lines())
        .map(str::trim)
        .filter(|line| !line.is_empty())
        .filter(|line| !line.contains("level=warning") && !line.contains("Found orphan"))
        .collect();
    lines
        .iter()
        .rev()
        .find(|line| {
            let lower = line.to_ascii_lowercase();
            lower.contains("error")
                || lower.contains("failed")
                || lower.contains("unhealthy")
                || lower.contains("exited")
        })
        .or_else(|| lines.last())
        .map(|line| (*line).to_string())
        .unwrap_or_else(|| "Docker Compose завершился с ошибкой".to_string())
}

fn check_command(program: &str, args: &[&str]) -> CheckResult {
    match command_output(program, args) {
        Ok(output) if output.status.success() => CheckResult {
            available: true,
            detail: first_non_empty_line(&output.stdout)
                .or_else(|| first_non_empty_line(&output.stderr))
                .unwrap_or_else(|| "Доступно".to_string()),
        },
        Ok(output) => CheckResult {
            available: false,
            detail: first_non_empty_line(&output.stderr)
                .or_else(|| first_non_empty_line(&output.stdout))
                .unwrap_or_else(|| format!("Код завершения: {}", output.status)),
        },
        Err(error) => CheckResult {
            available: false,
            detail: error.to_string(),
        },
    }
}

struct PausedContainers(Vec<String>);

impl PausedContainers {
    fn pause(names: &[&str]) -> Result<Self, String> {
        let mut paused: Vec<String> = Vec::new();
        for name in names {
            ensure_container_running(name)?;
            let result =
                command_output("docker", &["pause", name]).map_err(|error| error.to_string())?;
            if !result.status.success() {
                for paused_name in paused.iter().rev() {
                    let _ = command_output("docker", &["unpause", paused_name]);
                }
                return Err(first_non_empty_line(&result.stderr)
                    .unwrap_or_else(|| format!("Не удалось приостановить контейнер {name}")));
            }
            paused.push((*name).to_string());
        }
        Ok(Self(paused))
    }
}

impl Drop for PausedContainers {
    fn drop(&mut self) {
        for name in self.0.iter().rev() {
            let _ = command_output("docker", &["unpause", name]);
        }
    }
}

fn service_statuses(vk_bot_enabled: bool) -> Vec<ServiceStatus> {
    let mut expected = vec![
        MAIN_DB_CONTAINER,
        TAX_DB_CONTAINER,
        "cafehelp-backend",
        "cafehelp-pymodule",
        MINIO_CONTAINER,
    ];
    if vk_bot_enabled {
        expected.push(VKBOT_CONTAINER);
    }
    let rows = command_output(
        "docker",
        &["ps", "-a", "--format", "{{.Names}}|{{.Status}}"],
    )
    .ok()
    .filter(|result| result.status.success())
    .map(|result| String::from_utf8_lossy(&result.stdout).into_owned())
    .unwrap_or_default();
    expected
        .iter()
        .map(|name| {
            let status = rows
                .lines()
                .filter_map(|line| line.split_once('|'))
                .find(|(container, _)| container == name)
                .map(|(_, status)| status.trim().to_string())
                .unwrap_or_else(|| "Не создан".to_string());
            ServiceStatus {
                name: (*name).to_string(),
                running: status.starts_with("Up ") || status == "Up",
                status,
            }
        })
        .collect()
}

fn runtime_dir(app: &AppHandle) -> Result<PathBuf, String> {
    if let Ok(override_path) = std::env::var("CAFEHELP_RUNTIME_DIR") {
        let path = PathBuf::from(override_path);
        if path.join("docker-compose.yml").is_file() {
            return Ok(path);
        }
    }
    if cfg!(debug_assertions) {
        let path = Path::new(env!("CARGO_MANIFEST_DIR"))
            .join("..")
            .join("..")
            .join("desktop")
            .join("runtime");
        if path.join("docker-compose.yml").is_file() {
            return path.canonicalize().map_err(|error| error.to_string());
        }
    }
    let path = app
        .path()
        .resource_dir()
        .map_err(|error| error.to_string())?
        .join("runtime");
    if path.join("docker-compose.yml").is_file() {
        Ok(path)
    } else {
        Err("Файлы локального окружения CafeHelp не найдены".to_string())
    }
}

fn config_dir(app: &AppHandle) -> Result<PathBuf, String> {
    let path = app
        .path()
        .app_data_dir()
        .map_err(|error| error.to_string())?;
    fs::create_dir_all(&path).map_err(|error| error.to_string())?;
    Ok(path)
}

fn ensure_runtime_env(app: &AppHandle) -> Result<PathBuf, String> {
    let path = config_dir(app)?.join("runtime.env");
    if path.is_file() {
        let mut content = fs::read_to_string(&path)
            .map_err(|error| format!("Не удалось прочитать локальные настройки: {error}"))?;
        let secret = || format!("{}{}", Uuid::new_v4().simple(), Uuid::new_v4().simple());
        let defaults = [
            ("VK_BOT_ENABLED", "false".to_string()),
            ("VK_GROUP_ID", String::new()),
            ("VK_GROUP_TOKEN", String::new()),
            ("VK_BOT_API_TOKEN", secret()),
            (
                "CAFEHELP_VKBOT_IMAGE",
                format!("cafehelp-vkbot:{}", env!("CARGO_PKG_VERSION")),
            ),
        ];
        let original = content.clone();
        for (key, value) in defaults {
            if read_env_value(&content, key).is_none() {
                content = set_env_value(&content, key, &value);
            }
        }
        if content != original {
            fs::write(&path, content).map_err(|error| error.to_string())?;
        }
        return Ok(path);
    }
    let secret = || format!("{}{}", Uuid::new_v4().simple(), Uuid::new_v4().simple());
    let content = format!(
        concat!(
            "COMPOSE_PROJECT_NAME=cafehelp\n",
            "POSTGRES_PASSWORD={}\n",
            "TAX_POSTGRES_PASSWORD={}\n",
            "INTERNAL_SERVICE_TOKEN={}\n",
            "SECURITY_JWT_SECRET={}\n",
            "MINIO_ROOT_USER=cafehelp\n",
            "MINIO_ROOT_PASSWORD={}\n",
            "MINIO_BUCKET=cafehelp-files\n",
            "TAX_PROVIDER=SAFE\n",
            "VK_BOT_ENABLED=false\n",
            "VK_GROUP_ID=\n",
            "VK_GROUP_TOKEN=\n",
            "VK_BOT_API_TOKEN={}\n",
            "CAFEHELP_BACKEND_IMAGE=cafehelp-backend:{}\n",
            "CAFEHELP_PYMODULE_IMAGE=cafehelp-pymodule:{}\n",
            "CAFEHELP_VKBOT_IMAGE=cafehelp-vkbot:{}\n"
        ),
        secret(),
        secret(),
        secret(),
        secret(),
        secret(),
        secret(),
        env!("CARGO_PKG_VERSION"),
        env!("CARGO_PKG_VERSION"),
        env!("CARGO_PKG_VERSION")
    );
    fs::write(&path, content).map_err(|error| error.to_string())?;
    Ok(path)
}

fn ensure_runtime_images(app: &AppHandle, runtime: &Path) -> Result<(), String> {
    let archive = runtime.join("images").join("cafehelp-images.tar");
    if !archive.is_file() {
        return Ok(());
    }
    let marker = config_dir(app)?.join(format!("images-{}.loaded", env!("CARGO_PKG_VERSION")));
    let version = env!("CARGO_PKG_VERSION");
    let required_images = [
        POSTGRES_IMAGE.to_string(),
        MINIO_IMAGE.to_string(),
        format!("cafehelp-backend:{version}"),
        format!("cafehelp-pymodule:{version}"),
        format!("cafehelp-vkbot:{version}"),
    ];
    let images_are_available = required_images.iter().all(|image| {
        command_output("docker", &["image", "inspect", image])
            .map(|output| output.status.success())
            .unwrap_or(false)
    });
    if marker.is_file() && images_are_available {
        return Ok(());
    }
    let archive_arg = archive.to_string_lossy().into_owned();
    let output = hidden_command("docker")
        .args(["load", "--input", &archive_arg])
        .output()
        .map_err(|error| format!("Не удалось загрузить offline-образы CafeHelp: {error}"))?;
    if !output.status.success() {
        return Err(first_non_empty_line(&output.stderr)
            .unwrap_or_else(|| "Docker не смог загрузить offline-образы CafeHelp".to_string()));
    }
    fs::write(marker, Utc::now().to_rfc3339()).map_err(|error| error.to_string())?;
    Ok(())
}

fn ensure_runtime_volumes() -> Result<(), String> {
    for volume in RUNTIME_VOLUMES {
        let inspect = command_output("docker", &["volume", "inspect", volume])
            .map_err(|error| format!("Не удалось проверить Docker-том {volume}: {error}"))?;
        if inspect.status.success() {
            continue;
        }
        let create = command_output("docker", &["volume", "create", volume])
            .map_err(|error| format!("Не удалось создать Docker-том {volume}: {error}"))?;
        if !create.status.success() {
            return Err(first_non_empty_line(&create.stderr)
                .unwrap_or_else(|| format!("Docker не смог создать том {volume}")));
        }
    }
    Ok(())
}

fn synchronize_postgres_password(
    container: &str,
    database: &str,
    password: &str,
) -> Result<(), String> {
    let escaped_password = password.replace('\'', "''");
    let mut child = hidden_command("docker")
        .args([
            "exec",
            "-i",
            container,
            "psql",
            "-U",
            "postgres",
            "-d",
            database,
            "-v",
            "ON_ERROR_STOP=1",
        ])
        .stdin(Stdio::piped())
        .stdout(Stdio::null())
        .stderr(Stdio::piped())
        .spawn()
        .map_err(|error| format!("Не удалось открыть PostgreSQL в {container}: {error}"))?;
    child
        .stdin
        .take()
        .ok_or_else(|| format!("Не удалось передать команду PostgreSQL в {container}"))?
        .write_all(format!("ALTER ROLE postgres WITH PASSWORD '{escaped_password}';\n").as_bytes())
        .map_err(|error| format!("Не удалось синхронизировать пароль {container}: {error}"))?;
    let output = child
        .wait_with_output()
        .map_err(|error| format!("Не удалось дождаться PostgreSQL в {container}: {error}"))?;
    if output.status.success() {
        Ok(())
    } else {
        Err(first_non_empty_line(&output.stderr)
            .unwrap_or_else(|| format!("PostgreSQL в {container} отклонил смену пароля")))
    }
}

fn run_compose(runtime: &Path, args: &[String]) -> Result<Output, String> {
    hidden_command("docker")
        .args(args)
        .env("COMPOSE_IGNORE_ORPHANS", "true")
        .current_dir(runtime)
        .output()
        .map_err(|error| format!("Не удалось запустить Docker Compose: {error}"))
}

fn compose_args(runtime: &Path, env_file: &Path, action: &[&str]) -> Vec<String> {
    let mut args = vec![
        "compose".to_string(),
        "--env-file".to_string(),
        env_file.to_string_lossy().into_owned(),
        "-f".to_string(),
        runtime
            .join("docker-compose.yml")
            .to_string_lossy()
            .into_owned(),
    ];
    args.extend(action.iter().map(|value| (*value).to_string()));
    args
}

fn printer_driver_path(app: &AppHandle) -> Result<PathBuf, String> {
    let filename = if std::env::consts::ARCH == "aarch64" {
        "DriverInstallArm64_V1.03.exe"
    } else {
        "RongTaDriverInstall V2.65.exe"
    };
    if let Ok(directory) = std::env::var("CAFEHELP_PRINTER_DRIVER_DIR") {
        let path = PathBuf::from(directory).join(filename);
        if path.is_file() {
            return Ok(path);
        }
    }
    let path = app
        .path()
        .resource_dir()
        .map_err(|error| error.to_string())?
        .join("printer-drivers")
        .join(filename);
    if path.is_file() {
        Ok(path)
    } else {
        Err(format!(
            "Установщик драйвера {filename} не включён в сборку"
        ))
    }
}

#[tauri::command]
async fn get_system_status(app: AppHandle) -> SystemStatus {
    let mut wsl = check_command("wsl", &["--status"]);
    if wsl.available {
        wsl.detail = "WSL 2 доступен".to_string();
    }
    let vk_bot = ensure_runtime_env(&app)
        .and_then(|path| fs::read_to_string(path).map_err(|error| error.to_string()))
        .map(|content| vk_bot_settings_from_env(&content))
        .unwrap_or(VkBotSettings {
            enabled: false,
            group_id: String::new(),
            group_token_configured: false,
        });
    SystemStatus {
        platform: std::env::consts::OS.to_string(),
        architecture: std::env::consts::ARCH.to_string(),
        docker_cli: check_command("docker", &["--version"]),
        docker_engine: check_command("docker", &["info", "--format", "{{.ServerVersion}}"]),
        docker_compose: check_command("docker", &["compose", "version", "--short"]),
        wsl,
        services: service_statuses(vk_bot.enabled),
        printer_driver_available: printer_driver_path(&app).is_ok(),
        vk_bot,
    }
}

#[tauri::command]
async fn configure_vk_bot(
    app: AppHandle,
    enabled: bool,
    group_id: String,
    group_token: Option<String>,
) -> Result<OperationResult, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let path = ensure_runtime_env(&app)?;
        let mut content = fs::read_to_string(&path)
            .map_err(|error| format!("Не удалось прочитать локальные настройки: {error}"))?;
        let normalized_group_id = if group_id.trim().is_empty() {
            String::new()
        } else {
            validate_vk_group_id(&group_id)?
        };
        let normalized_token = group_token
            .as_deref()
            .map(validate_vk_group_token)
            .transpose()?;
        let token_configured = normalized_token
            .as_ref()
            .is_some_and(|value| !value.is_empty())
            || read_env_value(&content, "VK_GROUP_TOKEN")
                .is_some_and(|value| !value.trim().is_empty());

        if enabled && normalized_group_id.is_empty() {
            return Err("Укажите ID сообщества VK перед включением бота".to_string());
        }
        if enabled && !token_configured {
            return Err("Укажите токен сообщества VK перед включением бота".to_string());
        }

        content = set_env_value(
            &content,
            "VK_BOT_ENABLED",
            if enabled { "true" } else { "false" },
        );
        content = set_env_value(&content, "VK_GROUP_ID", &normalized_group_id);
        if let Some(token) = normalized_token {
            content = set_env_value(&content, "VK_GROUP_TOKEN", &token);
        }
        fs::write(path, content)
            .map_err(|error| format!("Не удалось сохранить настройки VK-бота: {error}"))?;

        Ok(OperationResult {
            success: true,
            message: if enabled {
                "Настройки VK-бота сохранены. Запускаем интеграцию…".to_string()
            } else {
                "VK-бот выключен".to_string()
            },
        })
    })
    .await
    .map_err(|error| error.to_string())?
}

#[tauri::command]
async fn start_local_services(app: AppHandle) -> Result<OperationResult, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let runtime = runtime_dir(&app)?;
        let env_file = ensure_runtime_env(&app)?;
        ensure_runtime_images(&app, &runtime)?;
        ensure_runtime_volumes()?;
        let infrastructure_args = compose_args(
            &runtime,
            &env_file,
            &[
                "up",
                "-d",
                "--wait",
                "--wait-timeout",
                "120",
                "db",
                "tax_db",
                "minio",
            ],
        );
        let infrastructure = run_compose(&runtime, &infrastructure_args)?;
        if !infrastructure.status.success() {
            return Err(compose_failure_message(
                &infrastructure.stdout,
                &infrastructure.stderr,
            ));
        }

        let env_content = fs::read_to_string(&env_file)
            .map_err(|error| format!("Не удалось прочитать локальные настройки: {error}"))?;
        let main_password = read_env_value(&env_content, "POSTGRES_PASSWORD")
            .ok_or_else(|| "В локальных настройках отсутствует пароль основной БД".to_string())?;
        let tax_password = read_env_value(&env_content, "TAX_POSTGRES_PASSWORD")
            .ok_or_else(|| "В локальных настройках отсутствует пароль налоговой БД".to_string())?;
        synchronize_postgres_password(MAIN_DB_CONTAINER, "postgres", main_password)?;
        synchronize_postgres_password(TAX_DB_CONTAINER, "taxdb", tax_password)?;

        let args = compose_args(
            &runtime,
            &env_file,
            &["up", "-d", "--wait", "--wait-timeout", "180"],
        );
        let output = run_compose(&runtime, &args)?;
        if !output.status.success() {
            return Err(compose_failure_message(&output.stdout, &output.stderr));
        }

        let vk_bot = vk_bot_settings_from_env(&env_content);
        if vk_bot.enabled {
            validate_vk_group_id(&vk_bot.group_id)?;
            if !vk_bot.group_token_configured {
                return Err("VK-бот включён, но токен сообщества не настроен".to_string());
            }
            let bot_args = compose_args(
                &runtime,
                &env_file,
                &[
                    "--profile",
                    "vkbot",
                    "up",
                    "-d",
                    "--wait",
                    "--wait-timeout",
                    "180",
                    "vkbot",
                ],
            );
            let bot_output = run_compose(&runtime, &bot_args)?;
            if !bot_output.status.success() {
                return Err(compose_failure_message(
                    &bot_output.stdout,
                    &bot_output.stderr,
                ));
            }
        } else {
            let stop_args = compose_args(
                &runtime,
                &env_file,
                &["--profile", "vkbot", "stop", "vkbot"],
            );
            let stop_output = run_compose(&runtime, &stop_args)?;
            if !stop_output.status.success() {
                return Err(compose_failure_message(
                    &stop_output.stdout,
                    &stop_output.stderr,
                ));
            }
        }
        Ok(OperationResult {
            success: true,
            message: if vk_bot.enabled {
                "Локальные сервисы CafeHelp и VK-бот запущены".to_string()
            } else {
                "Локальные сервисы CafeHelp запущены, VK-бот выключен".to_string()
            },
        })
    })
    .await
    .map_err(|error| error.to_string())?
}

fn ensure_container_running(name: &str) -> Result<(), String> {
    let output = command_output("docker", &["inspect", "-f", "{{.State.Running}}", name])
        .map_err(|e| e.to_string())?;
    if output.status.success() && String::from_utf8_lossy(&output.stdout).trim() == "true" {
        Ok(())
    } else {
        Err(format!("Контейнер {name} не запущен"))
    }
}

fn dump_database(container: &str, database: &str, output_path: &Path) -> Result<(), String> {
    ensure_container_running(container)?;
    let output_file = File::create(output_path).map_err(|e| e.to_string())?;
    let result = hidden_command("docker")
        .args([
            "exec", container, "pg_dump", "-U", "postgres", "-d", database, "-Fc",
        ])
        .stdout(Stdio::from(output_file))
        .stderr(Stdio::piped())
        .output()
        .map_err(|e| format!("Не удалось запустить pg_dump: {e}"))?;
    if result.status.success() {
        Ok(())
    } else {
        Err(first_non_empty_line(&result.stderr)
            .unwrap_or_else(|| format!("Не удалось создать дамп базы {database}")))
    }
}

fn copy_container_directory(
    container: &str,
    source: &str,
    output_dir: &Path,
) -> Result<(), String> {
    ensure_container_running(container)?;
    fs::create_dir_all(output_dir).map_err(|e| e.to_string())?;
    let destination = output_dir.to_string_lossy().into_owned();
    let result = hidden_command("docker")
        .args(["cp", &format!("{container}:{source}"), &destination])
        .output()
        .map_err(|e| format!("Не удалось скопировать данные контейнера {container}: {e}"))?;
    if result.status.success() {
        Ok(())
    } else {
        Err(first_non_empty_line(&result.stderr)
            .unwrap_or_else(|| format!("Не удалось скопировать данные контейнера {container}")))
    }
}

fn sha256(path: &Path) -> Result<String, String> {
    let mut reader = BufReader::new(File::open(path).map_err(|e| e.to_string())?);
    let mut digest = Sha256::new();
    let mut buffer = [0_u8; 64 * 1024];
    loop {
        let count = reader.read(&mut buffer).map_err(|e| e.to_string())?;
        if count == 0 {
            break;
        }
        digest.update(&buffer[..count]);
    }
    Ok(format!("{:x}", digest.finalize()))
}

fn relative_files(root: &Path) -> Result<Vec<(PathBuf, String)>, String> {
    let mut files = Vec::new();
    for entry in WalkDir::new(root) {
        let entry = entry.map_err(|e| e.to_string())?;
        if entry.file_type().is_file() {
            let relative = entry
                .path()
                .strip_prefix(root)
                .map_err(|e| e.to_string())?
                .to_string_lossy()
                .replace('\\', "/");
            files.push((entry.path().to_path_buf(), relative));
        }
    }
    files.sort_by(|left, right| left.1.cmp(&right.1));
    Ok(files)
}

fn write_checksums(root: &Path) -> Result<(), String> {
    let lines = relative_files(root)?
        .into_iter()
        .filter(|(_, relative)| relative != "checksums.sha256")
        .map(|(path, relative)| Ok(format!("{}  {}", sha256(&path)?, relative)))
        .collect::<Result<Vec<String>, String>>()?;
    fs::write(
        root.join("checksums.sha256"),
        format!("{}\n", lines.join("\n")),
    )
    .map_err(|e| e.to_string())
}

fn zip_directory(source: &Path, destination: &Path) -> Result<(), String> {
    let file = File::create(destination).map_err(|e| e.to_string())?;
    let mut archive = zip::ZipWriter::new(BufWriter::new(file));
    let options = SimpleFileOptions::default()
        .compression_method(zip::CompressionMethod::Deflated)
        .unix_permissions(0o600);
    for (path, relative) in relative_files(source)? {
        archive
            .start_file(relative, options)
            .map_err(|e| e.to_string())?;
        let mut input = BufReader::new(File::open(path).map_err(|e| e.to_string())?);
        io::copy(&mut input, &mut archive).map_err(|e| e.to_string())?;
    }
    archive.finish().map_err(|e| e.to_string())?;
    Ok(())
}

fn verify_zip(path: &Path) -> Result<(), String> {
    let file = File::open(path).map_err(|error| error.to_string())?;
    let mut archive = zip::ZipArchive::new(file).map_err(|error| error.to_string())?;
    let mut buffer = [0_u8; 64 * 1024];
    for index in 0..archive.len() {
        let mut entry = archive.by_index(index).map_err(|error| error.to_string())?;
        loop {
            let count = entry.read(&mut buffer).map_err(|error| error.to_string())?;
            if count == 0 {
                break;
            }
        }
    }
    Ok(())
}

fn normalized_backup_path(requested: &str) -> Result<PathBuf, String> {
    if requested.trim().is_empty() {
        return Err("Не выбран путь для резервной копии".to_string());
    }
    let mut path = PathBuf::from(requested.trim());
    if path.extension().and_then(|value| value.to_str()) != Some("cafehelp-backup") {
        path.set_extension("cafehelp-backup");
    }
    let parent = path
        .parent()
        .filter(|value| !value.as_os_str().is_empty())
        .ok_or_else(|| "Некорректный путь резервной копии".to_string())?;
    fs::create_dir_all(parent).map_err(|e| e.to_string())?;
    Ok(path)
}

fn create_backup_blocking(destination: &str) -> Result<BackupResult, String> {
    let destination = normalized_backup_path(destination)?;
    let parent = destination.parent().expect("backup parent was validated");
    let workspace = TempBuilder::new()
        .prefix(".cafehelp-backup-")
        .tempdir_in(parent)
        .map_err(|e| e.to_string())?;
    let content = workspace.path().join("content");
    fs::create_dir_all(&content).map_err(|e| e.to_string())?;
    let _paused = PausedContainers::pause(&["cafehelp-backend", PYMODULE_CONTAINER])?;
    dump_database(MAIN_DB_CONTAINER, "postgres", &content.join("main.dump"))?;
    dump_database(TAX_DB_CONTAINER, "taxdb", &content.join("tax.dump"))?;
    copy_container_directory(MINIO_CONTAINER, "/data/.", &content.join("minio"))?;
    copy_container_directory(
        PYMODULE_CONTAINER,
        "/app/models/.",
        &content.join("pymodule-models"),
    )?;
    let created_at = Utc::now().to_rfc3339_opts(SecondsFormat::Secs, true);
    let manifest = BackupManifest {
        format_version: 1,
        application: "CafeHelp",
        application_version: env!("CARGO_PKG_VERSION"),
        created_at: created_at.clone(),
        components: vec!["main-database", "tax-database", "minio", "pymodule-models"],
    };
    fs::write(
        content.join("manifest.json"),
        serde_json::to_vec_pretty(&manifest).map_err(|e| e.to_string())?,
    )
    .map_err(|e| e.to_string())?;
    write_checksums(&content)?;
    let partial = workspace.path().join("backup.partial");
    zip_directory(&content, &partial)?;
    verify_zip(&partial)?;
    if destination.exists() {
        fs::remove_file(&destination).map_err(|e| e.to_string())?;
    }
    fs::rename(&partial, &destination).map_err(|e| e.to_string())?;
    let size_bytes = destination.metadata().map_err(|e| e.to_string())?.len();
    Ok(BackupResult {
        path: destination.to_string_lossy().into_owned(),
        created_at,
        size_bytes,
    })
}

#[tauri::command]
async fn create_backup(destination: String) -> Result<BackupResult, String> {
    tauri::async_runtime::spawn_blocking(move || create_backup_blocking(&destination))
        .await
        .map_err(|e| e.to_string())?
}

#[tauri::command]
async fn install_printer_driver(app: AppHandle) -> Result<OperationResult, String> {
    let path = printer_driver_path(&app)?;
    let escaped = path.to_string_lossy().replace('\'', "''");
    let command = format!("Start-Process -FilePath '{}' -Verb RunAs", escaped);
    hidden_command("powershell")
        .args(["-NoProfile", "-NonInteractive", "-Command", &command])
        .spawn()
        .map_err(|e| format!("Не удалось запустить установщик драйвера: {e}"))?;
    Ok(OperationResult {
        success: true,
        message: "Установщик драйвера запущен".to_string(),
    })
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .setup(|app| {
            if cfg!(debug_assertions) {
                app.handle().plugin(
                    tauri_plugin_log::Builder::default()
                        .level(log::LevelFilter::Info)
                        .build(),
                )?;
            }
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            get_system_status,
            configure_vk_bot,
            start_local_services,
            create_backup,
            install_printer_driver
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn runtime_tracks_every_persistent_volume() {
        assert_eq!(
            RUNTIME_VOLUMES,
            [
                "cafehelp_pg_data",
                "cafehelp_tax_pg_data",
                "cafehelp_minio_data",
                "cafehelp_pymodule_models"
            ]
        );
    }

    #[test]
    fn reads_runtime_secret_without_exposing_other_values() {
        let env = "POSTGRES_PASSWORD=main-secret\nTAX_POSTGRES_PASSWORD=tax-secret\n";
        assert_eq!(
            read_env_value(env, "POSTGRES_PASSWORD"),
            Some("main-secret")
        );
        assert_eq!(read_env_value(env, "MISSING"), None);
    }

    #[test]
    fn vk_bot_settings_are_disabled_without_credentials() {
        let settings = vk_bot_settings_from_env(
            "VK_BOT_ENABLED=false\nVK_GROUP_ID=\nVK_GROUP_TOKEN=\n",
        );
        assert!(!settings.enabled);
        assert!(settings.group_id.is_empty());
        assert!(!settings.group_token_configured);
    }

    #[test]
    fn updating_vk_settings_preserves_unrelated_runtime_secrets() {
        let original = "POSTGRES_PASSWORD=database-secret\nVK_BOT_ENABLED=false\n";
        let updated = set_env_value(original, "VK_BOT_ENABLED", "true");
        let updated = set_env_value(&updated, "VK_GROUP_ID", "12345");

        assert_eq!(read_env_value(&updated, "POSTGRES_PASSWORD"), Some("database-secret"));
        assert_eq!(read_env_value(&updated, "VK_BOT_ENABLED"), Some("true"));
        assert_eq!(read_env_value(&updated, "VK_GROUP_ID"), Some("12345"));
    }

    #[test]
    fn vk_credentials_reject_values_unsafe_for_compose_env() {
        assert!(validate_vk_group_id("12345").is_ok());
        assert!(validate_vk_group_id("club123").is_err());
        assert!(validate_vk_group_token("vk1.safe_TOKEN-123").is_ok());
        assert!(validate_vk_group_token("token\nINJECTED=value").is_err());
    }

    #[test]
    fn compose_failure_prefers_error_over_orphan_warning() {
        let stdout = b"dependency failed to start: container cafehelp-backend is unhealthy\n";
        let stderr = b"level=warning msg=\"Found orphan containers\"\n";
        assert_eq!(
            compose_failure_message(stdout, stderr),
            "dependency failed to start: container cafehelp-backend is unhealthy"
        );
    }

    #[test]
    fn backup_path_gets_cafehelp_extension() {
        let directory = tempfile::tempdir().expect("temporary directory");
        let path = normalized_backup_path(
            directory
                .path()
                .join("CafeHelp_2026-08-31")
                .to_string_lossy()
                .as_ref(),
        )
        .expect("normalized backup path");

        assert_eq!(
            path.extension().and_then(|value| value.to_str()),
            Some("cafehelp-backup")
        );
    }

    #[test]
    fn archive_contains_manifest_and_checksums() {
        let directory = tempfile::tempdir().expect("temporary directory");
        let content = directory.path().join("content");
        fs::create_dir_all(content.join("minio")).expect("content directory");
        fs::write(content.join("main.dump"), b"main database").expect("main dump");
        fs::write(content.join("tax.dump"), b"tax database").expect("tax dump");
        fs::write(content.join("minio").join("object.bin"), b"image").expect("minio object");
        fs::write(content.join("manifest.json"), b"{}").expect("manifest");

        write_checksums(&content).expect("checksums");
        let archive_path = directory.path().join("backup.cafehelp-backup");
        zip_directory(&content, &archive_path).expect("archive");
        verify_zip(&archive_path).expect("archive verification");

        let file = File::open(archive_path).expect("archive file");
        let mut archive = zip::ZipArchive::new(file).expect("zip archive");
        assert!(archive.by_name("main.dump").is_ok());
        assert!(archive.by_name("tax.dump").is_ok());
        assert!(archive.by_name("minio/object.bin").is_ok());
        assert!(archive.by_name("manifest.json").is_ok());
        assert!(archive.by_name("checksums.sha256").is_ok());
    }

    #[test]
    #[ignore = "requires running CafeHelp Docker containers"]
    fn creates_backup_from_running_containers() {
        let directory = tempfile::tempdir().expect("temporary directory");
        let destination = directory.path().join("integration.cafehelp-backup");

        let result = create_backup_blocking(destination.to_string_lossy().as_ref())
            .expect("backup from running containers");

        assert!(Path::new(&result.path).is_file());
        assert!(result.size_bytes > 0);
    }
}
