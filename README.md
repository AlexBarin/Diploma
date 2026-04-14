# Metric Lab — сервис мониторинга

Самодостаточная платформа мониторинга, которая запускает синтетическую нагрузку интернет-магазина с реалистичными шаблонами трафика и применяет поверх её метрик адаптивное обнаружение аномалий. Предназначена для запуска на одном VPS через Docker Compose.

## Архитектура

```text
                         +-----------------------+
                         |   Grafana (:3000)     |
                         |   10 дашбордов        |
                         +-----------+-----------+
                                     |
                         +-----------+-----------+
                         | Prometheus (:9090)    |
                         |  7 scrape jobs, 5s    |
                         +-----------+-----------+
                              /      |       \
            +----------------+       |        +-------------------+
            |                        |                            |
+-----------+----------+  +----------+---------+  +---------------+---------+
| metric-lab-app       |  | host-exporter      |  | adaptive-threshold-     |
| FastAPI (:8000)      |  | Linux (:9422)      |  | engine (:8080)          |
| metric-lab-worker    |  | CPU/память/диск/   |  | Java 21 / Spring Boot   |
| (:9108)              |  | статистика         |  | 4 алгоритма аномалий    |
|                      |  | контейнеров        |  |                         |
+----------+-----------+  +--------------------+  +--------+--------+-------+
           |                                               |        |
     +-----+------+                                        |   Аннотации Grafana
     |            |                                        |   + email-оповещения
+----+----+  +----+----+                              +----+----+
| Postgres |  | Redis   |                              | Postgres |
| (:5432)  |  | (:6379) |                              | (общий)  |
+----------+  +---------+                              +----------+
```

## Сервисы

| Сервис | Образ / Сборка | Порт | Роль |
|---|---|---|---|
| **postgres** | postgres:16-alpine | 5432 | Каталог товаров, заказы, состояние алгоритмов |
| **redis** | redis:7.2-alpine | 6379 | Сессии, хранение корзины, очередь заданий |
| **metric-lab-app** | build: `./app` | 8000 | FastAPI API интернет-магазина |
| **metric-lab-worker** | build: `./app` | 9108 | Фоновый worker выполнения заказов |
| **redis-exporter** | oliver006/redis_exporter:v1.61.0 | 9121 | Метрики Redis для Prometheus |
| **postgres-exporter** | prometheuscommunity/postgres-exporter:v0.15.0 | 9187 | Метрики PostgreSQL для Prometheus |
| **prometheus** | prom/prometheus:v2.52.0 | 9090 | Хранилище метрик (ретеншн 60 дней) |
| **grafana** | grafana/grafana:10.4.3 | 3000 | Дашборды и оповещения |
| **locust** | locustio/locust:2.24.1 | — | Headless-генератор нагрузки |
| **host-exporter-linux** | build: `./host-exporter-linux` | 9422 | Метрики CPU/памяти/диска/контейнеров хоста |
| **adaptive-threshold-engine** | build: `./adaptive-threshold-engine` | 8080 | Обнаружение аномалий (отдельный compose) |

## Быстрый запуск

```bash
# Запуск основной платформы
docker compose up -d --build

```

Grafana доступна по адресу `http://localhost:3000` (admin / admin).

## Приложение — синтетическая нагрузка интернет-магазина

Каталог `app/` содержит FastAPI-сервис, моделирующий магазин носков, со следующими endpoint'ами:

| Метод | Путь | Описание |
|---|---|---|
| GET | `/api/home` | Рекомендуемые товары |
| GET | `/api/catalog` | Список товаров |
| GET | `/api/search?q=` | Поиск товаров |
| GET | `/api/product/{id}` | Детальная информация о товаре |
| POST | `/api/cart/add` | Добавить товар в корзину |
| GET | `/api/cart` | Просмотр корзины |
| POST | `/api/checkout` | Оформить заказ |
| GET | `/health` | Проверка работоспособности |
| GET | `/metrics` | Метрики Prometheus |

Режим worker (`python main.py worker`) опрашивает очередь заданий Redis и выполняет заказы.

### Сжатый временной цикл

Приложение моделирует реалистичные шаблоны трафика в сжатом временном масштабе:

- **1 "день" = 25 минут** (1500 секунд)
- **1 "неделя" = 175 минут** (7 сжатых дней)
- Дни 0–4 — будни (более высокий трафик), дни 5–6 — выходные (примерно на 40% меньше трафика)

