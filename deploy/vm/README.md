# CafeHelp на ВМ Astra Linux (тестовый стенд)

Офлайн-установка: backend, веб-интерфейс, Postgres (основная и налоговая БД в одном сервере) и MinIO.
ML-модуль (прогнозы, голосовой ввод) и VK-бот не входят. Рассчитано на Docker 18.09 и 2 ГБ ОЗУ.

## Что в комплекте

| Файл | Зачем |
|---|---|
| `cafehelp-images.tar.gz` | образы `cafehelp-vm/backend:1`, `cafehelp-vm/frontend:1`, `postgres:16-alpine`, `minio/minio` (собираются отдельно, в git не лежат) |
| `docker-compose-Linux-x86_64` | docker-compose 1.29.2 (в Astra его нет) |
| `docker-compose.yml` | стенд, формат 2.4 |
| `initdb/` | схема БД (миграции на момент сборки) + тестовые данные, применяются при первом старте Postgres |
| `install.sh` | установка и запуск одной командой |
| `reset.sh` | сброс БД к тестовым данным |
| `tools/gen_seed.py` | генератор тестовых данных |

## Установка

```sh
mkdir -p ~/cafehelp && cd ~/cafehelp
# скопировать сюда cafehelp-vm-bundle.tar (общая папка или wget, см. ниже)
tar xf cafehelp-vm-bundle.tar
sudo sh install.sh
```

Потом открыть в браузере ВМ http://localhost:3000.

* Владелец: `admin` / `admin123`
* Кассир: `kassir` / `kassir123`

## Тестовые данные

Всё выдумано: люди «Тестов/Примерова», телефоны `+7 900 …`, улицы «Тестовая/Примерная».

* 9 категорий, 40 блюд с техкартами, 3 полуфабриката, 6 сетов
* 44 продукта (ингредиенты, расходники, упаковка), 4 поставщика, склады «Основной» и «Бар» с остатками
* 5 сотрудников, 40 клиентов
* 61 смена: 60 закрытых за прошлые дни и открытая сегодняшняя; ~1300 заказов (доставка, самовывоз, наличные/карта/перевод, скидки, несколько долгов)

Даты считаются от дня первого запуска, поэтому «сегодня» всегда есть открытая смена.

## Полезное

```sh
cd ~/cafehelp
sudo docker-compose ps               # состояние
sudo docker-compose logs -f backend  # логи
sudo docker-compose stop             # остановить
sudo docker-compose start            # запустить снова
sudo sh reset.sh                     # сбросить данные к исходным
```

## Пересборка

На машине с Docker из корня репозитория:

```sh
docker build -f Dockerfile.backend -t cafehelp-vm/backend:1 .
docker build -t cafehelp-vm/frontend:1 frontend
docker save cafehelp-vm/backend:1 cafehelp-vm/frontend:1 postgres:16-alpine \
  minio/minio:RELEASE.2025-09-07T16-13-09Z | gzip > deploy/vm/cafehelp-images.tar.gz
```

Если менялись миграции, `initdb/01-main-schema.sql` и `initdb/tax-schema.sql.txt` надо переснять: поднять backend на пустых БД и сделать `pg_dump --no-owner --no-privileges`. Тестовые данные: `python3 tools/gen_seed.py > initdb/02-seed.sql`.
