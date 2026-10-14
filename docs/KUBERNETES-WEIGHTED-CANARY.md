# Vážený canary provoz pro JSP frontend v Kubernetes

Obě varianty nastavují canary váhu na 10 %. NGINX Ingress Controller vybírá
canary náhodně podle této váhy; Kubernetes `Service` typu `LoadBalancer`
samotné váhy nepodporuje.
LoadBalancer je veřejný vstup do Ingress Controlleru, zatímco Ingress pravidla
vybírají mezi stabilní a canary službou.

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
- Obraz `erp-base-frontend:jsp-canary` musí být dostupný v clusteru. Protože
  manifest používá `imagePullPolicy: Never`, sestav ho z kódu canary verze a
  použij tag `erp-base-frontend:jsp-canary`, případně uprav image v manifestu.
  Pokud ho sestavíš ze stejného zdrojového kódu jako stabilní image, obě verze
  budou totožné.
- NGINX Ingress Controller musí přiřadit adresu své LoadBalancer službě. Pokud
  `EXTERNAL-IP` zůstane `<pending>`, Docker Desktop cluster nemá LoadBalancer
  implementaci nakonfigurovanou.

## Varianta 1: stávající ERP, sdílený backend a databáze

[kubernetes-JSP-weighted-shared.yml](../kubernetes-JSP-weighted-shared.yml) je
doplněk k [kubernetes-JSP.yml](../kubernetes-JSP.yml). Stávající frontend
slouží jako stabilní verze; canary frontend používá tentýž backend a databázi.
Nejdřív nasaď základní ERP stack a vytvoř jeho Secret podle README, potom:

```bash
kubectl apply -f kubernetes-JSP-weighted-shared.yml
kubectl -n erp-jsp rollout status deployment/frontend-canary --timeout=300s
kubectl -n erp-jsp get ingress,services,endpoints
```

Vstupní hostname je `erp-jsp.local`.

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
kubectl -n erp-jsp-weighted rollout status deployment/frontend --timeout=300s
kubectl -n erp-jsp-weighted rollout status deployment/frontend-canary --timeout=300s
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

Zastavení canary provozu: nastav váhu na `"0"` nebo odstraň canary Ingress.
Canary Deployment a Service můžeš ponechat pro další testování.
