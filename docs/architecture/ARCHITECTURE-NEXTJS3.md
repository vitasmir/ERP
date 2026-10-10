# Architektura ERP — Next.js 3 (React/TSX frontend)

Dokument popisuje nasazení definované v [`docker-compose-NEXTJS3.yml`](./docker-compose-NEXTJS3.yml).
Frontend `erp-base-nextjs3` je komponentová alternativa: obrazovky jsou
implementované jako typované React/TSX komponenty bez Twig runtime.

## Přehled služeb

```mermaid
flowchart LR
    browser["Prohlížeč"]
    frontend["frontend<br/>Next.js / React / TypeScript<br/>Node.js :3000"]
    backend["backend<br/>Spring Boot / Java 21<br/>REST API :8080"]
    postgres[("postgres<br/>PostgreSQL 18<br/>erp")]
    warehouse["remote-warehouse-mock<br/>REST mock :8080"]

    browser -->|"HTTP :3000"| frontend
    frontend -->|"BACKEND_URL<br/>backend:8080"| backend
    backend -->|"JPA / JDBC"| postgres
    browser -.->|"HTTP :8091"| warehouse
```

Všechny kontejnery jsou připojené k externí Docker síti `projects-network`.
Mock skladu není podle této konfigurace zapojený do požadavků backendu.

## Komponenty a odpovědnosti

| Služba | Image/build | Odpovědnost |
| --- | --- | --- |
| `frontend` | `./erp-base-nextjs3` | Next.js server, React/TSX stránky, serverové sessions a směrování volání backendu. |
| `backend` | `./erp-backend` | Spring Boot REST API, autentizace, autorizace, validace a aplikační pravidla. |
| `postgres` | `postgres:18` | Databáze `erp`, zdroj trvalých ERP dat. |
| `remote-warehouse-mock` | `./erp-remote-warehouse` | Samostatné HTTP API mocku skladu; jeho testovací data jsou v paměti. |

Frontend volá backend přes `http://backend:8080`. Backend používá
`postgres:5432` a Compose čeká na databázový healthcheck před jeho spuštěním.
Session s backendovým tokenem, košíkem a údaji o doručení je uložena na serveru;
prohlížeč obdrží neprůhlednou HttpOnly cookie.

## Síť, porty a perzistence

| Port hostitele | Kontejner | Účel |
| --- | --- | --- |
| `3000` | `frontend:3000` | Next.js ERP frontend a veřejné stránky |
| `8080` | `backend:8080` | REST API backendu |
| `5434` | `postgres:5432` | Přístup k PostgreSQL z hostitele |
| `8091` | `remote-warehouse-mock:8080` | API mocku skladu |

Databázová data jsou v pojmenovaném volume `erp-postgres-data`. Session data
frontend ukládá do `/app/var/sessions`, připojeného k oddělenému volume
`erp-nextjs3-sessions`. Souborové session úložiště je určeno pro jednu repliku
frontendu; horizontální škálování vyžaduje sdílené úložiště se synchronizací.
Mock skladu drží dataset pouze v paměti.

## Spuštění a přepnutí varianty

Před prvním spuštěním vytvořte externí síť:

```bash
docker network create projects-network
docker compose -f docker-compose-NEXTJS3.yml up --build -d
```

Frontend je dostupný na `http://localhost:3000`, API na
`http://localhost:8080`. Compose varianty sdílejí publikované porty i název
databázového volume; nespouštějte je současně. Při přepínání zastavte
předchozí variantu jejím Compose souborem. Nepoužívejte `down -v`, pokud chcete
zachovat databázová a session data.

## Konfigurace pro nasazení

Compose nastavuje `BACKEND_URL`, `COOKIE_SECURE` (výchozí `false`) a
`SESSION_DIR`. Za HTTPS nastavte `COOKIE_SECURE=true` a chraňte session volume
jako citlivá runtime data. Databázová hesla v Compose jsou vývojové hodnoty;
pro nasazení použijte bezpečnou správu tajemství.