Каждый день проходит через 5 фаз: ночное затишье, утренний рост, дневной пик, вечернее снижение и позднее затишье. Фазы управляют объёмом трафика (6–36 одновременных пользователей), задержками ответа, частотой ошибок, нагрузкой на CPU и поведением памяти. Для предотвращения плоских плато применяется jitter.

### Генерация нагрузки

Locust работает в headless-режиме с тремя пользовательскими персонами:

| Тип пользователя | Вес | Поведение |
|---|---|---|
| WindowShopper | 6 | Просматривает каталог и выполняет поиск, редко добавляет товары в корзину |
| IntentBuyer | 3 | Просматривает каталог, открывает товары, добавляет их в корзину и оформляет заказ |
| FlashSaleHunter | 1 | Агрессивно просматривает товары и оформляет заказ |

Таблицы фаз импортируются из `app/scenario.py` (единый источник истины).

## Adaptive Threshold Engine

Сервис на Spring Boot 3.3.5 / Java 21, который запрашивает данные из Prometheus, вычисляет динамические пороги для каждой метрики, обнаруживает аномалии и экспортирует результаты обратно в Prometheus и Grafana.

### Алгоритмы

| Алгоритм | Класс | Сценарий использования |
|---|---|---|
| `adaptive-moving-stats` | AdaptiveMovingStats | По умолчанию — EMA с адаптивным стандартным отклонением |
| `holt-winters` | HoltWintersSmoothing | Метрики с сезонными шаблонами (суточные/недельные циклы) |
| `robust-quantile-bounds` | RobustQuantileBounds | Скошенные распределения — на основе IQR через оценивание P-squared |
| `rate-of-change` | RateOfChangeDetector | Монотонно трендовые метрики (размер БД, использование памяти) |

Выбор алгоритма выполняется автоматически на основе характеристик данных (обнаружение сезонности через автокорреляцию, анализ скошенности, обнаружение тренда), при этом в `application.yml` доступны переопределения для отдельных метрик.

### API

Базовый путь: `/api/v1`

| Метод | Путь | Описание |
|---|---|---|
| GET | `/thresholds` | Все текущие пороги |
| GET | `/thresholds/{metric}` | Порог для конкретной метрики |
| GET | `/anomalies?since=1h` | Недавние события аномалий |
| GET | `/metrics/audit` | Аудиторское представление всех отслеживаемых метрик |
| GET | `/health` | Статус работоспособности engine |
| POST | `/metrics/{name}/config` | Обновление алгоритма/чувствительности во время выполнения |

Метрики Prometheus экспортируются по адресу `/actuator/prometheus` (scrape каждые 15s).

### Конвейер обнаружения аномалий

1. **Запрос** — получает 2-часовую историю из Prometheus с разрешением 15 секунд
2. **Фильтрация** — удаляет экстремальные выбросы-спайки через фильтрацию на основе MAD
3. **Выбор** — автоматически назначает алгоритм для каждой метрики (или использует настроенное переопределение)
4. **Вычисление** — рассчитывает верхний/нижний/срединный пороги с настраиваемой чувствительностью
5. **Защита** — обнаруживает монотонный дрейф и ужесточает границы, чтобы предотвратить его бесшумное поглощение
6. **Ограничение** — обеспечивает минимальную ширину полосы и неотрицательные нижние границы
7. **Обнаружение** — оценивает аномалии (0 = норма, максимум 10) с уровнями WARNING/CRITICAL
8. **Экспорт** — отправляет пороги в Prometheus, аннотации аномалий в Grafana, оповещения по email

Повторное вычисление выполняется каждые 30 секунд. Состояние алгоритмов сохраняется в PostgreSQL каждые 5 минут и при завершении работы для тёплых рестартов.

### Миграции базы данных (Flyway)

| Версия | Таблица | Назначение |
|---|---|---|
| V2 | `algorithm_state_store` | Сохранённое состояние алгоритмов для тёплых рестартов |
| V3 | `anomaly_events` | Исторический журнал аномалий с операторской маркировкой |
| V4 | `feedback_adjustments` | История настройки чувствительности на основе обратной связи оператора |

## Мониторинг

### Scrape Jobs Prometheus

| Job | Target | Интервал |
|---|---|---|
| prometheus | prometheus:9090 | 5s |
| metric-lab-app | metric-lab-app:8000 | 5s |
| metric-lab-worker | metric-lab-worker:9108 | 5s |
| redis | redis-exporter:9121 | 5s |
| postgres | postgres-exporter:9187 | 5s |
| metric-lab-host | host-exporter-linux:9422 | 5s |
| adaptive-threshold-engine | adaptive-threshold-engine:8080 | 15s |

