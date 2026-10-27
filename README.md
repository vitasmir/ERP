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

V Istio CLI 1.31.x příkaz `istioctl profile list` není dostupný. Přehled
vestavěných profilů najdeš v [dokumentaci Istio](https://istio.io/latest/docs/setup/additional-setup/config-profiles/).
Profil se při instalaci vybírá například takto:

```bash
istioctl install --set profile=demo
```

Tento příkaz instaluje Istio včetně ingress gateway služby typu
`LoadBalancer` do clusteru vybraného v aktuálním `kubectl` kontextu. Je
potřeba před nasazením JSP manifestu, který definuje Istio Gateway a trasu
k frontendu.

Před nasazením sestavte obrazy z kořene repozitáře:

```bash
./build-images-local.sh
```

Skript [build-images-local.sh](./build-images-local.sh) sestaví
`erp-backend:local`, `erp-base-frontend:jsp-local` a `erp-remote-warehouse:local`.
Pro kandidátní backend a JSP frontend použijte
[build-images-canary.sh](./build-images-canary.sh):

```bash
./build-images-canary.sh
```

Ten sestaví `erp-backend:canary` a `erp-base-frontend:jsp-canary`, aniž přepíše
stabilní tagy. Canary používá sdílený mock skladu s tagem `local`.
Oba skripty sestavují aktuální obsah pracovního stromu, lze je spustit
z libovolného adresáře a používají aktuální Docker kontext. Vyžadují běžící
Docker Engine; při chybě se zastaví. Image pouze sestaví, neodesílají je do
registru, nenasazují aplikaci ani nerestartují existující Pody.

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

Pokud workloady v namespace běžely už před povolením Istio injection, restartujte
je po nasazení manifestu, aby se vytvořily znovu s Istio sidecary:

```bash
kubectl -n erp-jsp rollout restart statefulset/postgres deployment/backend deployment/frontend deployment/remote-warehouse-mock
```

Zjistěte externí adresu Istio ingress gateway a otevřete ji v prohlížeči:

```bash
kubectl -n istio-system get service istio-ingressgateway
```

Adresa LoadBalanceru směruje HTTP provoz na frontend. Pokud cluster externí
IP nepřidělí, například pokud zůstane `EXTERNAL-IP` ve stavu `<pending>`,
použijte port-forward:

```bash
kubectl -n istio-system port-forward service/istio-ingressgateway 8088:80
```

Potom otevřete `http://localhost:8088`. Přímý port-forward na frontend
(`kubectl -n erp-jsp port-forward service/frontend 4201:8080`) zůstává také
dostupný.

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
(neobsahuje TLS, zálohování ani produkční omezení prostředků).

### Další kompletní Kubernetes varianty

Pro JSP full-stack canary přes Envoy Gateway (Gateway API) je k dispozici
[návod](docs/KUBERNETES-WEIGHTED-CANARY.md) a Helm chart
[charts/erp-jsp-weighted](charts/erp-jsp-weighted/). Jeden Helm release spravuje
stabilní i canary frontend/backend dvojice a jejich váženou HTTPRoute; každá
frontend verze volá pouze odpovídající backend verzi. K dispozici zůstává také
varianta doplňku ke stávajícímu stacku. Vážený provoz vyžaduje Envoy Gateway a
HPA navíc `metrics-server`.

Každý manifest obsahuje vlastní namespace, frontend, Java backend, PostgreSQL
18 se samostatným PVC a warehouse mock. Varianta tak používá vlastní databázi
a data; sdílené názvy Services se mezi namespaces nekříží.

| Webový projekt | Manifest | Namespace | Frontend |
| --- | --- | --- | --- |
| JSP / Tomcat | [kubernetes-JSP.yml](kubernetes-JSP.yml) | `erp-jsp` | port 8080 |
| JSP / Tomcat, weighted canary | [Helm chart](charts/erp-jsp-weighted/) | `erp-jsp-weighted` | port 8089 + stable/canary |
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

Frontendy PHP a Next.js ukládají sessions do vlastního 1Gi PVC. JSP stable i
canary ukládají sdílené HTTP sessions do PostgreSQL. Každý JSP frontend má dvě
repliky a při aktualizaci používá `RollingUpdate` s `maxUnavailable: 0`;
připravený Pod tak zůstává v provozu, než nový projde readiness kontrolou.
První přechod z dosavadních lokálních Tomcat sessions do databáze zneplatní
stávající JSP sessions; další aktualizace je zachovají.
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


Kubernetes manifests use **`apiVersion`** to identify an API group and version, and **`kind`** to identify the resource type within that API. The available choices depend on what your cluster supports.

For example:

```yaml
apiVersion: apps/v1
kind: Deployment
```

Here, `apps/v1` is the API version and `Deployment` is the kind.

Common kinds in your cluster include:

| `apiVersion` | Example kinds |
|---|---|
| `v1` | `Pod`, `Service`, `Namespace`, `ConfigMap`, `Secret`, `PersistentVolumeClaim` |
| `apps/v1` | `Deployment`, `StatefulSet`, `DaemonSet`, `ReplicaSet` |
| `autoscaling/v2` | `HorizontalPodAutoscaler` |
| `batch/v1` | `Job`, `CronJob` |
| `networking.k8s.io/v1` | `Ingress`, `NetworkPolicy` |
| `gateway.networking.k8s.io/v1` | `Gateway`, `GatewayClass`, `HTTPRoute` |
| `gateway.envoyproxy.io/v1alpha1` | `EnvoyProxy` and Envoy-specific policy resources |

The last two API groups are available because Gateway API and Envoy Gateway are installed in your cluster.

To list **all API versions** your current cluster serves:

```bash
kubectl api-versions
```

To list the **kinds/resources** supported by a particular API group:

```bash
kubectl api-resources --api-group apps
kubectl api-resources --api-group gateway.networking.k8s.io
kubectl api-resources --api-group gateway.envoyproxy.io
```

To check the fields and schema for a specific kind:

```bash
kubectl explain httproute --api-version=gateway.networking.k8s.io/v1
kubectl explain deployment.spec --api-version=apps/v1
```

Kubernetes has built-in kinds, and extensions can add more kinds through **CRDs**. So another cluster may offer a different set; `kubectl api-resources` reports what *your current cluster* supports.
#Rozdily
Hlavní rozdíl je: **shared rozšiřuje stávající ERP, isolated vytváří vlastní kompletní ERP stack.**

| Vlastnost | `shared` | `isolated` |
|---|---|---|
| Namespace | Existující `erp-jsp` | Vlastní `erp-jsp-weighted` |
| Základní nasazení | Vyžaduje již nasazený `kubernetes-JSP.yml` | Obsahuje celý stack |
| PostgreSQL | Používá stávající databázi a PVC | Vytvoří vlastní PostgreSQL a PVC |
| Secret | Používá existující `erp-postgres` | Potřebuje vlastní `erp-postgres` v novém namespace |
| Backend a frontend | **Upraví stávající** Deploymenty a přidá canary verze | Vytvoří nové stabilní i canary Deploymenty |
| Warehouse mock | Používá stávající | Vytvoří vlastní |
| Lokální adresa | `http://localhost:8088/login` | `http://localhost:8089/login` |
| Dopad na původní ERP | Mění jeho škálování a směrování | Původní ERP nemění |

**Obě varianty mají stejné funkce:**
- HPA pro stabilní i canary backend: **2–5 replik, CPU 70 %**.
- Vážené směrování **90 % stabilní / 10 % canary**, zvlášť pro frontend a backend.
- Envoy Gateway a session cookie pro frontend.

**Pozor:** „isolated“ neznamená vlastní databázi pro každý backend. Stabilní a canary backend **uvnitř izolovaného stacku sdílejí jednu databázi** — ta je ale oddělená od databáze původního ERP.

Obě varianty využívají společný Envoy Gateway controller v clusteru, ale každá má vlastní proxy a pravidla. V předchozím kroku jsme nasadili **shared variantu**.









Kubernetes manifests use **`apiVersion`** to identify an API group and version, and **`kind`** to identify the resource type within that API. The available choices depend on what your cluster supports.

For example:

```yaml
apiVersion: apps/v1
kind: Deployment
```

Here, `apps/v1` is the API version and `Deployment` is the kind.

Common kinds in your cluster include:

| `apiVersion` | Example kinds |
|---|---|
| `v1` | `Pod`, `Service`, `Namespace`, `ConfigMap`, `Secret`, `PersistentVolumeClaim` |
| `apps/v1` | `Deployment`, `StatefulSet`, `DaemonSet`, `ReplicaSet` |
| `autoscaling/v2` | `HorizontalPodAutoscaler` |
| `batch/v1` | `Job`, `CronJob` |
| `networking.k8s.io/v1` | `Ingress`, `NetworkPolicy` |
| `gateway.networking.k8s.io/v1` | `Gateway`, `GatewayClass`, `HTTPRoute` |
| `gateway.envoyproxy.io/v1alpha1` | `EnvoyProxy` and Envoy-specific policy resources |

The last two API groups are available because Gateway API and Envoy Gateway are installed in your cluster.

To list **all API versions** your current cluster serves:

```bash
kubectl api-versions
```

To list the **kinds/resources** supported by a particular API group:

```bash
kubectl api-resources --api-group apps
kubectl api-resources --api-group gateway.networking.k8s.io
kubectl api-resources --api-group gateway.envoyproxy.io
```

To check the fields and schema for a specific kind:

```bash
kubectl explain httproute --api-version=gateway.networking.k8s.io/v1
kubectl explain deployment.spec --api-version=apps/v1
```

Kubernetes has built-in kinds, and extensions can add more kinds through **CRDs**. So another cluster may offer a different set; `kubectl api-resources` reports what *your current cluster* supports.




















#Zmeny za behu
Za běhu změň váhy v **HTTPRoute** pomocí `kubectl patch`. Envoy změnu převezme automaticky — **není nutné restartovat aplikaci**.

### Dvojice frontend/backend: 75 % stable / 25 % canary

Pro sdílené nasazení v `erp-jsp`:

```bash
kubectl -n erp-jsp patch httproute frontend --type=json \
  -p='[
    {"op":"replace","path":"/spec/rules/0/backendRefs/0/weight","value":75},
    {"op":"replace","path":"/spec/rules/0/backendRefs/1/weight","value":25}
  ]'
```

Ověření aktuálních vah:

```bash
kubectl -n erp-jsp get httproute frontend \
  -o jsonpath='{range .spec.rules[0].backendRefs[*]}{.name}{": "}{.weight}{"\n"}{end}'
```

### Frontend

Váhy se nastavují na vstupní route `frontend`. Stabilní frontend volá pouze
Service `backend`, canary frontend pouze Service `backend-canary`:

```bash
kubectl -n erp-jsp patch httproute frontend --type=json \
  -p='[
    {"op":"replace","path":"/spec/rules/0/backendRefs/0/weight","value":75},
    {"op":"replace","path":"/spec/rules/0/backendRefs/1/weight","value":25}
  ]'
```

Frontend má session affinity: **již připojení klienti s cookie mohou zůstat na své dosavadní verzi**, nové váhy se projeví především u nových klientů.

- **Vypnout canary provoz:** stable `100`, canary `0`.
- **Přesměrovat vše na canary:** stable `0`, canary `100`.
- Pro izolovaný stack změň namespace na `erp-jsp-weighted`.

**Změnu zapiš také do používaného manifestu** — `shared` nebo `isolated`. Jinak další `kubectl apply` obnoví váhy uložené v souboru.



















Myslím **YAML soubor, ze kterého nasazuješ ERP**. Pro nyní běžící sdílenou variantu je to `kubernetes-JSP-weighted-shared.yml`.

Najdi v něm `kind: HTTPRoute` s `name: frontend`. V jeho `backendRefs` změň například:

```yaml
backendRefs:
  - name: frontend
    port: 8080
    weight: 75
  - name: frontend-canary
    port: 8080
    weight: 25
```

Tato váha vybírá dvojici: stable frontend volá stable backend, canary frontend
volá canary backend.

**`kubectl patch` mění nastavení v běžícím clusteru, ale neupravuje YAML soubor na disku.** Proto je potřeba uložit stejné váhy i do souboru, aby je další `kubectl apply -f ...` nepřepsal původními hodnotami.

Pro izolovanou variantu by to byl `kubernetes-JSP-weighted-isolated.yml`.









Myslím **YAML soubor, ze kterého nasazuješ ERP**. Pro nyní běžící sdílenou variantu je to `kubernetes-JSP-weighted-shared.yml`.

Najdi v něm `kind: HTTPRoute` s `name: frontend`. V jeho `backendRefs` změň například:

```yaml
backendRefs:
  - name: frontend
    port: 8080
    weight: 75
  - name: frontend-canary
    port: 8080
    weight: 25
```

Tato váha vybírá dvojici: stable frontend volá stable backend, canary frontend
volá canary backend.

**`kubectl patch` mění nastavení v běžícím clusteru, ale neupravuje YAML soubor na disku.** Proto je potřeba uložit stejné váhy i do souboru, aby je další `kubectl apply -f ...` nepřepsal původními hodnotami.

Pro izolovanou variantu by to byl `kubernetes-JSP-weighted-isolated.yml`.










Ano. V zápisu:

```yaml
image: erp-backend:canary
```

- **`erp-backend`** je název image repository.
- **`canary`** je **tag** — označení image, zde kandidátní verze aplikace.

Tag nemusí být číslo verze. Může to být například `canary`, `stable`, `1.2.0` nebo identifikátor commitu. **Název `canary` sám nezaručuje jiný obsah** — záleží, jaký image pod tímto tagem sestavíš.

Při našem testu jsme použili:

```bash
docker tag erp-backend:local erp-backend:canary
```

Tím jsme pouze vytvořili další označení **stejného image**, nikoliv novou verzi aplikace. Pro skutečný canary release sestavíš image z upraveného kódu:

```bash
docker build -t erp-backend:canary ./erp-backend
```

O rozdělení provozu rozhodují váhy v `HTTPRoute`, **ne tag `canary`**.



























#`kubectl patch` **částečně upraví Kubernetes prostředek, který už běží v clusteru**, aniž bys musel znovu aplikovat celý YAML manifest.

Například tento příkaz upraví váhy v běžící HTTPRoute:

```bash
kubectl -n erp-jsp patch httproute frontend --type=json \
  -p='[
    {"op":"replace","path":"/spec/rules/0/backendRefs/0/weight","value":75},
    {"op":"replace","path":"/spec/rules/0/backendRefs/1/weight","value":25}
  ]'
```

Tím nastaví 75 % nových klientů na stabilní dvojici frontend/backend a 25 %
na canary dvojici. Existující sessions mohou zůstat na své verzi.
Změní se **zdroj v Kubernetes API**; YAML soubor na disku se tím nezmění.
Prohlédnout aktuální nastavení můžeš takto:

```bash
kubectl -n erp-jsp get httproute frontend -o yaml
```

`--type=json` určuje, že patch používá JSON Patch operace, například `replace` pro změnu existující hodnoty.















#prepis konfigurace tak, at z canary frontendu chodi pozadavky na canary backend a at z stable frontendu chodi pozadavky na stable backend 


Upravil jsem obě konfigurace:
- `shared`
- `isolated`

Nyní platí:
```text
stable frontend → backend:8080
canary frontend → backend-canary:8080
```

Init kontejnery také čekají na odpovídající backend. Odstranil jsem nepotřebný `backend-router` a nezávislé backendové vážené směrování. **Váha 90/10 nyní vybírá celou dvojici frontend/backend.** HPA zůstává zachované.

Validace manifestů i kontrola párování prošly. **Do běžícího clusteru jsem změny zatím neaplikoval.** Pro sdílené nasazení spusť:

```bash
kubectl apply -f kubernetes-JSP-weighted-shared.yml
kubectl -n erp-jsp rollout status deployment/frontend
kubectl -n erp-jsp rollout status deployment/frontend-canary
```

Po dokončení odstraň staré, již nepoužívané prostředky:

```bash
kubectl -n erp-jsp delete httproute backend-api --ignore-not-found
kubectl -n erp-jsp delete service backend-router --ignore-not-found
```

Změna restartuje frontend Pody, takže současná přihlášení mohou zaniknout.














#stable frontend → backend:8080 
canary frontend → backend-canary:8080











Upravil jsem obě konfigurace:
- `shared`
- `isolated`

Nyní platí:
```text
stable frontend → backend:8080
canary frontend → backend-canary:8080
```

Init kontejnery také čekají na odpovídající backend. Odstranil jsem nepotřebný `backend-router` a nezávislé backendové vážené směrování. **Váha 90/10 nyní vybírá celou dvojici frontend/backend.** HPA zůstává zachované.

Validace manifestů i kontrola párování prošly. **Do běžícího clusteru jsem změny zatím neaplikoval.** Pro sdílené nasazení spusť:

```bash
kubectl apply -f kubernetes-JSP-weighted-shared.yml
kubectl -n erp-jsp rollout status deployment/frontend
kubectl -n erp-jsp rollout status deployment/frontend-canary
```

Po dokončení odstraň staré, již nepoužívané prostředky:

```bash
kubectl -n erp-jsp delete httproute backend-api --ignore-not-found
kubectl -n erp-jsp delete service backend-router --ignore-not-found
```

Změna restartuje frontend Pody, takže současná přihlášení mohou zaniknout.















#JAK SMAZAT PODy
V tvém `manifestu` je namespace `erp-jsp-weighted`. Záleží, jestli chceš pody jen smazat, nebo aplikaci skutečně zastavit:

### 1. Smazat jeden pod
```bash
kubectl get pods -n erp-jsp-weighted
kubectl delete pod NAZEV_PODU -n erp-jsp-weighted
```
**Deployment nebo StatefulSet automaticky vytvoří náhradní pod.** Tohle tedy funguje spíš jako restart.

### 2. Zastavit aplikaci bez odstranění konfigurace
Nastav počet replik na nulu:

```bash
# Zastavit všechny Deploymenty v tomto namespace
kubectl scale deployment --all --replicas=0 -n erp-jsp-weighted

# Volitelně zastavit také PostgreSQL
kubectl scale statefulset postgres --replicas=0 -n erp-jsp-weighted
```

Pody se ukončí, ale konfigurace a databázové PVC zůstanou. Nové nasazení nebo automatizace mohou počet replik zase změnit.

### 3. Odstranit celé nasazení podle souboru
```bash
kubectl delete -f kubernetes-JSP-weighted-isolated.yml
```

**Pozor:** soubor obsahuje i definici namespace, takže se smaže celý namespace `erp-jsp-weighted` včetně ostatních prostředků v něm. To může odstranit i databázová PVC a podle konfigurace úložiště také samotná data.

**Pro pouhé zastavení použij variantu 2.**














#smaze to i vse co je v danem namespace?
kubectl delete namespace <namespace-name>

Ano. `kubectl delete namespace <namespace-name>` odstraní namespace i prostředky **v něm** — například pody, Deployments, Services, ConfigMaps, Secrets a PVC. Odstranění může chvíli trvat, než Kubernetes prostředky uklidí.

Pozor na databázová data: smaže se objekt PVC, ale zda se smažou i data na úložišti, závisí na `persistentVolumeReclaimPolicy` příslušného PV nebo na nastavení storage class. Prostředky clusterové úrovně, které nejsou v namespace (například Nodes nebo některé CRD), tímto příkazem odstraněny nejsou.

Pro tvůj namespace by příkaz byl:

```bash
kubectl delete namespace erp-jsp-weighted
```

Před spuštěním můžeš ověřit, co v něm je:

```bash
kubectl get all,pvc -n erp-jsp-weighted
```















```

`kubectl apply -f <file-name>.yaml` předá Kubernetesu YAML manifest jako **požadovaný stav** a ten se pokusí sladit s aktuálním stavem clusteru.

- `kubectl` je nástroj pro komunikaci s Kubernetes.
- `apply` vytvoří prostředky, které ještě neexistují, nebo upraví existující podle změn v manifestu.
- `-f` říká, že následuje cesta k souboru. `<file-name>` je jen zástupný text — nahraď ho skutečným názvem, například:
  ```bash
  kubectl apply -f kubernetes-JSP.yml
  ```

Příkaz pracuje s clusterem zvoleným v aktuálním `kubectl` kontextu. Pokud YAML obsahuje více prostředků, například `Deployment`, `Service` a `PersistentVolumeClaim`, aplikuje je všechny. Opakované spuštění se stejným souborem je běžný způsob, jak změny bezpečně nasadit.

**Co se stane po změně?** Například úprava šablony Podu v `Deployment` obvykle spustí postupnou výměnu Podů. Změna konfigurace `Service` může upravit jeho chování, ale nemusí restartovat Pody.

**Co příkaz sám neudělá:**

- Neodstraní prostředky jen proto, že jsi jejich části nebo celé definice odstranil ze souboru.
- Nečeká automaticky, až aplikace naběhne a bude připravená.
- Nesestaví ani nenahraje Docker image; pouze nastaví, co má Kubernetes použít.

Stav po nasazení můžeš ověřit například:

```bash
kubectl get pods
kubectl rollout status deployment/frontend
```

V tomto repozitáři se JSP varianta nasazuje právě příkazem `kubectl apply -f kubernetes-JSP.yml`; další kroky jsou popsané v `README.md`.


```
Použij `docker compose exec` na běžící službu `postgres`. Pokud máš archiv `/tmp/erp.dump` vytvořený přes `pg_dump -Fc`, spusť z počítače, kde ten soubor leží:

```bash
docker compose -f docker-compose-JSP.yml exec -T postgres \
  pg_restore -U erp -d erp --no-owner --no-privileges - \
  < /tmp/erp.dump
```

`-T` vypne pseudo-terminál, aby se archiv správně poslal přes vstup do kontejneru. `postgres` je název služby v Compose; `erp` je uživatel i databáze podle této konfigurace.

Pokud databáze už obsahuje tabulky a chceš je před obnovou přepsat, přidej `--clean --if-exists`:

```bash
docker compose -f docker-compose-JSP.yml exec -T postgres \
  pg_restore -U erp -d erp --no-owner --no-privileges --clean --if-exists - \
  < /tmp/erp.dump
```

**Pozor:** `--clean` maže objekty z archivu v cílové databázi. Bez této volby může obnova do již naplněné databáze skončit chybami kvůli existujícím tabulkám. Pokud soubor `/tmp/erp.dump` na tom počítači nemáš, nahraď cestu k němu skutečným umístěním.

























#restore dat z postgres do postgres bezicim v kubernetes
Search for `kind: (StatefulSet|Secret)|name: postgres|POSTGRES_DB|POSTGRES_USER|POSTGRES_...`

Search for `kubectl.*exec|port-forward service/postgres|POSTGRES_PASSWORD`
s

V Kubernetes JSP variantě se PostgreSQL jmenuje `postgres-0`, běží v namespace `erp-jsp` a databáze i uživatel se jmenují `erp` — podle `kubernetes-JSP.yml`.

Pokud už máš archiv `/tmp/erp.dump` ve formátu vytvořeném přes `pg_dump -Fc`, pošli ho přes stdin přímo do Podu:

```bash
kubectl -n erp-jsp exec -i postgres-0 -- \
  pg_restore -U erp -d erp --no-owner --no-privileges - \
  < /tmp/erp.dump
```

Nejdřív můžeš ověřit, že Pod běží:

```bash
kubectl -n erp-jsp get pods
```

Příkaz obnovuje **strukturu i data**. Pokud cílová databáze už obsahuje tabulky, obnova může narazit na konflikty. Chceš-li jejich obsah z archivu přepsat, přidej `--clean --if-exists`:

```bash
kubectl -n erp-jsp exec -i postgres-0 -- \
  pg_restore -U erp -d erp --no-owner --no-privileges --clean --if-exists - \
  < /tmp/erp.dump
```

**Pozor:** `--clean` smaže objekty z archivu v cílové databázi. Při nahrazování dat je také vhodné pozastavit aplikaci, aby během obnovy do databáze nezapisovala. Pokud používáš jiný Kubernetes namespace nebo databázi než `erp-jsp`/`erp`, uprav příkaz podle své konfigurace.




















V Kubernetes JSP variantě se PostgreSQL jmenuje `postgres-0`, běží v namespace `erp-jsp` a databáze i uživatel se jmenují `erp` — podle `kubernetes-JSP.yml`.

Pokud už máš archiv `/tmp/erp.dump` ve formátu vytvořeném přes `pg_dump -Fc`, pošli ho přes stdin přímo do Podu:

```bash
kubectl -n erp-jsp exec -i postgres-0 -- \
  pg_restore -U erp -d erp --no-owner --no-privileges - \
  < /tmp/erp.dump
```

Nejdřív můžeš ověřit, že Pod běží:

```bash
kubectl -n erp-jsp get pods
```

Příkaz obnovuje **strukturu i data**. Pokud cílová databáze už obsahuje tabulky, obnova může narazit na konflikty. Chceš-li jejich obsah z archivu přepsat, přidej `--clean --if-exists`:

```bash
kubectl -n erp-jsp exec -i postgres-0 -- \
  pg_restore -U erp -d erp --no-owner --no-privileges --clean --if-exists - \
  < /tmp/erp.dump
```

**Pozor:** `--clean` smaže objekty z archivu v cílové databázi. Při nahrazování dat je také vhodné pozastavit aplikaci, aby během obnovy do databáze nezapisovala. Pokud používáš jiný Kubernetes namespace nebo databázi než `erp-jsp`/`erp`, uprav příkaz podle své konfigurace.


#INSTALL HELM
Pokud používáš **Ubuntu nebo Debian**, nainstaluj Helm z jeho APT repozitáře:

```bash
sudo apt-get install curl gpg apt-transport-https --yes

curl -fsSL https://packages.buildkite.com/helm-linux/helm-debian/gpgkey \
  -o /tmp/helm.gpg

# Ověř, že klíč odpovídá očekávanému otisku
gpg --show-keys --with-colons /tmp/helm.gpg | awk -F: '$1 == "fpr" {print $10}' | head -n 1
```

Výstup otisku má být:
```text
DDF78C3E6EBB2D2CC223C95C62BA89D07698DBC6
```

Pokud souhlasí, přidej repozitář a nainstaluj Helm:

```bash
sudo mkdir -p /usr/share/keyrings
gpg --dearmor < /tmp/helm.gpg | sudo tee /usr/share/keyrings/helm.gpg > /dev/null

echo "deb [signed-by=/usr/share/keyrings/helm.gpg] https://packages.buildkite.com/helm-linux/helm-debian/any/ any main" \
  | sudo tee /etc/apt/sources.list.d/helm-stable-debian.list

sudo apt-get update
sudo apt-get install helm
```

Ověř instalaci:

```bash
helm version
```

Helm pak použije cluster nastavený v `kubectl` — ověř ho příkazem `kubectl config current-context`.





NASTAV HESLO
kubectl -n erp-jsp create secret generic erp-postgres \
  --from-literal=POSTGRES_PASSWORD=erp

kubectl -n erp-jsp get pods -w



# jak pracovat s images
kdyz delam canary deployment s kubernetes se souborem kubernetes-JSP-weighted-isolated.yml, tak musim vzdy vytvorit image image: erp-base-frontend:jsp-local a erp-base-frontend:jsp-canary. Tedy prejmenovat novou canary verzi na erp-base-frontend:jsp-local a vytvorit novy image erp-base-frontend:jsp-canary?
##CANARY RELEASE DEPLOYMENT
Ne tak docela. **Pro současný canary test nepotřebuješ přejmenovat canary image na `jsp-local`.** Manifest spouští obě verze současně:

- stabilní frontend: `erp-base-frontend:jsp-local`
- canary frontend: `erp-base-frontend:jsp-canary`

Proto musí být v Kubernetes dostupné oba obrazy. Stabilní image nemusíš znovu sestavovat při každém canary releasu — použiješ stávající stabilní verzi a sestavíš jen novou canary:

```bash
docker build -t erp-base-frontend:jsp-canary ./erp-base-frontend
```

Protože manifest používá `imagePullPolicy: Never`, obraz musí být dostupný lokálnímu Kubernetes clusteru, například Docker Desktopu.

**Až canary ověříš a chceš ji povýšit na stabilní**, změníš stabilní image na schválenou verzi a další kandidát dostane nový canary tag. Pozor: když hned přetaguješ kandidáta jako `jsp-local`, stabilní Deployment může začít používat kandidátní kód — canary tak přestane být oddělená. Bezpečnější je používat verziované tagy, třeba `erp-base-frontend:1.4.0` a `erp-base-frontend:1.5.0-rc1`, a upravit odpovídající image v `kubernetes-JSP-weighted-isolated.yml`.






# Jak se vratit zpet?
Záleží, jestli chceš **zastavit canary provoz**, nebo **vrátit Deployment na předchozí obraz**.

### Zastavit provoz na canary verzi

Ve tvém weighted manifestu canary ještě nemění stabilní Deployment. Přesměruj proto všechen nový provoz na stabilní frontend:

```bash
kubectl -n erp-jsp-weighted patch httproute frontend --type=json \
  -p='[{"op":"replace","path":"/spec/rules/0/backendRefs/0/weight","value":100},{"op":"replace","path":"/spec/rules/0/backendRefs/1/weight","value":0}]'
```

Ulož stejné váhy i do `kubernetes-JSP-weighted-isolated.yml`, jinak je další `kubectl apply` přepíše. Uživatelé s existující session cookie mohou podle nastavení session persistence ještě zůstat na canary.

### Vrátit canary Deploymenty na předchozí revizi

Kubernetes uchovává historii změn pod šablony Deploymentů. Podívej se na ni:

```bash
kubectl -n erp-jsp-weighted rollout history deployment/frontend-canary
kubectl -n erp-jsp-weighted rollout history deployment/backend-canary
```

Pak lze vrátit oba Deploymenty:

```bash
kubectl -n erp-jsp-weighted rollout undo deployment/frontend-canary
kubectl -n erp-jsp-weighted rollout undo deployment/backend-canary
```

Canary frontend a backend vrať jako pár, aby spolu jejich verze zůstaly kompatibilní. `rollout undo` se týká každého Deploymentu zvlášť, není to atomická operace.

Pokud už byla nová verze povýšena na stabilní, zkontroluj a případně vrať také `deployment/frontend` a `deployment/backend`. Při nasazení přes Helm chart je vhodnější použít `helm history erp-jsp-weighted -n erp-jsp-weighted` a `helm rollback` na předchozí release. U všech metod platí, že rollback aplikace **nevrací databázové migrace**; starý backend musí být kompatibilní se současným schématem databáze.










# Jak se sparuji backend a frontend v canary verzi
`/dev/tcp/backend-canary/8080` je speciální zápis, který rozpoznává **Bash**. Není to běžný soubor na disku. Bash ho interpretuje jako pokus otevřít TCP spojení na host `backend-canary`, port `8080`.

V `manifestu` je použit takto:

```bash
until (echo > /dev/tcp/backend-canary/8080); do
  echo "Waiting for canary backend"
  sleep 5
done
```

`backend-canary` je DNS název Kubernetes Service. Smyčka každých pět sekund zkusí TCP spojení; dokud se nepodaří, čeká. Jakmile se spojení otevře, init kontejner skončí a Kubernetes může spustit frontend kontejner.

Je to jen **kontrola dostupnosti TCP portu při startu** — neověřuje, že backend správně zpracovává HTTP požadavky nebo že aplikace je plně funkční.



# Jak se paruji kontainery v stabilni verzi?
U stabilní verze je to stejné, jen se používá stabilní Service `backend`:

- Init kontejner stabilního frontendu zkouší TCP spojení na `backend:8080` každých pět sekund (`řádky 348–349`).
- Samotný frontend posílá požadavky na `http://backend:8080` přes proměnnou `BACKEND_URL` (`řádek 360`).
- Service `backend` vybírá backend pody se štítkem `app: backend` (`Service backend`).

Tedy dvojice stabilní verze je `frontend → backend`, zatímco canary dvojice je `frontend-canary → backend-canary`.






# Jak je to s loadballancingem?
V manifestu jsou **dvě vrstvy rozdělování provozu**:

1. **Envoy Gateway rozděluje příchozí provoz mezi stable a canary frontend.** `HTTPRoute` má váhy `frontend: 90` a `frontend-canary: 10` (`HTTPRoute`). To je poměr pro nové návštěvy — ne záruka, že přesně 90 % všech požadavků připadne na stable.

2. **Každá Kubernetes Service rozděluje požadavky mezi pody své verze.** Service `frontend` vybírá stable frontend pody a Service `frontend-canary` vybírá canary frontend pody (`stable Service`, `canary Service`). Podobně `backend` a `backend-canary` rozdělují provoz jen mezi své vlastní backend pody (`stable backend Service`, `canary backend Service`).

Frontend verze jsou navíc pevně spárované: stable frontend volá `backend:8080`, canary frontend `backend-canary:8080`. Díky tomu se provoz canary frontendu neposílá do stable backendu.

Manifest používá session cookie pro frontend, aby návštěvník zůstal u vybrané frontend verze. Proto může skutečný podíl návštěv či API volání po chvíli od poměru `90/10` odchýlit. Backendová HPA škáluje každou verzi zvlášť.

























# Ktere radky se musi zmenit pri canary deployemntu?
Při novém canary releasu v `kubernetes-JSP-weighted-isolated.yml` měníš **obrazy canary verze**, ne stable:

| Co měníš | Řádky | Příklad |
|---|---:|---|
| Canary backend image | 222 | `erp-backend:canary` → nový tag |
| Canary frontend image v init kontejneru | 445 | `erp-base-frontend:jsp-canary` → nový tag |
| Canary frontend image v hlavním kontejneru | 457 | musí mít stejný nový tag jako řádek 445 |

Například při verzovaných tagech:

```yaml
# řádek 222
image: erp-backend:1.5.0-rc1

# řádky 445 a 457
image: erp-base-frontend:1.5.0-rc1
```

Tagy sestav tak, aby odpovídaly těmto názvům, a pak manifest aplikuj. Váhy canary měň jen tehdy, když chceš upravit podíl provozu: stabilní je na řádku 511, canary na řádku 514. Například z `90/10` na `75/25`.

**Stabilní image** na řádcích 126, 342 a 354 při běžném testu canary neměň. Až canary schválíš a povýšíš ji na stabilní, přepneš stabilní odkazy na schválené obrazy a pro další kandidátku nastavíš nové canary tagy.

Doporučuji unikátní verziované tagy. Pokud místo toho přepíšeš obsah pod stejným tagem, manifest se nezmění a Kubernetes nemusí spustit nový rollout; s `imagePullPolicy: Never` pak po sestavení lokálních obrazů musíš canary Deploymenty restartovat.