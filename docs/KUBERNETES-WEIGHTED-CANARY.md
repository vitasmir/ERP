# Vážený canary provoz a HPA pro JSP ERP

Obě varianty používají Envoy Gateway a Gateway API. `HTTPRoute` rozděluje
provoz mezi stabilní a canary frontend Service pomocí `backendRefs[].weight`.
Sdílený YAML používá poměr 90/10, izolovaný YAML začíná bezpečně na 100/0.
Stabilní frontend volá pouze stabilní backend, canary frontend pouze canary
backend. Váha tedy vybírá celou dvojici frontend/backend. Service typu `LoadBalancer` pouze
zpřístupňuje Envoy proxy; sama vážené směrování neprovádí.

Pro samostatný stack s vlastní databází je doporučený způsob nasazení Helm
chart [`charts/erp-jsp-weighted`](../charts/erp-jsp-weighted/). Chart eviduje
stabilní i canary Deploymenty, jejich Services, HPA, databázi a gateway jako
jeden Helm release. Frontend a backend zůstávají samostatné workloady:
každý frontend je pevně nakonfigurovaný na backend stejné verze. Gateway
rozděluje provoz pouze mezi frontendové Services, takže uživatel zůstává na
odpovídající dvojici.

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

Z kořene repozitáře lze použít samostatné build skripty:

```bash
./build-images-local.sh
./build-images-canary.sh
```

[build-images-local.sh](../build-images-local.sh) sestaví stabilní backend,
JSP frontend a sdílený mock skladu.
[build-images-canary.sh](../build-images-canary.sh) sestaví pouze canary backend
a JSP frontend; stabilní tagy nemění. Každý skript používá aktuální pracovní
strom a Docker kontext. Pro novou canary verzi tedy nejprve připrav kandidátní
kód a potom spusť canary skript. Skripty image nepushují ani nenasazují
a nerestartují běžící Pody. Při chybě se zastaví.

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
základní manifest obnovuje nastavení stabilního backendu bez HPA resource
requests. Doplněk musí zůstat zdrojem konfigurace pro váženou variantu.

## Varianta 2: vlastní namespace a databáze (Helm)

Předpoklady: Envoy Gateway controller a `GatewayClass erp-envoy` jsou
nainstalované (viz část Controller a metriky), obrazy jsou dostupné uzlům a
cluster má StorageClass pro databázové PVC. Vytvoř namespace se sidecar
injection a databázovým secretem:

```bash
kubectl create namespace erp-jsp-weighted
kubectl label namespace erp-jsp-weighted istio-injection=enabled
read -r -s -p "PostgreSQL password: " POSTGRES_PASSWORD
echo
kubectl -n erp-jsp-weighted create secret generic erp-postgres \
  --from-literal=POSTGRES_PASSWORD="$POSTGRES_PASSWORD"
unset POSTGRES_PASSWORD
```

Nasaď celý stable/canary stack jako jeden Helm release. První instalace nechá
veškerý provoz na stabilní dvojici; po ověření obou canary workloadů můžeš
canary provoz postupně zapnout:

```bash
helm upgrade --install erp-jsp-weighted charts/erp-jsp-weighted \
  --namespace erp-jsp-weighted \
  --atomic --wait --timeout 10m \
  --set route.stableWeight=100 \
  --set route.canaryWeight=0

kubectl -n erp-jsp-weighted rollout status statefulset/postgres --timeout=300s
kubectl -n erp-jsp-weighted rollout status deployment/backend --timeout=300s
kubectl -n erp-jsp-weighted rollout status deployment/backend-canary --timeout=300s
kubectl -n erp-jsp-weighted rollout status deployment/frontend --timeout=300s
kubectl -n erp-jsp-weighted rollout status deployment/frontend-canary --timeout=300s

helm upgrade erp-jsp-weighted charts/erp-jsp-weighted \
  --namespace erp-jsp-weighted \
  --reuse-values --atomic --wait --timeout 10m \
  --set route.stableWeight=90 \
  --set route.canaryWeight=10
```