Отслеживаются 53 метрики в 6 сервисных группах (app, worker, Redis, PostgreSQL, инфраструктура хоста, самонаблюдение Prometheus).

### Дашборды Grafana

**Дашборды сервисов** (генерируются `grafana/scripts/generate-dashboards.ps1`):
- Service — metric-lab-app
- Service — metric-lab-worker
- Service — postgres
- Service — redis
- Service — host
- Alerting Overview

**Дашборды адаптивных порогов** (генерируются `grafana/scripts/generate-dashboards.py`):
- Dynamic Thresholds Overview — коридоры порогов для всех метрик, сгруппированных по сервисам
- Anomaly Feed — аномальные оценки в реальном времени, частоты обнаружения по job и severity
- Engine Health — процентили длительности вычисления, распределение алгоритмов, количество отслеживаемых метрик

### Генерация дашбордов

```bash
# Генерация дашбордов адаптивных порогов (Python)
python grafana/scripts/generate-dashboards.py

# Генерация дашбордов сервисов (PowerShell, Windows)
powershell grafana/scripts/generate-dashboards.ps1
```

## Структура проекта

```text
.
├── adaptive-threshold-engine/     Сервис обнаружения аномалий на Java 21
│   ├── Dockerfile                 Многоэтапная Maven-сборка
│   ├── pom.xml                    Spring Boot 3.3.5
│   └── src/
│       ├── main/java/.../threshold/
│       │   ├── algorithm/         4 алгоритма порогов + авто-селектор
│       │   ├── api/               REST-контроллер + DTO
│       │   ├── config/            Properties и Spring-конфигурация
│       │   ├── engine/            Основной compute engine + состояние
│       │   ├── export/            Экспортёр Prometheus + аннотации Grafana
│       │   ├── feedback/          Обратная связь оператора + настройка чувствительности
│       │   ├── notification/      Email-оповещения + дедупликация
│       │   ├── persistence/       Состояние алгоритмов + хранение аномалий
│       │   └── prometheus/        Запросы Prometheus + обнаружение метрик
│       ├── main/resources/
│       │   ├── application.yml    Вся конфигурация engine
│       │   └── db/migration/      Миграции Flyway (V2–V4)
│       └── test/                  Unit-тесты алгоритмов
├── app/                           Сервис интернет-магазина на Python FastAPI
│   ├── main.py                    API-сервер + фоновый worker
│   └── scenario.py                Сжатый временной цикл + определения фаз
├── docs/                          Документация проекта
├── grafana/
│   ├── dashboards/                Сгенерированные JSON-файлы дашбордов
│   ├── provisioning/              Источники данных, провайдеры дашбордов, alerting
│   └── scripts/                   Генераторы дашбордов (.py + .ps1)
├── host-exporter-linux/           Пользовательский Prometheus-exporter для метрик хоста
├── locust/                        Определения нагрузочного тестирования
├── postgres/                      Инициализация базы данных (таблицы products + orders)
├── prometheus/                    Конфигурация Prometheus
├── docker-compose.yml             Основная платформа (10 сервисов)
└── docker-compose.threshold.yml   Adaptive threshold engine (1 сервис)
```

## Конфигурация

### Переменные окружения — App

| Переменная | По умолчанию | Описание |
|---|---|---|
| `SERVICE_NAME` | metric-lab-app | Идентификатор сервиса в метриках |
| `POSTGRES_DSN` | postgresql://lab:lab@postgres:5432/metriclab | Подключение к базе данных |
| `REDIS_URL` | redis://redis:6379/0 | Подключение к Redis |
| `APP_PORT` | 8000 | Порт HTTP API |
| `METRICS_PORT` | 9108 | Порт метрик Prometheus (worker) |
| `SESSION_TTL_SECONDS` | 900 | Время жизни сессии Redis |

### Переменные окружения — Threshold Engine

| Переменная | По умолчанию | Описание |
|---|---|---|
| `THRESHOLD_ENGINE_PROMETHEUS_URL` | http://prometheus:9090 | API Prometheus |
| `THRESHOLD_ENGINE_GRAFANA_URL` | http://grafana:3000 | API Grafana |
| `DB_HOST` | postgres | Хост PostgreSQL |
| `DB_NAME` | metriclab | Имя базы данных |
| `DB_USER` / `DB_PASS` | lab / lab | Учётные данные базы данных |
| `ALERT_EMAIL_ENABLED` | false | Включить SMTP email-оповещения |
| `SMTP_USERNAME` / `SMTP_PASSWORD` | — | Учётные данные Gmail SMTP |
| `ALERT_RECIPIENTS` | — | Email-адреса через запятую |
