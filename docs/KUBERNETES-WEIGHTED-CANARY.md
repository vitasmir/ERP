# Vážený canary provoz a HPA pro JSP ERP

Obě varianty používají Envoy Gateway a Gateway API. `HTTPRoute` rozděluje
provoz mezi stabilní a canary Service pomocí `backendRefs[].weight: 90/10`.
Váhy backendu a frontendu jsou nezávislé. Service typu `LoadBalancer` pouze
zpřístupňuje Envoy proxy; sama vážené směrování neprovádí.

Komunitní ingress-nginx byl v březnu 2026 ukončen a nedostává bezpečnostní
opravy. Tyto manifesty jej proto již nepoužívají.

## Controller a metriky

Potřebuješ Helm CLI, dostupné image v clusteru a výchozí StorageClass.
Envoy Gateway v1.9 podporuje Kubernetes 1.36 používaný v Docker Desktopu.
Instalace controlleru, CRD a společné GatewayClass:

```bash
helm upgrade --install eg oci://docker.io/envoyproxy/gateway-helm \
  --version v1.9.2 -n envoy-gateway-system --create-namespace \
  --wait --timeout 5m
kubectl apply -f deploy/envoy-gateway.yml
```

Podrobnosti: [Envoy Gateway quickstart](https://gateway.envoyproxy.io/docs/tasks/quickstart/).

HPA potřebuje Metrics API:

```bash
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/download/v0.9.0/components.yaml
kubectl -n kube-system rollout status deployment/metrics-server --timeout=120s
kubectl wait --for=condition=Available \
  apiservice/v1beta1.metrics.k8s.io --timeout=90s
kubectl top nodes
```

Na lokálním Docker Desktopu může kontrola certifikátu kubeletu selhat, protože
certifikát neobsahuje IP SAN. Pouze pro tento vývojový cluster lze použít:

```bash
kubectl -n kube-system patch deployment metrics-server --type=strategic \
  --patch-file deploy/metrics-server-docker-desktop-patch.yml
```

Patch vypíná ověřování TLS certifikátu kubeletu. Nepoužívej jej v produkci;
tam oprav certifikát a jeho důvěryhodnost. Po změně znovu ověř rollout a
dostupnost Metrics API.

## Image a databáze

Stabilní backend používá `erp-backend:local`, canary `erp-backend:canary`.
Frontend používá `erp-base-frontend:jsp-local` a `erp-base-frontend:jsp-canary`.
`imagePullPolicy: Never` vyžaduje dostupnost image v každém příslušném nodu.
Pro skutečný release sestav canary image z kandidátní verze kódu.

Pro výukový test směrování lze vytvořit kopie současných image:

```bash
docker tag erp-backend:local erp-backend:canary
docker tag erp-base-frontend:jsp-local erp-base-frontend:jsp-canary
```

Takový test neověřuje novou verzi aplikace, pouze oddělené Deploymenty a routing.
Stabilní i canary backend sdílejí databázi daného namespace. Backend při
spuštění provádí Flyway migrace, proto musí být zpětně kompatibilní. Před
skutečným canary releasem ověř zálohu databáze a kompatibilitu migrací.

## Varianta 1: stávající ERP

[kubernetes-JSP-weighted-shared.yml](../kubernetes-JSP-weighted-shared.yml)
rozšiřuje základní [kubernetes-JSP.yml](../kubernetes-JSP.yml) v `erp-jsp`.
Databáze i její Secret zůstávají zachovány. Doplněk upraví stabilní backend a
frontend, přidá canary Deploymenty, HPA a Gateway routing. Frontend při změně
adresy backendu restartuje; dosavadní přihlášení v paměti Tomcatu zaniknou.

```bash
kubectl apply -f kubernetes-JSP-weighted-shared.yml
kubectl -n erp-jsp rollout status deployment/backend --timeout=300s
kubectl -n erp-jsp rollout status deployment/backend-canary --timeout=300s
kubectl -n erp-jsp rollout status deployment/frontend --timeout=300s
kubectl -n erp-jsp rollout status deployment/frontend-canary --timeout=300s
kubectl -n erp-jsp get gateway,httproute,hpa
```

LoadBalancer služba se jmenuje `erp-jsp-proxy` v `envoy-gateway-system`.
Na Docker Desktopu otevři `http://localhost:8088/login`. Hostname
`erp-jsp.local` je také podporovaný, pokud jej nastavíš v DNS nebo `hosts`.

Po aplikaci doplňku neaplikuj samotný základní manifest bez opakování doplňku:
základní frontend volá Service `backend` přímo a vážený routing by obešel.

## Varianta 2: vlastní namespace a databáze

[kubernetes-JSP-weighted-isolated.yml](../kubernetes-JSP-weighted-isolated.yml)
obsahuje kompletní stack v `erp-jsp-weighted` s vlastním PVC a databází:

```bash
kubectl create namespace erp-jsp-weighted \
  --dry-run=client -o yaml | kubectl apply -f -
read -r -s -p "PostgreSQL password: " POSTGRES_PASSWORD
echo
kubectl -n erp-jsp-weighted create secret generic erp-postgres \
  --from-literal=POSTGRES_PASSWORD="$POSTGRES_PASSWORD"
unset POSTGRES_PASSWORD
kubectl apply -f kubernetes-JSP-weighted-isolated.yml
kubectl -n erp-jsp-weighted rollout status statefulset/postgres --timeout=300s
kubectl -n erp-jsp-weighted rollout status deployment/backend --timeout=300s
kubectl -n erp-jsp-weighted rollout status deployment/backend-canary --timeout=300s
kubectl -n erp-jsp-weighted rollout status deployment/frontend --timeout=300s
kubectl -n erp-jsp-weighted rollout status deployment/frontend-canary --timeout=300s
```

Secret vytvoř pouze při prvním nasazení; jeho změna nemění heslo již
inicializované databáze. Na Docker Desktopu otevři `http://localhost:8089/login`.
Podporovaný hostname je `erp-jsp-weighted.local`, proxy Service se jmenuje
`erp-jsp-weighted-proxy`. Obě varianty mohou běžet současně bez konfliktu
backendových hostname nebo veřejných portů, pokud má cluster dost kapacity.

## Jak teče provoz

```text
prohlížeč -> LoadBalancer/Envoy -> frontend nebo frontend-canary
frontend -> backend-router (DNS alias) -> Envoy -> backend nebo backend-canary
```

`backend-router` je ExternalName alias na příslušnou Envoy proxy Service.
Frontendy používají plný DNS název vlastního namespace, který je také hostname
backendové HTTPRoute. Tak se provoz obou stacků nemůže zaměnit.

Frontendová route používá session cookie `ERP_GATEWAY_SESSION`, aby se
uživatel držel konkrétního frontend Podu; Tomcat sessions nejsou sdílené.
Backendová route cookie affinity nepoužívá: API tokeny jsou uložené ve společné
databázi. Poměr 90/10 je náhodný a nemusí přesně odpovídat malé sérii požadavků.

## Změna váhy a rollback provozu

Například změna backend canary podílu z 10 % na 25 %:

```bash
kubectl -n erp-jsp patch httproute backend-api --type=json \
  -p='[{"op":"replace","path":"/spec/rules/0/backendRefs/0/weight","value":75},{"op":"replace","path":"/spec/rules/0/backendRefs/1/weight","value":25}]'
```

Změnu zapiš i do používaného manifestu, jinak další `apply` obnoví 90/10.
V izolovaném stacku použij namespace `erp-jsp-weighted`.
Pro zastavení backend canary provozu nastav stabilní váhu na 100 a canary na 0.
To vrací pouze provoz, nikoli databázové migrace.

## HPA a kontrola provozu

Každý backend má vlastní HPA: minimum 2, maximum 5 replik, cílové CPU 70 %
z requestu `250m`. Celkem tedy mohou oba backendy požadovat až 10 Podů.
HPA přidává Pody, ne nody; dostatek paměti, CPU a databázových spojení musíš
zajistit zvlášť. Opakovaný `apply` s `replicas: 2` může dočasně vrátit počet
replik na 2, než HPA znovu vyhodnotí zátěž.

```bash
kubectl -n erp-jsp get gateway,httproute,hpa
kubectl -n erp-jsp get pods -o wide
kubectl top pods -n erp-jsp
kubectl -n envoy-gateway-system get services
kubectl -n envoy-gateway-system logs \
  -l gateway.envoyproxy.io/owning-gateway-namespace=erp-jsp \
  -c envoy --tail=20
```

Gateway musí mít `Programmed=True`; HTTPRoute podmínky `Accepted=True` a
`ResolvedRefs=True`. Access log obsahuje `upstream_host`, který lze porovnat
s IP adresami stabilních a canary Podů. Nepřihlášený požadavek na
`/api/v1/auth/me` má vracet 401, zatímco veřejný katalog lze testovat bez hesla.
Pokud cluster LoadBalancer nepodporuje, pro lokální kontrolu použij
`kubectl -n envoy-gateway-system port-forward service/erp-jsp-proxy 8088:8088`.
