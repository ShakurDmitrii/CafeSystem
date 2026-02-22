# PyModule

Python-сервис для аналитики/ML и вспомогательных API.

## Быстрый старт

1. Создать окружение:
```bat
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
```

2. Запустить API:
```bat
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

## Структура

- `app/` - код FastAPI и сервисы
- `models/` - локальные артефакты моделей (`*.pkl`, не коммитятся)
- `app/script/` - утилиты/скрипты генерации данных

## Важно

- ML-модели сохраняются в `PyModule/models`.
- `__pycache__`, локальная `.venv`, IDE-файлы и временные CSV исключены через `PyModule/.gitignore`.
