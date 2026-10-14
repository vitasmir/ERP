# Vážený canary provoz pro JSP frontend v Kubernetes

Obě varianty nastavují canary váhu na 10 % zvlášť pro frontend i backend.
NGINX Ingress Controller vybírá canary náhodně podle této váhy; Kubernetes
`Service` typu `LoadBalancer` samotné váhy nepodporuje. LoadBalancer je veřejný
vstup do Ingress Controlleru, zatímco Ingress pravidla vybírají mezi stabilní
a canary službou.

## Požadavky

- V clusteru musí běžet NGINX Ingress Controller a `IngressClass` pojmenovaná
  `nginx`. V aktuálním Docker Desktop clusteru zatím nejsou nainstalované.
- Ingress Controller musí být vystaven přes `Service` typu `LoadBalancer`.
  S nainstalovaným Helm CLI ho můžeš nasadit oficiálním chartem:

  ```bash
  helm upgrade --install ingress-nginx ingress-nginx \
    --repo https://kubernetes.github.io/ingress-nginx \
    --namespace ingress-nginx --create-namespace \
    --set controller.service.type=LoadBalancer
  ```

  Podrobnosti a varianty instalace jsou v
  [dokumentaci ingress-nginx](https://kubernetes.github.io/ingress-nginx/deploy/).
- HPA potřebuje Metrics API poskytované `metrics-server`. V aktuálním clusteru
  Docker Desktop toto API není dostupné; bez něj se HPA vytvoří, ale nebude
  schopné měřit CPU ani škálovat. Ověř dostupnost příkazem `kubectl top nodes`
  a nainstaluj kompatibilní `metrics-server` pro svůj cluster.
- Obraz `erp-base-frontend:jsp-canary` musí být dostupný v clusteru. Protože
  manifest používá `imagePullPolicy: Never`, sestav ho z kódu canary verze a
  použij tag `erp-base-frontend:jsp-canary`, případně uprav image v manifestu.
  Pokud ho sestavíš ze stejného zdrojového kódu jako stabilní image, obě verze
  budou totožné.
- Backend canary používá obraz `erp-backend:canary`. Sestav ho z kódu
  kandidátní verze a ujisti se, že je dostupný v Docker Desktopu. Stejný zdrojový
  kód jako pro stabilní image `erp-backend:local` by vytvořil totožnou verzi.
- Oba backend Deploymenty mají HPA s rozsahem 2–5 replik a cílovým využitím CPU
  70 %. CPU request `250m` je základem pro výpočet tohoto procenta.
- NGINX Ingress Controller musí přiřadit adresu své LoadBalancer službě. Pokud
  `EXTERNAL-IP` zůstane `<pending>`, Docker Desktop cluster nemá LoadBalancer
  implementaci nakonfigurovanou.

Frontendy posílají API požadavky na `backend-router`. Tato interní služba
přesměruje požadavky přes Ingress Controller; Ingressy pro host
`backend-router` a cestu `/api` pak váženě vybírají stabilní nebo canary
backend. Frontendová váha a backendová váha jsou samostatná nastavení.

## Varianta 1: stávající ERP, sdílený backend a databáze

[kubernetes-JSP-weighted-shared.yml](../kubernetes-JSP-weighted-shared.yml) je
doplněk k [kubernetes-JSP.yml](../kubernetes-JSP.yml). Stávající frontend a
backend slouží jako stabilní verze; canary frontend i backend používají
stávající databázi. Nejdřív nasaď základní ERP stack a vytvoř jeho Secret podle
README, potom:

```bash
kubectl apply -f kubernetes-JSP-weighted-shared.yml
kubectl -n erp-jsp rollout status deployment/frontend-canary --timeout=300s
kubectl -n erp-jsp rollout status deployment/backend-canary --timeout=300s
kubectl -n erp-jsp get hpa
kubectl -n erp-jsp get ingress,services,endpoints
```

Vstupní hostname je `erp-jsp.local`. Po aplikaci overlay znovu neaplikuj
samotný základní manifest bez následného opakování overlay: základní manifest
nastavuje frontend přímo na Service `backend`, zatímco overlay používá váženou
backendovou cestu přes Ingress.

## Varianta 2: samostatný ERP stack

[kubernetes-JSP-weighted-isolated.yml](../kubernetes-JSP-weighted-isolated.yml)
je úplný stack v namespace `erp-jsp-weighted`, s vlastní databází a PVC.
Před aplikováním vytvoř namespace a jeho vlastní Secret:

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
kubectl -n erp-jsp-weighted get hpa
kubectl -n erp-jsp-weighted get ingress,services,endpoints
```

Vstupní hostname je `erp-jsp-weighted.local`. Tento stack potřebuje vlastní
Secret; Kubernetes Secrets se mezi namespaces nesdílejí.

## Přístup a nastavení váhy

Zjisti adresu LoadBalancer služby Ingress Controlleru:

```bash
kubectl -n ingress-nginx get service ingress-nginx-controller -o wide
```

Nastav příslušný hostname na tuto adresu v DNS nebo v souboru `hosts`. Potom
otevři `/login` na `http://erp-jsp.local` nebo
`http://erp-jsp-weighted.local`. Canary Ingress je nastaven na
`nginx.ingress.kubernetes.io/canary-weight: "10"`; uprav tuto anotaci pro změnu
váhy.

Rozdělení nemusí při malém počtu požadavků přesně odpovídat 10 %. Stabilní
Ingress nastavuje cookie affinity a canary Ingress ji zachovává, protože JSP
frontend uchovává session v paměti Tomcatu. Cookie affinity udrží klienta na
zvolené verzi; proto se 10 % nemusí rovnat přesně 10 % všech dalších HTTP
požadavků v delším provozu.

HPA škáluje stabilní i canary backend samostatně podle CPU. Sleduj jejich
aktuální stav příkazem `kubectl get hpa -n erp-jsp` nebo pro izolovanou variantu
`kubectl get hpa -n erp-jsp-weighted`. Canary backend sdílí databázi se stabilní
verzí, proto musí být databázové migrace zpětně kompatibilní.

Zastavení canary provozu: nastav váhu na `"0"` nebo odstraň canary Ingress.
Canary Deployment a Service můžeš ponechat pro další testování.
