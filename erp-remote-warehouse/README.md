# Mock Remote Warehouse

Standalone, in-memory warehouse service for local ERP integration testing.

## Run

Start it with the ERP Compose project:

```bash
docker compose up -d --build remote-warehouse-mock
```

The REST API is available at `http://localhost:8091/api/v1`. Containers on the
Compose network can use `http://remote-warehouse-mock:8080/api/v1`.

## Endpoints

- `GET /health`
- `GET /warehouses`
- `GET /categories`
- `GET /products?categoryId=meat&q=krkovice`
- `GET /products/{sku}`
- `GET /availability?warehouseCode=WH-PRAHA&availableOnly=true`
- `GET /products/{sku}/availability`
- `GET /openapi.yaml` for the OpenAPI 3.1 contract

Product responses include GTIN, purchase price excluding VAT, margin, VAT rate,
and sales prices both excluding and including VAT. Availability reports on-hand,
reserved, and available quantities per warehouse.

There is no single universal REST protocol for product catalogs, prices, and
current stock snapshots across WMS providers. This mock uses HTTP/JSON REST and
documents the contract with OpenAPI 3.1; GTIN and GLN fields use GS1 identifier
formats. GS1 EPCIS 2.0 is intended for supply-chain event capture and querying,
so it is not substituted for this catalog and availability API.

The dataset is static and resets when the service restarts.