Při vydání nové canary verze změň v jednom `helm upgrade` jak tag backendu,
tak tag frontendu. Nech váhy na 100/0, dokud oba rollouty nedoběhnou; pak
váhu canary zvyš. `Recreate` na obou verzích backendu i frontendu brání
smíchání starých a nových replik v téže verzi během aktualizace. Release
spravuje Kubernetes objekty jako celek, ale databázové migrace ani samotné
spuštění více Deploymentů nejsou atomická transakce.

Hodnoty obrazů, replik, HPA, storage a vah lze upravit v
[`values.yaml`](../charts/erp-jsp-weighted/values.yaml). Secret vytvoř pouze
při prvním nasazení; jeho změna nemění heslo již inicializované databáze.
Helm uninstall odstraní StatefulSet, nikoli automaticky data v PVC. Namespace
nesmaž, pokud si přeješ zachovat databázová data. Na Docker Desktopu otevři
`http://localhost:8089/login`.
Podporovaný hostname je `erp-jsp-weighted.local`, proxy Service se jmenuje
`erp-jsp-weighted-proxy`. Obě varianty mohou běžet současně bez konfliktu
veřejných portů, pokud má cluster dost kapacity.

Stávající YAML varianta zůstává dostupná v
[kubernetes-JSP-weighted-isolated.yml](../kubernetes-JSP-weighted-isolated.yml).
Nepoužívej ji současně s Helm releasem ve stejném namespace, protože oba
způsoby spravují stejné Kubernetes objekty.

## Izolovaný YAML: komponenty a význam jednotlivých polí

Tato část vysvětluje přímo
[kubernetes-JSP-weighted-isolated.yml](../kubernetes-JSP-weighted-isolated.yml).
Komentáře u řádků manifestu popisují konkrétní hodnoty; následující přehled
vysvětluje, jak na sebe navazují. Nejde o výpis vyrenderovaného Helm chartu:
například strategie aktualizace je v tomto YAML explicitně `Recreate`
pouze u stabilního frontendu. Ostatní Deploymenty používají výchozí
`RollingUpdate`.

### Společná struktura každého objektu

| Pole nebo zápis | Co daný řádek znamená |
| --- | --- |
| `---` | Odděluje samostatné YAML dokumenty. Jeden soubor tak obsahuje více Kubernetes objektů. |
| `apiVersion` | Vybírá skupinu a verzi API, která umí daný objekt zpracovat. `v1` je základní API, `apps/v1` spravuje workloady, `autoscaling/v2` HPA. API Envoy a Gateway vyžadují nainstalované CRD. |
| `kind` | Určuje druh objektu, například `Service`, `Deployment` nebo `HTTPRoute`. |
| `metadata` | Obsahuje identifikaci a štítky samotného objektu. |
| `metadata.name` | Jméno objektu, na které mohou odkazovat další objekty. Service a Deployment mohou mít stejné jméno, protože jde o různé druhy objektů. |
| `metadata.namespace` | Umístí objekt do `erp-jsp-weighted`. Samotný objekt `Namespace` toto pole nemá. |
| `metadata.labels` | Štítky objektu. Label na Deploymentu není automaticky labelem jeho Podů. |
| `spec` | Požadovaný stav, který příslušný controller průběžně zajišťuje. Uvnitř `template` jde o požadovanou konfiguraci Podu. |
| `-` před položkou | Položka seznamu, například jeden kontejner, port, hostname nebo cíl routy. |
| Odsazení | Určuje, ke kterému objektu pole patří. Například port Service a port kontejneru jsou různá nastavení. |

### 1. Namespace: hranice pojmenování, ne síťový firewall

`kind: Namespace` vytvoří prostor `erp-jsp-weighted`. Krátké adresy jako
`postgres`, `backend` a `backend-canary` se z Podů překládají právě v tomto
namespace, takže nekolidují se stejně pojmenovanými Services v `erp-jsp`.

