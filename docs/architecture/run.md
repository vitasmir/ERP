Soubor `kubernetes-JSP.yml` nasadíš pomocí `kubectl apply`. Nejdřív ale musíš připravit image a databázový Secret.

Následující postup používá tvůj cluster **`erp-k8s` s Kubernetes v1.37.0**. Namespace aplikace se bude jmenovat `erp-jsp` — to není totéž jako název clusteru.

### 1. Vyber cluster

Docker Desktop musí běžet.

```bash
cd /home/jan/Desktop/ERP
export PATH="$HOME/.local/bin:$PATH"

kubectl config use-context kind-erp-k8s
kubectl get nodes
kubectl get storageclass
```

Uzel musí být `Ready` a musí existovat výchozí StorageClass pro databázový disk.

### 2. Sestav image a nahraj je do kind

Tagy musí odpovídat manifestu:

```bash
docker build -t erp-backend:local ./erp-backend
docker build -t erp-base-frontend:jsp-local ./erp-base-frontend
docker build -t erp-remote-warehouse:local ./erp-remote-warehouse

kind load docker-image \
  erp-backend:local \
  erp-base-frontend:jsp-local \
  erp-remote-warehouse:local \
  --name erp-k8s
```

Manifest používá `imagePullPolicy: Never`, takže bez načtení image se aplikace nespustí.

### 3. Vytvoř namespace a databázový Secret

```bash
kubectl create namespace erp-jsp --dry-run=client -o yaml | kubectl apply -f -

read -r -s -p "Heslo PostgreSQL: " POSTGRES_PASSWORD
echo
kubectl -n erp-jsp create secret generic erp-postgres \
  --from-literal=POSTGRES_PASSWORD="$POSTGRES_PASSWORD"
unset POSTGRES_PASSWORD
```

**Secret vytvářej pouze při prvním nasazení.** Pokud už existuje, zachovej jej. Změna Secretu sama nezmění heslo v již vytvořené databázi.

### 4. Nasaď manifest a počkej na připravenost

```bash
kubectl apply --dry-run=client --validate=strict -f kubernetes-JSP.yml
kubectl apply -f kubernetes-JSP.yml

kubectl -n erp-jsp rollout status statefulset/postgres --timeout=300s
kubectl -n erp-jsp rollout status deployment/backend --timeout=300s
kubectl -n erp-jsp rollout status deployment/frontend --timeout=300s
kubectl -n erp-jsp rollout status deployment/remote-warehouse-mock --timeout=300s

kubectl -n erp-jsp get pods,services,pvc
```

PostgreSQL se spustí první, backend počká na databázi a frontend na backend.

### 5. Otevři aplikaci

```bash
kubectl -n erp-jsp port-forward service/frontend 4201:8080
```

Terminál ponech spuštěný a otevři **http://localhost:4201/login**. Pokud port 4201 používá Docker Compose, nejprve jej uvolni, nebo použij například `4202:8080`.

### Když se něco nespustí

```bash
kubectl -n erp-jsp get events --sort-by=.metadata.creationTimestamp
kubectl -n erp-jsp logs deployment/backend -c wait-for-postgres
kubectl -n erp-jsp logs deployment/backend -c backend --tail=100
kubectl -n erp-jsp logs statefulset/postgres --tail=100
```

**Pozor:** jde o novou databázi v tomto clusteru. Data z Compose ani z druhého clusteru se automaticky nepřenášejí.