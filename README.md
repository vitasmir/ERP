# Retail ERP

MVP životního cyklu letákové akce pro retailový řetězec.

## Stack

- `erp-backend`: Spring Boot 4.1.1, Java 21, JPA, Flyway
- `erp-base-frontend`: legacy JSP/Servlet frontend, Java 21, Jakarta Servlet 6.1, Tomcat 10.1
- `erp-base-nextjs2`: Next.js 16.3.8, TypeScript, Twig.js, Node.js 22; původní PHP vzhled, CSS a JavaScript
- `erp-base-nextjs3`: Next.js 16.3.8, React/TSX, Node.js 22; komponentová alternativa bez Twig
- `erp-base-nextjs`: zachovaný první React frontend
- `docker-compose-PHP.yml`: Symfony frontend, PostgreSQL, backend a mock skladu
- PostgreSQL 18
- Docker Compose
- Kubernetes (lokální JSP varianta)

## Podrobná architektura a instalační návody

Mermaid diagramy, zásadní workflow a postup od lokálního setupu přes Compose
po Kubernetes jsou oddělené podle frontendové varianty:

- [JSP / Tomcat a společný Java backend](./docs/architecture/ARCHITECTURE-FULL-JSP.md)
- [PHP / Symfony](./docs/architecture/ARCHITECTURE-FULL-PHP.md)
- [Next.js / první React varianta](./docs/architecture/ARCHITECTURE-FULL-NEXTJS.md)
- [Next.js / Twig.js](./docs/architecture/ARCHITECTURE-FULL-NEXTJS2.md)
- [Next.js / React TSX](./docs/architecture/ARCHITECTURE-FULL-NEXTJS3.md)

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
Cluster musí mít výchozí StorageClass s dynamickým provisionerem pro PVC
databáze a sessions. Níže uvedený postup používá Kubernetes v Docker Desktopu.

### Spuštění v Docker Desktopu

V Docker Desktopu zapněte Kubernetes v **Settings → Kubernetes** a počkejte,
dokud cluster nebude spuštěný. V terminálu ověřte kontext, uzel a výchozí
StorageClass. Pokud příkaz `kubectl` není dostupný, nainstalujte Kubernetes
CLI nebo jej zpřístupněte v PATH.

```bash
kubectl config use-context docker-desktop
kubectl get nodes
kubectl get storageclass
```

Před nasazením sestavte obrazy z kořene repozitáře:

```bash
docker build -t erp-backend:local ./erp-backend
docker build -t erp-base-frontend:jsp-local ./erp-base-frontend
docker build -t erp-remote-warehouse:local ./erp-remote-warehouse
```

Manifest používá `imagePullPolicy: Never`, takže Kubernetes obrazy nestahuje
z registru. Obrazy sestavte pomocí Docker Desktopu a ujistěte se, že jsou
dostupné i Kubernetes clusteru. Pokud pod skončí s chybou
`ErrImageNeverPull`, nakonfigurujte sdílené úložiště obrazů Docker Desktopu
nebo obrazy zpřístupněte přes registr.

Vytvořte namespace a Secret s vlastním heslem. Heslo není součástí manifestu.
Následující příklad zadání hesla je pro Bash a Secret se vytváří pouze při
prvním nasazení:

```bash
kubectl create namespace erp-jsp --dry-run=client -o yaml | kubectl apply -f -
read -r -s -p "PostgreSQL password: " POSTGRES_PASSWORD
echo
kubectl -n erp-jsp create secret generic erp-postgres \
  --from-literal=POSTGRES_PASSWORD="$POSTGRES_PASSWORD"
unset POSTGRES_PASSWORD
kubectl apply -f kubernetes-JSP.yml
kubectl -n erp-jsp rollout status statefulset/postgres --timeout=300s
kubectl -n erp-jsp rollout status deployment/backend --timeout=300s
kubectl -n erp-jsp rollout status deployment/frontend --timeout=300s
kubectl -n erp-jsp rollout status deployment/remote-warehouse-mock --timeout=300s
```

Secret vytvářejte jen při prvním nasazení. Změna Secretu sama nezmění heslo
v již inicializované databázi. PostgreSQL ukládá data do PVC
`erp-postgres-data-postgres-0` na cestě `/var/lib/postgresql`, kterou používá
PostgreSQL 18. Data z Docker Compose se automaticky nepřenášejí; případný
přenos proveďte přes `pg_dump` / `pg_restore`.