Label `istio-injection: enabled` žádá existující Istio o automatické přidání
sidecaru při vytváření Podů. Istio tím nenainstaluješ a existující Pody se
samotnou změnou labelu nepřestaví. Manifest neobsahuje `NetworkPolicy`;
oddělený namespace tedy sám o sobě nezakazuje komunikaci z jiných namespaces.

### 2. Services: stabilní adresy a propojení přes labely

Každá Service vyhledává cílové Pody pomocí `spec.selector.app`.
Není propojena s Deploymentem jen tím, že má stejné jméno.

| Service / DNS jméno | Hodnota `selector.app` | Port Service → port kontejneru |
| --- | --- | --- |
| `postgres` | `postgres` | `5432` → pojmenovaný port `postgres`, tedy `5432` |
| `backend` | `backend` | `8080` → `http`, tedy `8080` stabilního backendu |
| `backend-canary` | `backend-canary` | `8080` → `http`, tedy `8080` canary backendu |
| `frontend` | `frontend` | `8080` → `http`, tedy `8080` stabilního frontendu |
| `frontend-canary` | `frontend-canary` | `8080` → `http`, tedy `8080` canary frontendu |
| `remote-warehouse-mock` | `remote-warehouse-mock` | `8080` → `http`, tedy `8080` mock skladu |

| Pole | Význam |
| --- | --- |
| `spec.selector.app` | Musí odpovídat `spec.template.metadata.labels.app` cílového workloadu. |
| `spec.ports[].name` | Jméno portu Service, například `http`. |
| `spec.ports[].port` | Port, na který se připojuje klient Service nebo odkazuje HTTPRoute. |
| `spec.ports[].targetPort` | Číslo nebo jméno portu cílového Podu. Zde odkazuje na `containers[].ports[].name`. |
| `clusterIP: None` u PostgreSQL | Headless Service bez virtuální IP; DNS poskytuje adresy databázových Podů. |
| Chybějící `type` u ostatních Services | Výchozí `ClusterIP`: interní vstup, nikoli veřejný port na hostiteli. |

### 3. PostgreSQL: StatefulSet, přihlašovací údaje a disk

| Pole v databázové komponentě | Co dělá |
| --- | --- |
| `kind: StatefulSet`, `name: postgres` | Spravuje databázový Pod se stabilní identitou `postgres-0`. |
| `serviceName: postgres` | Připojuje síťovou identitu StatefulSetu k headless Service `postgres`. |
| `replicas: 1` | Spouští jedinou databázovou instanci. Nevytváří replikační cluster ani záložní databázi. |
| `selector.matchLabels.app: postgres` | Určuje, které Pody StatefulSet spravuje. |
| `template.metadata.labels.app: postgres` | Dává Podům label, který hledá StatefulSet i Service. |
| `containers[].name: postgres` | Jméno kontejneru uvnitř Podu, použitelné například při výběru logů. |
| `image: postgres:18` | Použije image PostgreSQL hlavní verze 18. |
| `ports[].name: postgres`, `containerPort: 5432` | Pojmenuje port databáze pro odkaz ze Service. Deklarace portu sama službu nespouští. |
| `env[].name: POSTGRES_DB`, `value: erp` | Při inicializaci prázdného datového adresáře vytvoří databázi `erp`. |
| `POSTGRES_USER=erp` | Inicializační databázový uživatel. |
| `POSTGRES_PASSWORD` + `valueFrom.secretKeyRef` | Načte heslo z existujícího Secretu `erp-postgres`, klíče `POSTGRES_PASSWORD`, ve stejném namespace. |
| `volumeMounts[].name: erp-postgres-data` | Připojí svazek vytvořený ze stejnojmenné PVC šablony. |
| `mountPath: /var/lib/postgresql` | Cesta persistentního úložiště v kontejneru PostgreSQL 18. |
| `volumeClaimTemplates[].metadata.name` | Pro `postgres-0` vznikne PVC `erp-postgres-data-postgres-0`. |
| `accessModes: [ReadWriteOnce]` | Přístup pro čtení a zápis z jednoho uzlu; neznamená automaticky jediný Pod. |
| `resources.requests.storage: 10Gi` | Požadovaná kapacita disku. Protože není `storageClassName`, musí vyhovět výchozí StorageClass nebo dostupný PV. |

