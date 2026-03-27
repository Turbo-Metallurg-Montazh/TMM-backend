# EMK CRM Backend

Backend-сервис для CRM: аутентификация пользователей, управление ролями и пользователями, интеграция с внешним API закупок, фоновая загрузка тендеров и публичный AI-чат endpoint.

## Технологии

- Java 25
- Spring Boot 4 (Milestone)
- Spring Security + JWT
- Spring Data JPA
- PostgreSQL 18
- Flyway
- OpenAPI Generator
- Docker / Docker Compose
- Kubernetes manifests (`k8s/`)

## Структура проекта

- `src/main/java/.../authentication` - логин, JWT, роли, управление пользователями
- `src/main/java/.../askai` - прокси к Cloudflare Workers AI
- `src/main/java/.../services` - фоновые задачи и доменная логика
- `src/main/resources/openapi` - OpenAPI-контракты
- `src/main/resources/db/migration` - миграции БД (Flyway)
- `docker-compose.local.yml` - локальная БД + pgAdmin
- `docker-compose.prod.yml` - blue/green схема + nginx
- `k8s/` - манифесты для namespace/backend/postgres

## Требования

- JDK 25
- Docker + Docker Compose
- (опционально) Maven 4+, либо использовать `./mvnw`

## Переменные окружения

Минимально обязательные для запуска backend:

- `SPRING_DATASOURCE_URL` (пример: `jdbc:postgresql://localhost:5432/emk_project`)
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

Полезно вынести в env (вместо хранения в `application.yaml`):

- `SECURITY_JWT_TOKEN_SECRET_KEY`
- `EXTERNAL_API_KONTUR_API_KEY`
- `PUBLIC_AI_CLOUDFLARE_ACCOUNT_ID`
- `PUBLIC_AI_CLOUDFLARE_API_TOKEN`
- `SPRING_MAIL_USERNAME`
- `SPRING_MAIL_PASSWORD`

Spring Boot поддерживает env-переопределения в формате `UPPER_SNAKE_CASE`.

## Быстрый старт (локально через Docker)

1. Поднимите PostgreSQL и pgAdmin:

```bash
docker compose -f docker-compose.local.yml up -d
```

2. Запустите backend:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/emk_project
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=postgres
./mvnw spring-boot:run
```

3. Проверка:

- Health: `http://localhost:8080/actuator/health`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- pgAdmin: `http://localhost:8888` (`admin@example.com` / `admin`)

## API (основные endpoint'ы)

Публичные:

- `POST /login` - логин по username/email + password, возвращает JWT
- `POST /password-reset/confirm` - подтверждение сброса пароля по токену из письма
- `POST /public-ai/chat` - запрос к AI-провайдеру

Защищенные (по ролям):

- `GET /admin`, `GET /user`, `GET /owner`
- `GET /add-administrator-role?user=...`
- `GET /remove-administrator-role?user=...`
- `GET /delete-user?user=...`
- `GET/POST /admin/users`
- `GET/DELETE /admin/users/{username}`
- `POST /admin/users/reset-password` - отправка письма со ссылкой на сброс пароля (email передается в body)

Автоочистка reset-токенов:
- Использованные и просроченные токены из `password_reset_token` удаляются по cron `schedule.password-reset-cleanup-cron` (по умолчанию раз в неделю: в воскресенье в 23:00, `Europe/Moscow`).

Служебный endpoint:

- `POST /tender-filters` - добавление фильтра тендеров
- `POST /tender-filters/export` - выгрузка тендеров по имени фильтра

## База данных и миграции

- Миграции Flyway лежат в `src/main/resources/db/migration`
- Основные таблицы: `user_info`, `roles`, `user_roles`, `illiquid_assets`, `tender_filter`, `unloading_date`, `tenders`

После первого запуска убедитесь, что в `roles` есть значения ролей, используемых системой (`USER`, `ADMIN`, `OWNER`).

## Планировщик

Включен `@EnableScheduling`. Фоновая загрузка тендеров запускается по cron из `schedule.time` (по умолчанию: `"0 53 8 * * ?"`), timezone `Europe/Moscow`.

## Kubernetes

Манифесты находятся в `k8s/`:

- `k8s/namespace.yaml`
- `k8s/postgres/*`
- `k8s/backend/*`

Ingress настроен на `api.turbo-metallurg-montazh.ru`.

## Сборка и тесты

Сборка:

```bash
./mvnw clean package -DskipTests
```

Тесты:

```bash
./mvnw test
```

## Безопасность

В `src/main/resources/application.yaml` сейчас присутствуют секреты/ключи. Перед публикацией или деплоем:

1. Удалите секреты из репозитория.
2. Перенесите их в переменные окружения или Kubernetes Secrets.
3. Ротуйте уже скомпрометированные ключи (JWT, SMTP, external API, AI token).