Každý port-forward spusťte v samostatném terminálu; používají stejné lokální
porty jako JSP Compose:

```bash
kubectl -n erp-jsp port-forward service/frontend 4201:8080
kubectl -n erp-jsp port-forward service/backend 8080:8080
kubectl -n erp-jsp port-forward service/remote-warehouse-mock 8091:8080
kubectl -n erp-jsp port-forward service/postgres 5434:5432
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
zajistěte jejich dostupnost v clusteru a spusťte:

```bash
kubectl -n erp-jsp rollout restart deployment/backend deployment/frontend deployment/remote-warehouse-mock
```

Zastavení aplikací se zachováním databázového PVC a Secretu:

```bash
kubectl -n erp-jsp delete deployment backend frontend remote-warehouse-mock
kubectl -n erp-jsp delete statefulset postgres
kubectl -n erp-jsp delete service postgres backend frontend remote-warehouse-mock
```

Opětovné `kubectl apply -f kubernetes-JSP.yml` použije zachovaný PVC.
**Odstranění namespace `erp-jsp` odstraní také PVC a může nenávratně smazat
databázová data.** Manifest je pro lokální vývoj, nikoli produkční nasazení
(neobsahuje Ingress/TLS, zálohování ani produkční omezení prostředků).

### Další kompletní Kubernetes varianty

Pro vážené canary směrování JSP frontendu přes NGINX Ingress Controller jsou
k dispozici [návod a dvě varianty manifestů](docs/KUBERNETES-WEIGHTED-CANARY.md):
doplněk ke stávajícímu ERP stacku nebo samostatný stack s vlastní databází.
Vážený provoz vyžaduje samostatně nainstalovaný NGINX Ingress Controller.

Každý manifest obsahuje vlastní namespace, frontend, Java backend, PostgreSQL
18 se samostatným PVC a warehouse mock. Varianta tak používá vlastní databázi
a data; sdílené názvy Services se mezi namespaces nekříží.

| Webový projekt | Manifest | Namespace | Frontend |
| --- | --- | --- | --- |
| JSP / Tomcat | [kubernetes-JSP.yml](kubernetes-JSP.yml) | `erp-jsp` | port 8080 |
| JSP / Tomcat, weighted canary | [kubernetes-JSP-weighted-isolated.yml](kubernetes-JSP-weighted-isolated.yml) | `erp-jsp-weighted` | port 8080 + 10% canary |
| PHP / Symfony | [kubernetes-PHP.yml](kubernetes-PHP.yml) | `erp-php` | port 80 |
| Next.js / React | [kubernetes-NEXTJS.yml](kubernetes-NEXTJS.yml) | `erp-nextjs` | port 3000 |
| Next.js / Twig.js | [kubernetes-NEXTJS2.yml](kubernetes-NEXTJS2.yml) | `erp-nextjs2` | port 3000 |
| Next.js / React TSX | [kubernetes-NEXTJS3.yml](kubernetes-NEXTJS3.yml) | `erp-nextjs3` | port 3000 |

Z kořene repozitáře sestavte potřebné obrazy pomocí Docker Desktopu. Obrazy
musí být dostupné Kubernetes clusteru; viz poznámku o `imagePullPolicy: Never`
v JSP návodu výše.

```bash
docker build -t erp-backend:local ./erp-backend
docker build -t erp-remote-warehouse:local ./erp-remote-warehouse
docker build -t erp-base-frontend:jsp-local ./erp-base-frontend
docker build -t erp-base-php:local ./erp-base-php
docker build -t erp-base-nextjs:local ./erp-base-nextjs
docker build -t erp-base-nextjs2:local ./erp-base-nextjs2
docker build -t erp-base-nextjs3:local ./erp-base-nextjs3
```

Vyberte **jeden** manifest a odpovídající namespace z tabulky. Příklad pro PHP:

```bash
kubectl create namespace erp-php --dry-run=client -o yaml | kubectl apply -f -
read -r -s -p "PostgreSQL password: " POSTGRES_PASSWORD
echo
kubectl -n erp-php create secret generic erp-postgres \
  --from-literal=POSTGRES_PASSWORD="$POSTGRES_PASSWORD"
unset POSTGRES_PASSWORD
read -r -s -p "Symfony APP_SECRET: " APP_SECRET
echo
kubectl -n erp-php create secret generic erp-php-frontend \
  --from-literal=APP_SECRET="$APP_SECRET"
