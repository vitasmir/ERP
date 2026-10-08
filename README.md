# Retail ERP

MVP životního cyklu letákové akce pro retailový řetězec.

## Stack

- `erp-backend`: Spring Boot 4.1.1, Java 21, JPA, Flyway
- `erp-base-nextjs2`: Next.js 16.3.8, TypeScript, Twig.js, Node.js 22; původní PHP vzhled, CSS a JavaScript
- `erp-base-nextjs`: zachovaný první React frontend
- PostgreSQL 18
- Docker Compose

## Spuštění přes Docker

```bash
docker compose up --build
```

Aplikace bude na `http://localhost:3000`, API na `http://localhost:8080`.
Síť `projects-network` musí existovat; při prvním spuštění ji vytvořte přes
`docker network create projects-network`.
Pro HTTPS nasazení nastavte `COOKIE_SECURE=true`.
Přihlášení vyprší po 30 minutách neaktivity, nejpozději po 8 hodinách.
Backend tokeny i košíky se ukládají na serveru v objemu `erp-nextjs-sessions`;
prohlížeč dostává pouze náhodný identifikátor v HttpOnly cookie.
Symfony frontend lze spustit místo Next.js takto:

```bash
docker compose down
docker compose -f docker-compose-PHP.yml up --build
```

PHP frontend je na portu 4201. Varianty sdílejí backendové porty, proto je
nespouštějte současně. Původní JSP frontend zůstává jako reference.
Samostatná konfigurace nové varianty je také v `docker-compose-NEXTJS2.yml`
s vlastním objemem `erp-nextjs2-sessions`. Podrobnosti o věrném přepisu PHP
šablon a funkčnosti jsou v [erp-base-nextjs2/README.md](erp-base-nextjs2/README.md).

## Automatický rebuild UI

Pro automatické sestavení po změně zdrojů frontendového UI spusťte:

```bash
./watch-ui.sh
```

Watcher spustí Compose na pozadí, sleduje zdroje, šablony a assety
`erp-base-nextjs2` a při změně automaticky provede `docker compose up --build -d`.

## Lokální vývoj

```bash
(cd erp-backend && mvn test)
cd erp-base-nextjs2
npm ci
npm run dev
```

Next.js vývojový server běží na `http://localhost:3000` a volá API na
`http://localhost:8080`. Volitelnou konfiguraci uvádí `erp-base-nextjs2/.env.example`.

Validace nového frontendu (integrační test spouští vlastní backend mock):

```bash
cd erp-base-nextjs2
npm run typecheck
npm run build
npm test
```

Plánování načítá všechny definované role bez duplicitních názvů přes
`GET /api/v1/planning/roles`; seznam je společný pro vytvoření i úpravu směny.

Backend nabízí `GET /api/v1/promo-campaigns` a `GET /api/v1/dashboard/overview`.
Veřejný e-shop používá `GET /api/v1/settings/public`, který vrací pouze poplatek
za doručení, nikoli administrativní nastavení.
Platba kartou je nadále pouze ERP demonstrace, nikoli skutečná platební brána.
Současný backend při checkoutu účtuje cenu zboží bez poplatku za doručení.