Data přežijí nahrazení Podu díky PVC, nikoli díky samotnému kontejneru.
Persistentní disk ale není záloha. Změna inicializačních proměnných nebo
Secretu sama nezmění uživatele a heslo již existující databáze.

### 4. Deploymenty: repliky, obrazy a startovací závislosti

| Deployment | Počáteční repliky | Aplikační image | Závislost při startu / další komunikace |
| --- | --- | --- | --- |
| `backend` | 2 | `erp-backend:local` | Čeká na PostgreSQL; připojuje se k databázi `erp`. |
| `backend-canary` | 2 | `erp-backend:canary` | Čeká na tutéž PostgreSQL; sdílí data se stable backendem. |
| `frontend` | 1 | `erp-base-frontend:jsp-local` | Čeká na `backend:8080`; používá `BACKEND_URL=http://backend:8080`. |
| `frontend-canary` | 1 | `erp-base-frontend:jsp-canary` | Čeká na `backend-canary:8080`; používá `BACKEND_URL=http://backend-canary:8080`. |
| `remote-warehouse-mock` | 1 | `erp-remote-warehouse:local` | Simulace externího skladu, bez init kontejneru; veřejná routa na něj neodkazuje. |

| Pole Deploymentu / kontejneru | Co daný řádek dělá |
| --- | --- |
| `replicas` | Požadovaný počet Podů. U backendů ho následně mění HPA, frontendům ani mocku HPA nepřísluší. |
| `strategy.type: Recreate` | U stabilního frontendu odstraní starý Pod před spuštěním nového. Při jedné replice vznikne výpadek a zanikne session v paměti starého Tomcatu. |
| `selector.matchLabels` | Určuje Pody spravované Deploymentem. Musí odpovídat labelům šablony. |
| `template.metadata.labels` | Označuje nově vytvářené Pody pro Deployment a Service. |
| `template.spec.initContainers` | Seznam kontejnerů, které musí postupně úspěšně skončit před startem hlavní aplikace. |
| `initContainers[].name` | Identifikuje čekací kontejner `wait-for-postgres` nebo `wait-for-backend`. |
| `initContainers[].image` | Dodává kontrolní nástroj: `postgres:18` obsahuje `pg_isready`, frontendový image obsahuje Bash. |
| `command: [sh, -c, …]` / `[bash, -c, …]` | Nahradí výchozí start image vlastním shell skriptem. `-c` znamená vyhodnocení následujícího argumentu jako příkazu. |
| `- \|` v seznamu `command` | Předá následující odsazené řádky jako jediný řetězec se zachovanými konci řádků. |
| `containers` | Hlavní kontejnery běžící po úspěšném dokončení init kontejnerů. |
| `containers[].name` | Jméno kontejneru v Podu; může být stejné ve stable i canary Deploymentu. |
| `image` | Volí sestavení aplikace. Canary tag sám nezaručuje odlišný kód; rozhoduje obsah image. |
| `imagePullPolicy: Never` | Zakazuje stahování image. Pokud image na vybraném uzlu chybí, kontejner nenastartuje. |
| `ports[].name: http`, `containerPort: 8080` | Deklaruje pojmenovaný port pro Services a sondy; aplikace na něm musí skutečně naslouchat. |
| `env[].name` a `value` | Nastavují proměnnou procesu aplikace, například `BACKEND_URL` nebo `DB_USERNAME`. |
| `DB_URL=jdbc:postgresql://postgres:5432/erp` | JDBC spojení na Service `postgres`, port `5432`, databázi `erp`. |
| `DB_USERNAME=erp` | Databázový uživatel backendu. |
| `DB_PASSWORD` z `secretKeyRef` | Stejný Secret a klíč jako při inicializaci PostgreSQL. |
| `resources.requests.cpu: 250m` | Požadavek na 0,25 CPU jádra pro každý backend kontejner; zároveň základ pro jeho CPU utilization. |
| `resources.requests.memory: 512Mi` | Paměťový požadavek pro plánování jedné backend repliky. |
| `resources.limits.memory: 1Gi` | Paměťový limit kontejneru. Překročení může způsobit OOM ukončení. CPU limit zde nastaven není. |

