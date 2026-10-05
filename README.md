# Запись на приём к специалисту

## Назначение

Учебный сервис курсовой работы по дисциплине «Оптимизация клиент-серверных приложений», вариант 3.
Показывает свободные слоты под услугу, бронирует их без наложений, отменяет записи и считает загрузку
специалистов и долю отмен за период.

## Требования

Docker с Compose — для запуска сервиса и базы. JDK 21 (Temurin 21.0.7) — только для тестов и запуска без контейнера.
Сервис работает в контейнере `eclipse-temurin:21.0.12.1_1-jre`, база — в `postgres:16.15-alpine`.
Kotlin 2.4.0, Ktor 3.6.0, Exposed JDBC 1.3.1, HikariCP 6.3.3.

## Установка и запуск

```sh
git clone <адрес репозитория>
cd okspkur
cp .env.example .env
docker compose up -d --build --wait
docker compose run --rm seed small
```

`docker compose up` собирает образ сервиса и поднимает базу и сервис; `--wait` дожидается, пока оба ответят.
Сидер пересоздаёт схему целиком и применяет `schema.sql`; отдельный шаг применения схемы не нужен.
Повторное наполнение даёт те же данные и удаляет изменения, сделанные через сервис; перезапускать сервис после него не нужно.
Для рабочего объёма (200 000 слотов, 80 000 записей):

```sh
docker compose run --rm seed working
```

Проверка количества строк:

```sh
docker compose exec db psql -U ivan_tikhonov -d ivan_tikhonov -c "SELECT 'users', count(*) FROM ivan_tikhonov.users UNION ALL SELECT 'specialists', count(*) FROM ivan_tikhonov.specialists UNION ALL SELECT 'services', count(*) FROM ivan_tikhonov.services UNION ALL SELECT 'slots', count(*) FROM ivan_tikhonov.slots UNION ALL SELECT 'bookings', count(*) FROM ivan_tikhonov.bookings"
```

Журнал сервиса и остановка:

```sh
docker compose logs -f app
docker compose stop
```

Запуск без контейнера, для разработки: остановить контейнер сервиса (`docker compose stop app`),
затем `set -a; source .env; set +a` и `./gradlew run`; наполнение с хоста — `./gradlew seed --args=small`.

## Переменные окружения

| Имя          | Назначение                     | Пример                                          |
|--------------|--------------------------------|-------------------------------------------------|
| DB_PORT      | Порт PostgreSQL на хосте       | 26432                                           |
| DB_USER      | Пользователь PostgreSQL        | ivan_tikhonov                                   |
| DB_PASSWORD  | Пароль PostgreSQL              | changeme                                        |
| DB_SCHEMA    | Схема таблиц                   | ivan_tikhonov                                   |
| DATABASE_URL | Адрес подключения JDBC         | jdbc:postgresql://localhost:26432/ivan_tikhonov |
| DB_POOL_SIZE | Максимум соединений приложения | 10                                              |
| JWT_SECRET   | Секрет подписи токенов         | changeme                                        |
| APP_PORT     | Порт сервиса                   | 8080                                            |

Docker Compose читает `.env` сам; внутри сети Docker сервис подключается к базе по адресу `db:5432`.
После смены `DB_POOL_SIZE` или `JWT_SECRET` сервис пересоздаётся: `docker compose up -d --wait app`.
Часовой пояс задан в `application.yaml`: Europe/Moscow.

## Проверка работоспособности

Интерфейс: http://localhost:8080. Вход: `admin` / `demo`.
Swagger UI: http://localhost:8080/swagger.

```sh
curl -i -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{"login":"admin","password":"demo"}'
```

Скопируйте поле `token` в команду:

```sh
curl -i 'http://localhost:8080/api/bookings?page=1&size=5' -H 'Authorization: Bearer <token>'
```

Каждый ответ содержит `X-Response-Time-Ms` (время до формирования ответа в миллисекундах),
`X-Db-Queries` (число SQL-операторов) и `X-Db-Time-Ms` (их суммарное время в миллисекундах).

## Тесты

```sh
./gradlew test
```

Тесты проверяют правила бронирования и отмены, расчёт сводки, операции API, формы ответов,
параметры, ошибки JSON и заголовки метрик. База для них не нужна.

## Программный интерфейс

| Метод и путь                      | Параметры                                               | Ответ                                     | Ошибки                  |
|-----------------------------------|---------------------------------------------------------|-------------------------------------------|-------------------------|
| POST /api/auth/login              | тело: login, password                                   | 200 `{"token": "..."}`                    | 400, 401                |
| GET /api/bookings                 | page=1, size=20 (1–100), status, specialistId, from, to | 200 `{"items": [BookingRow], "total": N}` | 401, 422                |
| GET /api/bookings/{id}            | —                                                       | 200 BookingCard                           | 401, 404                |
| POST /api/bookings                | тело: slotId, serviceId                                 | 201 BookingCard                           | 400, 401, 404, 409, 422 |
| POST /api/bookings/{id}/cancel    | —                                                       | 200 BookingCard                           | 401, 404, 409           |
| GET /api/services                 | —                                                       | 200 `[Service]`                           | 401                     |
| GET /api/services/{id}/free-slots | from, to (обязательные), page=1, size=20 (1–100)        | 200 `{"items": [FreeSlot], "total": N}`   | 401, 404, 422           |
| GET /api/summary                  | from, to (обязательные)                                 | 200 Summary                               | 401, 422                |

Полный контракт: [docs/api.md](docs/api.md).
Машиночитаемое описание: [OpenAPI](src/main/resources/openapi/documentation.yaml).
