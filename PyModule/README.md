# PyModule

Внутренний FastAPI-сервис CafeHelp для аналитики, прогнозирования, обучения
модели и вспомогательных ML-операций. Печать чеков в PyModule не входит.

## Быстрый старт

1. Создать окружение и установить зависимости:

```bat
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
```

2. Задать окружение:

```env
INTERNAL_SERVICE_TOKEN=<секрет не короче 24 символов>
INTERNAL_API_CONTRACT_VERSION=1
JAVA_API_URL=http://localhost:8080
JAVA_API_TIMEOUT_SECONDS=30
```

3. Запустить API:

```bat
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

## API

- `GET /health` — liveness процесса;
- `GET /ready` — готовность API, модели и Java-клиента;
- `/api/ml/*` — прогноз, обучение, генерация и оптимизация;
- `/api/analytics/*` — KPI, топ блюд, тренды и инсайты.

Маршруты `/api/ml/*` и `/api/analytics/*` требуют `X-Service-Token` и передают
`X-Contract-Version: 1`. Подробный контракт: `../docs/PYMODULE_CONTRACT.md`.

## Тесты

```bat
python -m pytest -q
```

## Структура

- `app/` — FastAPI, клиент Java API и ML/аналитические сервисы;
- `tests/` — контрактные и модульные тесты;
- `models/` — локальные артефакты модей (`*.pkl`, не коммитятся);
- `app/script/` — вспомогательные скрипты.

## Важно

- Java backend — единственный владелец бизнес-транзакций и данных;
- отказ PyModule не должен блокировать кассу, склад или смены;
- ML-модели сохраняются в `PyModule/models`.
