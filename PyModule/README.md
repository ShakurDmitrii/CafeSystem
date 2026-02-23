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

## Печать (Windows)

Можно задать в `.env`:

```env
DEFAULT_PRINTER=windows
WINDOWS_PRINT_MODE=auto
PRINTER_NAME=
```

- `WINDOWS_PRINT_MODE=auto` - сначала Word COM, затем `startfile`
- `WINDOWS_PRINT_MODE=com` - только Word COM
- `WINDOWS_PRINT_MODE=startfile` - только `os.startfile`
- `PRINTER_NAME` - опционально, имя конкретного принтера для COM-печати

## Структура

- `app/` - код FastAPI и сервисы
- `models/` - локальные артефакты моделей (`*.pkl`, не коммитятся)
- `app/script/` - утилиты/скрипты генерации данных

## Важно

- ML-модели сохраняются в `PyModule/models`.
- `__pycache__`, локальная `.venv`, IDE-файлы и временные CSV исключены через `PyModule/.gitignore`.
