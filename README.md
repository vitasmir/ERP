# Retail ERP

MVP životního cyklu letákové akce pro retailový řetězec.

## Stack

- `erp-backend`: Spring Boot 4.1.1, Java 21, JPA, Flyway
- `erp-frontend`: JSP
- PostgreSQL 18
- Docker Compose

## Spuštění přes Docker

```bash
docker compose up --build
```

Aplikace bude na `http://localhost:4201`, API na `http://localhost:8080`.

## Automatický rebuild UI

Pro automatické sestavení po změně zdrojů frontendového UI spusťte:

```bash
./watch-ui.sh
```

Watcher spustí Compose na pozadí, sleduje `erp-base-frontend/src` a při změně automaticky provede `docker compose up --build -d`.

## Lokální vývoj

```bash
cd erp-backend && mvn test
cd erp-frontend && npm install && npm run build
```

Backend nabízí `GET /api/v1/promo-campaigns` a `GET /api/v1/dashboard/summary`.
