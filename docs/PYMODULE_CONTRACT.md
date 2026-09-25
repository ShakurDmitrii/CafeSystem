# Внутренний контракт CafeHelp ↔ PyModule

**Версия контракта:** `1`  
**Статус:** действующий внутренний API  
**Область:** аналитика, прогнозирование и обучение модели

## Назначение и границы

PyModule — вспомогательный FastAPI-сервис. Java backend остаётся владельцем бизнес-данных и
транзакций: создание и оплата заказов, складские списания, смены и выплаты не зависят от
доступности Python. Отказ PyModule временно отключает аналитику и ML-функции, но не должен
блокировать операционную работу кафе.

Первый этап аудита ограничивался транспортом. Исправления данных и оценки ML/генетического
поиска описаны в [отчёте сентября 2026](ML_CORRECTIONS_2026-09.md). XGBoost и генетический
поиск сохранены; неопределённые показатели теперь допускают `null`.

## Аутентификация и версия

Защищённые запросы в обоих направлениях передают:

- `X-Service-Token` — общий внутренний токен длиной не менее 24 символов;
- `X-Contract-Version: 1` — версия транспортного контракта.

Неверный токен возвращает `401`. Явно переданная несовместимая версия возвращает `409`.
На переходный период отсутствие заголовка версии допускается, чтобы Java и PyModule можно
было обновлять поочерёдно. После обновления всех окружений эту совместимость следует убрать.
Токен и тела ответов не записываются в логи.

## Java → PyModule

| Операция | Метод и путь | Основной запрос | Основной ответ |
|---|---|---|---|
| Проверка процесса | `GET /health` | — | `status` |
| Проверка готовности | `GET /ready` | — | `status`, `modelLoaded`, `javaClientConnected` |
| Прогноз блюда | `POST /api/ml/predict` | `ingredients`, опционально `date` | `predictedSales`, `ingredients`, `confidenceScore`, `modelVersion` |
| Пакетный прогноз | `POST /api/ml/predict/batch` | `rolls[]` | `results[]`, `modelVersion` |
| Обучение | `POST /api/ml/train` | `records[]` (10–100000) | результат обучения |
| Информация о модели | `GET /api/ml/info` | — | версия и состояние модели |
| Популярные пары | `GET /api/ml/insights/popular-pairs` | `limit` (1–50) | массив строк |
| Генерация блюда | `POST /api/ml/generate-dish` | продажи, меню, ингредиенты, ограничения | результат генерации |
| Оптимизация | `POST /api/ml/optimize` | ограничения и входные наборы | статус и результаты |
| Дашборд | `GET /api/analytics/dashboard` | период и опциональные даты | KPI, топ блюд, тренд, инсайты |
| KPI | `GET /api/analytics/kpi` | `timeRange` | KPI |
| Топ блюд | `GET /api/analytics/top-rolls` | период, `limit`, `sortBy` | массив блюд |
| Тренд | `GET /api/analytics/sales-trend` | период, `granularity=day` | временной ряд |
| Инсайты | `GET /api/analytics/insights` | `timeRange` | массив инсайтов |

Pydantic отклоняет лишние поля. Поэтому Java формирует отдельные внутренние payload, а не
сериализует UI DTO целиком. Пакетный ответ Python использует поле `results`; Java сопоставляет
его с `predictions` своей DTO через явную Jackson-аннотацию.

## PyModule → Java

Для построения аналитики Python запрашивает Java API:

| Операция | Метод и путь | Ограничения |
|---|---|---|
| Продажи | `GET /api/ml/data/sales` | `startDate`, `endDate`, `limit` 1–10000 |
| Меню | `GET /api/ml/data/menu` | внутренний endpoint |
| Ингредиенты | `GET /api/ml/data/ingredients` | внутренний endpoint |
| Популярные ингредиенты | `GET /api/ml/data/ingredients/popular` | `limit` |
| Проверка Java API | `GET /api/ml/data/health` | внутренний endpoint |

PyModule принимает только JSON ожидаемого типа. Ошибка Java API или некорректный формат
продаж превращается в безопасный ответ аналитики `502`, без выдачи внутреннего URL и текста
исключения клиенту.

## Ошибки и таймауты

Java нормализует сбои PyModule:

| HTTP | Код | Значение |
|---:|---|---|
| `502` | `PYTHON_UPSTREAM_ERROR` | PyModule вернул ошибку |
| `422` | `PYTHON_INPUT_INVALID` | явная доменная ошибка данных/ограничений ML |
| `502` | `PYTHON_INVALID_RESPONSE` | пустой, частичный или некорректный JSON |
| `503` | `PYTHON_UNAVAILABLE` | соединение не установлено |
| `504` | `PYTHON_TIMEOUT` | истёк connect/read timeout |
| `409` | `INTERNAL_CONTRACT_VERSION_MISMATCH` | несовместимая версия на Java стороне |

Для `503` и `504` Java добавляет `Retry-After: 5`. Сообщения внешнему клиенту стабильны и не
содержат адресов сервисов или исходных исключений.

## Конфигурация

| Переменная | Значение по умолчанию | Назначение |
|---|---:|---|
| `INTERNAL_SERVICE_TOKEN` | отсутствует | внутренний секрет; в Compose обязателен |
| `INTERNAL_API_CONTRACT_VERSION` | `1` | версия контракта в обоих сервисах |
| `PYTHON_API_URL` | `http://localhost:8000` | адрес PyModule для Java |
| `JAVA_API_URL` | `http://localhost:8080` | адрес Java API для PyModule |
| `PYTHON_CONNECT_TIMEOUT` | `3s` | таймаут соединения Java → Python |
| `PYTHON_READ_TIMEOUT` | `30s` | таймаут ответа Java → Python |
| `JAVA_API_TIMEOUT_SECONDS` | `30` | общий таймаут Python → Java |

## Проверка перед выпуском

```powershell
./gradlew.bat test --tests "com.shakur.cafehelp.Service.MlServices.PythonMlContractTest" `
  --tests "com.shakur.cafehelp.Controller.PythonAnalyticsClientContractTest" `
  --tests "com.shakur.cafehelp.Controller.PythonServiceFailureMappingTest" `
  --tests "com.shakur.cafehelp.config.PythonRestTemplateConfigTest" `
  --tests "com.shakur.cafehelp.security.ServiceTokenContractVersionTest"

Set-Location PyModule
python -m pytest -q
```

Дополнительно в доступном Docker-окружении нужно поднять стек на свободном тестовом порту,
проверить `/health`, `/ready` и один двунаправленный запрос аналитики. Порт `5433`, занятый
другим процессом, не использовать; для проверки CafeHelp задан `TAX_DB_PORT=55433`.

Машиночитаемая спецификация: `docs/api/pymodule-internal.openapi.yaml`.

## Семантика оценки (сентябрь 2026)

`confidenceScore=null` означает отсутствие измеренной уверенности, а не нулевую вероятность.
`target=daily_quantity_on_sale_days` — условная суточная оценка по дням с продажами. Записи
обучения должны быть предварительно сгруппированы по блюду и дате. Ответы содержат `warnings`.

Генератор/оптимизатор возвращают `salesSource=ml|heuristic`, `priceSource`, `modelVersion`,
`warnings`, `techCard`. В эвристическом режиме `predictedSales` и `estimatedProfit` равны `null`.
`score` — рейтинг внутри запуска, не уверенность. Ингредиенты передаются в базовых единицах
с `warehouseId` и `costSource`; меню содержит `ingredientQuantities` (потребность в сырье, г).

Java, Python и интерфейс обновляются совместно: старый клиент требовал числовую уверенность.