Řádky čekacích skriptů mají tento význam:

| Řádek skriptu | Význam |
| --- | --- |
| `until pg_isready -h postgres -p 5432 -U erp -d erp; do` | Opakuje tělo smyčky, dokud server `postgres:5432` nezačne přijímat spojení. `-U` volí uživatele a `-d` databázi; test nezaručuje správné heslo ani připravené aplikační schéma. |
| `until (echo > /dev/tcp/backend/8080); do` | Bash se pokouší otevřít TCP spojení na stabilní backend. Canary skript používá místo něj `backend-canary`. Není to HTTP kontrola API. |
| `echo "Waiting …"` | Zapíše důvod čekání do logu init kontejneru. |
| `sleep 5` | Po neúspěšném testu čeká pět sekund, než podmínku zkusí znovu. |
| `done` | Ukončuje tělo smyčky; po úspěšném testu skript skončí a umožní start aplikace. |

Init kontejnery hlídají závislost jen při startu Podu. Pozdější výpadek databáze
nebo backendu tím není vyřešen. Čekací smyčky nemají vlastní maximální počet
pokusů; při nedostupné závislosti může Pod zůstat ve fázi inicializace.

### 5. Probes: start, připravenost a restart

| Pole | Význam |
| --- | --- |
| `startupProbe` | Dokud neuspěje, nespouští se readiness ani liveness. Po dosažení limitu selhání restartuje kontejner. |
| `readinessProbe` | Určuje připravenost pro příjem provozu. Selhání samo kontejner nerestartuje. |
| `livenessProbe` | Opakované selhání vede k restartu kontejneru. |
| `exec.command` | U PostgreSQL spustí v kontejneru lokální `pg_isready -U erp -d erp`. |
| `tcpSocket.port: http` | U backendů a frontendů testuje TCP spojení na pojmenovaný port `8080`, nikoli funkčnost přihlášení nebo API. |
| `httpGet.path: /api/v1/health`, `port: http` | U mock skladu provede HTTP zdravotní kontrolu. Odpověď se stavem 200–399 znamená úspěch. |
| `periodSeconds` | Interval mezi kontrolami. Startup a readiness mají 5 s, liveness 10 s. |
| `timeoutSeconds: 5` | U PostgreSQL dovolí jednomu pokusu trvat až 5 s. U ostatních sond není pole uvedené, platí výchozí 1 s. |
| `failureThreshold: 60` | Startup dovolí přibližně 5 minut startu: 60 pokusů s periodou 5 s. Nejde o přesnou časovou záruku. |
| `failureThreshold: 10` | PostgreSQL readiness/liveness reagují po deseti selháních v řadě. |
| Chybějící `failureThreshold` | U aplikačních readiness/liveness platí výchozí tři selhání. |

### 6. Dvě HPA: nezávislé škálování backendů

