@echo off
setlocal

where docker >nul 2>nul
if errorlevel 1 (
  echo Docker is not installed or not in PATH.
  exit /b 1
)

echo Starting Cafehelp stack...
docker compose up --build

endlocal