unset APP_SECRET
kubectl apply --dry-run=client --validate=strict -f kubernetes-PHP.yml
kubectl apply -f kubernetes-PHP.yml
kubectl -n erp-php rollout status statefulset/postgres --timeout=300s
kubectl -n erp-php rollout status deployment/backend --timeout=300s
kubectl -n erp-php rollout status deployment/frontend --timeout=300s
kubectl -n erp-php rollout status deployment/remote-warehouse-mock --timeout=300s
```

U jiné varianty nahraďte namespace, manifest i secret příkaz podle tabulky.
Secret vytvořte před prvním nasazením; při opakovaném nasazení ho nevytvářejte
znovu. Stejný postup vytvoření namespace a PostgreSQL Secretu platí pro JSP
a Next.js manifesty; ty nepotřebují Secret `erp-php-frontend`. Každý manifest
lze validovat ještě před nasazením:

```bash
kubectl apply --dry-run=client --validate=strict -f kubernetes-NEXTJS2.yml
```

Frontendy PHP a Next.js ukládají sessions do vlastního 1Gi PVC. JSP používá
Tomcat sessions lokálně v Podu a při jeho výměně se mohou ztratit. Všechny
frontend Deployments používají jedinou repliku i `Recreate`, aby při výměně
nebyly dvě instance se stejnou session store současně aktivní.
PostgreSQL síťový port je ve všech manifesty 5432; 5434 se používá pouze
jako lokální port-forward. Pro každý port-forward otevřete zvláštní terminál:

```bash
kubectl -n erp-php port-forward service/frontend 4201:80
kubectl -n erp-php port-forward service/backend 8080:8080
kubectl -n erp-php port-forward service/postgres 5434:5432
```

Pro jiný namespace změňte `erp-php` a port frontendu podle tabulky.
Host port 4201 nebo 3000 lze použít jen pro jeden aktivní port-forward.
Odstranění namespace odstraní jeho PVC a může nenávratně
smazat databázi i uložené sessions. Původní JSP návod používá image tagy
`erp-backend:local` a `erp-remote-warehouse:local`.

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






kubectl --context kind-erp-k8s -n headlamp create token headlamp
eyJhbGciOiJSUzI1NiIsImtpZCI6IjlXUUlNV2lVN2RFMGNzd05lbV9TNUFwMjlyTktfYlVMREx1XzMzTTVsMDgifQ.eyJhdWQiOlsiaHR0cHM6Ly9rdWJlcm5ldGVzLmRlZmF1bHQuc3ZjLmNsdXN0ZXIubG9jYWwiXSwiZXhwIjoxNzkxNzQ0ODgwLCJpYXQiOjE3OTE3NDEyODAsImlzcyI6Imh0dHBzOi8va3ViZXJuZXRlcy5kZWZhdWx0LnN2Yy5jbHVzdGVyLmxvY2FsIiwianRpIjoiOWMyNWI0OTEtZjdkNC00MTc5LWJkMWMtMzU2YjhmZTFiNzYxIiwia3ViZXJuZXRlcy5pbyI6eyJuYW1lc3BhY2UiOiJoZWFkbGFtcCIsInNlcnZpY2VhY2NvdW50Ijp7Im5hbWUiOiJoZWFkbGFtcCIsInVpZCI6Ijc0MzRlNTk4LWJmMjEtNGExNy04ZWJlLWNiOWUwYWI3MmYwMyJ9fSwibmJmIjoxNzkxNzQxMjgwLCJzdWIiOiJzeXN0ZW06c2VydmljZWFjY291bnQ6aGVhZGxhbXA6aGVhZGxhbXAifQ.EPA4oAd0sam0AyDAokCT8-I863khLLIhOXYPWBhN6DazfCKlPzEiY6VsSORRlWHSb3M7QIwqSP6D1L7Io9Fn_YOkItoNy8mDUWQ7p3KUIwpK-QcAe2ttUVaG5tyclOpuo4GyuBTz_XDqTjc0aYt1iMHn6xMu7JBqV0ln-xhZaONSY_u_RfRLDdnee2XTyPSjeWhdcLPuiVYv7IjmjZTXBBjv2HnOsXF-thdrqAQ1iba2xQ7g8dKWyycHWGmYF6FvYYOMtaoZFzTQOF3XSI4PdQxnVC4e0vEhoTYlz-UqkmmF2hmunbhLu3sYYSAUgBT9qStv0vCq86ydMEzNwzlNyA