| Pole | Význam |
| --- | --- |
| `apiVersion: autoscaling/v2`, `kind: HorizontalPodAutoscaler` | Vytvoří autoscaler využívající metriky clusteru. |
| `scaleTargetRef.apiVersion: apps/v1`, `kind: Deployment` | Cílem je škálovatelný Deployment, nikoli Service. |
| `scaleTargetRef.name` | `backend` nebo `backend-canary`; každá větev má vlastní autoscaler. |
| `minReplicas: 2`, `maxReplicas: 5` | Povolený rozsah pro každou větev. Nulová routovací váha canary nevypíná její Pody. |
| `metrics[].type: Resource`, `resource.name: cpu` | Čte využití CPU z Metrics API. |
| `target.type: Utilization` | Vyhodnocuje CPU jako procento resource requests, ne jako procento výkonu uzlu. |
| `averageUtilization: 70` | Cílový průměr 70 %. Pro samotný backend s requestem `250m` odpovídá `175m`; není to tvrdý limit spotřeby. |

Jde o podovou metriku `Resource`, nikoli o `ContainerResource` pro jediný
kontejner. S přidaným Istio sidecarem se uplatní také jeho CPU spotřeba a
requests; chybějící requests mohou vyhodnocení metriky znemožnit. HPA
neškáluje databázi, frontend ani uzly clusteru.

### 7. EnvoyProxy a Gateway: infrastruktura a veřejný port

| Objekt a pole | Význam |
| --- | --- |
| `EnvoyProxy`, `name: erp-proxy` | Konfigurační objekt pro Envoy Gateway controller; sám není běžící proxy. |
| `provider.type: Kubernetes` | Proxy infrastrukturu má controller vytvořit v Kubernetes. |
| `provider.kubernetes.envoyService.name` | Generovaná proxy Service se bude jmenovat `erp-jsp-weighted-proxy`. Nejde o Service frontendové aplikace. |
| `envoyService.type: LoadBalancer` | Žádá externí přístup. Bez podpory LoadBalanceru může externí adresa zůstat nepřidělená. Toto pole neurčuje poměr stable/canary. |
| `Gateway`, `name: erp` | Vstup, na který se připojuje HTTPRoute. |
| `gatewayClassName: erp-envoy` | Odkaz na předem vytvořenou clusterovou GatewayClass pro Envoy controller. |
| `infrastructure.parametersRef.group: gateway.envoyproxy.io` | API skupina konfiguračního objektu proxy. |
| `parametersRef.kind: EnvoyProxy`, `name: erp-proxy` | Propojí Gateway s výše popsanou proxy konfigurací ve stejném namespace. |
| `listeners[].name: http` | Pojmenuje jeden vstupní listener. |
| `protocol: HTTP`, `port: 8089` | Poslouchá nesifrované HTTP na portu 8089; není zde TLS ani certifikát. |
| `allowedRoutes.namespaces.from: Same` | Povolí připojit routy jen z namespace této Gateway. Nejde o zákaz síťové komunikace mezi namespaces. |

### 8. HTTPRoute: hostitel, cesta, váhy a cookie

| Pole | Význam |
| --- | --- |
| `parentRefs[].name: erp` | Připojí routu ke Gateway `erp` ve stejném namespace. |
| `hostnames: [erp-jsp-weighted.local, localhost]` | Vybere požadavky podle HTTP hostitele. DNS ani soubor `hosts` se tím nenastaví; přístup přes samotnou IP nemusí odpovídat routě. |
| `rules[].matches[].path.type: PathPrefix` | Použije prefixové porovnání URL cesty po segmentech. |
| `path.value: /` | Odpovídá všem cestám pro povoleného hostitele, například `/login`. |
| `backendRefs[].name` | Jméno cílové **frontendové Service**, ne Deploymentu backendového API. |
| `backendRefs[].port: 8080` | Port cílové Service. Není to veřejný port Gateway `8089`. |
| `weight: 100` u `frontend` | Při výběru podle vah posílá všechen nový provoz na stabilní větev. |
| `weight: 0` u `frontend-canary` | Canary se podle vah nově nevybírá, ale její Deploymenty dál běží. |
| `sessionPersistence` | Zapíná afinitu klienta; vyžaduje podporu nainstalovaných Gateway API CRD a Envoy Gateway. |
| `sessionName: ERP_GATEWAY_SESSION` | Název gateway cookie, odlišný od Tomcat cookie `JSESSIONID`. |
| `type: Cookie` | Klient vrací cookie v dalších požadavcích. Envoy ji využívá k opětovnému výběru frontendové instance. |
| `cookieConfig.lifetimeType: Session` | Relace bez pevné trvalé expirace cookie, typicky do ukončení prohlížeče; funkce obnovení relace ji může zachovat. |

