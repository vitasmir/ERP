# Retail ERP

MVP životního cyklu letákové akce pro retailový řetězec.

## Stack

- `erp-backend`: Spring Boot 4.1.1, Java 21, JPA, Flyway
- `erp-frontend`: Angular 22
- PostgreSQL 18
- Docker Compose

## Spuštění přes Docker

```bash
docker compose up --build
```

Aplikace bude na `http://localhost:4200`, API na `http://localhost:8080`.

## Lokální vývoj

```bash
cd erp-backend && mvn test
cd erp-frontend && npm install && npm run build
```

Backend nabízí `GET /api/v1/promo-campaigns` a `GET /api/v1/dashboard/summary`.
