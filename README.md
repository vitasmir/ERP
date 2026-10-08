# Retail ERP

MVP životního cyklu letákové akce pro retailový řetězec.

## Stack

- `erp-backend`: Spring Boot 4.1.1, Java 21, JPA, Flyway
- `erp-base-php`: Symfony 7.4, PHP 8.4
- PostgreSQL 18
- Docker Compose

## Spuštění přes Docker

```bash
docker compose up --build
```

Aplikace bude na `http://localhost:4201`, API na `http://localhost:8080`.
Při nasazení nastavte vlastní náhodný `APP_SECRET`; výchozí hodnota je pouze pro lokální vývoj.
Přihlášení vyprší po 30 minutách neaktivity, nejpozději po 8 hodinách.

## Automatický rebuild UI

Pro automatické sestavení po změně zdrojů frontendového UI spusťte:

```bash
./watch-ui.sh
```

Watcher spustí Compose na pozadí, sleduje `erp-base-php` a při změně automaticky provede `docker compose up --build -d`.

## Lokální vývoj

```bash
(cd erp-backend && mvn test)
(cd erp-base-php && composer install)
```

Regresní test uložení oprávnění modulů (bez změn v běžící databázi):

```bash
php erp-base-php/tests/Controller/RoleModulesSmokeTest.php
```

Plánování načítá všechny definované role bez duplicitních názvů přes
`GET /api/v1/planning/roles`; seznam je společný pro vytvoření i úpravu směny.

Backend nabízí `GET /api/v1/promo-campaigns` a `GET /api/v1/dashboard/summary`.