Váhy jsou relativní poměr: `90:10` a `9:1` vyjadřují stejný podíl.
S cookie afinitou jde především o výběr nových relací, ne o záruku, že přesně
10 % všech HTTP požadavků půjde na canary. Envoy
[session persistence](https://gateway.envoyproxy.io/docs/tasks/traffic/session-persistence/)
udržuje klienta u konkrétní instance (frontend Podu), nikoli pouze u Service.
Neukládá však aplikační session a nedokáže zachránit přihlášení z paměti
odstraněného nebo restartovaného Tomcatu. Změna vah na `100:0` navíc sama
nemusí okamžitě přesunout již přiřazené relace.

## Jak teče provoz

```text
prohlížeč -> LoadBalancer/Envoy -> frontend        -> backend
                             -> frontend-canary -> backend-canary
```

Frontendy používají `BACKEND_URL=http://backend:8080`, respektive
`BACKEND_URL=http://backend-canary:8080`. Jejich init kontejnery čekají na stejnou
službu, kterou následně volá aplikace. Service selektory oddělují stabilní a
canary Pody; každá služba rozděluje provoz jen mezi repliky své verze.
Krátké DNS názvy se vyhodnocují ve vlastním namespace.

Frontendová route používá session cookie `ERP_GATEWAY_SESSION`, aby se
uživatel držel konkrétního frontend Podu; Tomcat sessions nejsou sdílené.
API tokeny jsou uložené ve společné databázi. Poměr 90/10 vybírá frontend
pro nové klienty; kvůli session affinity a rozdílné aktivitě uživatelů nemusí
stejnému poměru odpovídat počet backendových API požadavků.

Při přechodu ze starší konfigurace nejprve aplikuj nový manifest a počkej na
rollout obou frontendů. Potom odstraň nepoužívané prostředky (samotný `apply`
prostředky odstraněné ze souboru nemaže):

```bash
kubectl -n erp-jsp delete httproute backend-api --ignore-not-found
kubectl -n erp-jsp delete service backend-router --ignore-not-found
```

Pro izolovanou variantu použij namespace `erp-jsp-weighted`.

## Změna váhy a rollback provozu

Například změna podílu nových klientů pro canary dvojici z 10 % na 25 %:

```bash
kubectl -n erp-jsp patch httproute frontend --type=json \
  -p='[{"op":"replace","path":"/spec/rules/0/backendRefs/0/weight","value":75},{"op":"replace","path":"/spec/rules/0/backendRefs/1/weight","value":25}]'
```

Změnu zapiš i do používaného manifestu, jinak další `apply` obnoví 90/10.
V izolovaném stacku použij namespace `erp-jsp-weighted`.
Pro zastavení výběru canary pro nové klienty nastav stabilní váhu na 100 a
canary na 0. Existující klienti se session cookie mohou zůstat na canary;
samotná změna vah není okamžité ukončení všech canary sessions.
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
s IP adresami stabilních a canary frontend Podů. Backendové požadavky už
neprocházejí Envoy a nejsou v jeho access logu. Nepřihlášený požadavek na
`/api/v1/auth/me` má vracet 401, zatímco veřejný katalog lze testovat bez hesla.
Pokud cluster LoadBalancer nepodporuje, pro lokální kontrolu použij
`kubectl -n envoy-gateway-system port-forward service/erp-jsp-proxy 8088:8088`.
