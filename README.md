# Retail ERP

MVP životního cyklu letákové akce pro retailový řetězec.

## Stack

- `erp-backend`: Spring Boot 4.1.1, Java 21, JPA, Flyway
- `erp-base-nextjs2`: Next.js 16.3.8, TypeScript, Twig.js, Node.js 22; původní PHP vzhled, CSS a JavaScript
- `erp-base-nextjs3`: Next.js 16.3.8, React/TSX, Node.js 22; komponentová alternativa bez Twig
- `erp-base-nextjs`: zachovaný první React frontend
- `docker-compose-PHP.yml`: Symfony frontend, PostgreSQL, backend a mock skladu
- PostgreSQL 18
- Docker Compose
- Kubernetes (lokální JSP varianta)

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
React komponentovou alternativu lze spustit přes `docker-compose-NEXTJS3.yml`;
ta používá samostatný objem `erp-nextjs3-sessions`. Přehled je v
[erp-base-nextjs3/README.md](erp-base-nextjs3/README.md).

## JSP varianta v Kubernetes

[kubernetes-JSP.yml](kubernetes-JSP.yml) je lokální Kubernetes alternativa
k [docker-compose-JSP.yml](docker-compose-JSP.yml); původní Compose zůstává
beze změny. Obsahuje namespace `erp-jsp`, PostgreSQL 18 jako StatefulSet
s 10Gi PVC a Deploymenty pro backend, JSP frontend a mock vzdáleného skladu.
Cluster musí mít výchozí StorageClass s dynamickým provisionerem (kind jej
standardně poskytuje; v minikube musí být zapnuté addony `storage-provisioner`
a `default-storageclass`).

### Spuštění v Minikube

Z kořene repozitáře spusťte Minikube s Docker driverem a zapněte addony pro
trvalé úložiště:

```bash
minikube start --driver=docker
minikube addons enable storage-provisioner
minikube addons enable default-storageclass
minikube status
minikube kubectl -- get storageclass
```

Potom podle následujícího návodu sestavte obrazy, nahrajte je do Minikube
příkazem `minikube image load` a nasaďte manifest. Kubernetes příkazy v tomto
návodu používají `minikube kubectl --`, takže nepotřebujete samostatně
nainstalovaný `kubectl`. Před nasazením ověřte kontext. Po nasazení lze
aplikaci otevřít port-forwardem z následujících příkazů. Minikube zůstává
spuštěný i po zavření terminálu; pro jeho zastavení použijte `minikube stop`.
Další spuštění proveďte přes `minikube start`.

Kubernetes nesestavuje Docker obrazy. Z kořene repozitáře je nejprve sestavte:

```bash
docker build -t erp-backend:jsp-local ./erp-backend
docker build -t erp-base-frontend:jsp-local ./erp-base-frontend
docker build -t erp-remote-warehouse:jsp-local ./erp-remote-warehouse
```

Nahrajte je do zvoleného lokálního clusteru (pro kind upravte název clusteru,
pokud není `kind`):

```bash
# minikube
minikube image load erp-backend:jsp-local erp-base-frontend:jsp-local erp-remote-warehouse:jsp-local

# Nebo kind
kind load docker-image erp-backend:jsp-local erp-base-frontend:jsp-local erp-remote-warehouse:jsp-local --name kind
```

Zkontrolujte cílový kontext a vytvořte namespace a Secret s vlastním heslem.
Heslo není součástí manifestu. Příklad zadání hesla je pro Bash:

```bash
minikube kubectl -- config current-context
minikube kubectl -- create namespace erp-jsp --dry-run=client -o yaml | minikube kubectl -- apply -f -
read -r -s -p "PostgreSQL password: " POSTGRES_PASSWORD
echo
minikube kubectl -- -n erp-jsp create secret generic erp-postgres \
  --from-literal=POSTGRES_PASSWORD="$POSTGRES_PASSWORD"
unset POSTGRES_PASSWORD
minikube kubectl -- apply -f kubernetes-JSP.yml
minikube kubectl -- -n erp-jsp rollout status statefulset/postgres --timeout=300s
minikube kubectl -- -n erp-jsp rollout status deployment/backend --timeout=300s
minikube kubectl -- -n erp-jsp rollout status deployment/frontend --timeout=300s
minikube kubectl -- -n erp-jsp rollout status deployment/remote-warehouse-mock --timeout=300s
```

Secret vytvářejte jen při prvním nasazení. Změna Secretu sama nezmění heslo
v již inicializované databázi. PostgreSQL ukládá data do PVC
`erp-postgres-data-postgres-0` na cestě `/var/lib/postgresql`, kterou používá
PostgreSQL 18. Data z Docker Compose se automaticky nepřenášejí; případný
přenos proveďte přes `pg_dump` / `pg_restore`.

Každý port-forward spusťte v samostatném terminálu; používají stejné lokální
porty jako JSP Compose:

```bash
minikube kubectl -- -n erp-jsp port-forward service/frontend 4201:8080
minikube kubectl -- -n erp-jsp port-forward service/backend 8080:8080
minikube kubectl -- -n erp-jsp port-forward service/remote-warehouse-mock 8091:8080
minikube kubectl -- -n erp-jsp port-forward service/postgres 5434:5432
```

Frontend je na `http://localhost:4201`, API na `http://localhost:8080`
a mock skladu na `http://localhost:8091`. Porty nesmějí být současně obsazené
Compose variantou. Služby komunikují uvnitř namespace přes DNS názvy
`postgres`, `backend`, `frontend` a `remote-warehouse-mock`; externí Docker
síť `projects-network` není potřeba.

Init kontejnery čekají na PostgreSQL a backend. Startup, readiness a liveness
probes řídí dostupnost a restarty; backend a frontend kontrolují TCP port,
nikoli úplnou funkčnost aplikace. Kubernetes průběžně restartuje neúspěšné
kontejnery, takže nepřebírá Compose limit pěti restartů backendu.
Frontend má jednu repliku a strategii `Recreate`, protože Tomcat sessions
nejsou sdílené; restart frontendu ukončí stávající přihlášení.
Lokální aplikační obrazy mají `imagePullPolicy: Never`. Po novém sestavení
je znovu nahrajte do clusteru a spusťte:

```bash
minikube kubectl -- -n erp-jsp rollout restart deployment/backend deployment/frontend deployment/remote-warehouse-mock
```

Zastavení aplikací se zachováním databázového PVC a Secretu:

```bash
minikube kubectl -- -n erp-jsp delete deployment backend frontend remote-warehouse-mock
minikube kubectl -- -n erp-jsp delete statefulset postgres
minikube kubectl -- -n erp-jsp delete service postgres backend frontend remote-warehouse-mock
```

Opětovné `minikube kubectl -- apply -f kubernetes-JSP.yml` použije zachovaný PVC.
**Odstranění namespace `erp-jsp` odstraní také PVC a může nenávratně smazat
databázová data.** Manifest je pro lokální vývoj, nikoli produkční nasazení
(neobsahuje Ingress/TLS, zálohování ani produkční omezení prostředků).

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
