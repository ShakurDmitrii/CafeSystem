# CafeHelp local runtime

Этот Compose-файл предназначен для установленного Tauri launcher и не публикует PostgreSQL, налоговую БД, MinIO или PyModule на порты Windows. На `127.0.0.1:8080` доступен только Java API.

Launcher создаёт `runtime.env` с локальными секретами в каталоге данных приложения и передаёт его через `docker compose --env-file`.

Release-сборка обязана заранее загрузить или приложить версии образов:

- `cafehelp-backend:0.1.0`
- `cafehelp-pymodule:0.1.0`
- `cafehelp-vkbot:0.1.0`
- `postgres:16-alpine`
- `minio/minio:RELEASE.2025-09-07T16-13-09Z`

Рабочие данные находятся только в именованных Docker volumes и не удаляются при обновлении launcher. Launcher создаёт отсутствующие volumes до запуска Compose и подключает существующие как внешние, поэтому данные старых версий сохраняются даже без Compose-меток.

VK-бот упакован в installer, но по умолчанию выключен через Compose-профиль `vkbot`.
Owner включает его в разделе «Система» и указывает ID и токен сообщества. Внутренний
токен доступа к Java API создаётся launcher автоматически и не показывается пользователю.
