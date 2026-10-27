## 1. První nasazení

Skript `install.sh` provede sestavení obrazů, přípravu Envoy Gateway, vytvoření namespace a Secretu a nasazení Kubernetes manifestu `kubernetes-JSP-weighted-canary.yml`.

Nejdřív spusťte Kubernetes v Docker Desktopu jako **Kubeadm** cluster a ověřte výchozí úložiště:

```bash
kubectl config use-context docker-desktop
kubectl get nodes
kubectl get storageclass
```

Skript očekává Docker context `desktop-linux`, Kubernetes context `docker-desktop` a výchozí StorageClass. Pak z kořene repozitáře spusťte:

```bash
cd /home/jan/Desktop/ERP
git status --short
docker context use desktop-linux
./install.sh
```

Při prvním spuštění vás skript vyzve k zadání a potvrzení hesla pro PostgreSQL. Zadejte silné heslo; skript ho uloží do Kubernetes Secretu a při opakovaném spuštění existující Secret zachová.

Ověřte stav podů a otevřete aplikaci:

```bash
kubectl -n erp-jsp-weighted get pods,pvc,hpa

kubectl -n envoy-gateway-system port-forward \
  service/erp-jsp-weighted-proxy 8089:8089
```

Pak přejděte na [http://localhost:8089/login](http://localhost:8089/login). Podrobnější instalační podmínky jsou v `instalačním manuálu`.

## 2. Canary deployment pomocí `install.sh`

Důležitý detail: první spuštění už vytvoří stable i canary deploymenty, ale **provoz nastaví na stable 100 %, canary 0 %**. Počáteční canary tagy jsou kopií stable obrazů, takže samy o sobě nepředstavují novou verzi.

Pro skutečný canary připravte obrazy kandidátní verze. Následující příklad předpokládá, že máte její zdrojový kód v samostatném checkoutu; upravte cestu:

```bash
CANDIDATE_DIR=/cesta/kandidatu

docker build -t erp-backend:canary "$CANDIDATE_DIR/erp-backend"
docker build -t erp-base-frontend:jsp-canary "$CANDIDATE_DIR/erp-base-frontend"
```

Pokud chcete znovu použít instalační skript, spusťte ho s `BUILD_CANARY_FROM_STABLE=0`. Tím zabráníte tomu, aby výchozí chování skriptu přepsalo vaše canary tagy kopiemi stable obrazů:

```bash
BUILD_CANARY_FROM_STABLE=0 ./install.sh
```

Tento skript ale stále sestaví stable obrazy z právě otevřeného checkoutu a znovu aplikuje manifest s výchozím poměrem 100/0. Proto ho spouštějte z checkoutu se stable verzí a ověřte, že kandidátní obrazy zůstaly dostupné v Docker Engine používaném clusterem.

Po dokončení rolloutů pošlete například 10 % provozu na canary:

```bash
kubectl -n erp-jsp-weighted patch httproute frontend --type=json \
  -p='[{"op":"replace","path":"/spec/rules/0/backendRefs/0/weight","value":90},{"op":"replace","path":"/spec/rules/0/backendRefs/1/weight","value":10}]'
```

V tomto manifestu je první cíl stable frontend a druhý canary frontend; canary frontend volá canary backend. Váhy se vztahují k **novým relacím**, ne k přesnému počtu požadavků. Kvůli cookie persistenci mohou již přiřazené relace zůstat na původní verzi.

Před změnou vah zkontrolujte připravenost deploymentů. Při problému vraťte poměr na 100/0 stejným příkazem, jen použijte hodnoty `100` a `0`. Nezapomeňte také změnit váhy v YAML manifestu, pokud nechcete, aby je příští spuštění skriptu vrátilo na výchozích 100/0.

**Shrnutí omezení:** `install.sh` nemá samostatný přepínač pro nasazení nové canary verze. Je to instalační a opakovaně spustitelný deploy skript; pro zachování kandidátních obrazů je podstatné `BUILD_CANARY_FROM_STABLE=0`. Obě verze sdílejí jednu databázi, takže změny databázového schématu musí být kompatibilní se stable verzí. Navíc skript může na Docker Desktopu nastavit TLS workaround pro Metrics Server — podle jeho výstupu je určen jen pro lokální vývoj, ne pro produkci.




# JAK SPUSTIT install.sh POPRVE A JAK PRI DALSIM SPUSTENI?
Spouštějte skript z kořene repozitáře. Potřebujete běžící Docker Desktop Kubernetes cluster, `docker`, `kubectl` a Helm. Skript standardně očekává Docker context `desktop-linux`, Kubernetes context `docker-desktop` a výchozí StorageClass.

```bash
cd /home/jan/Desktop/ERP
docker context use desktop-linux
kubectl config use-context docker-desktop
kubectl get nodes
kubectl get storageclass
```

## První spuštění

```bash
./install.sh
```

Skript sestaví obrazy, nastaví Envoy Gateway a vytvoří namespace i databázový Secret. Při prvním běhu vás požádá o nové heslo PostgreSQL. Po dokončení ověřte pody:

```bash
kubectl -n erp-jsp-weighted get pods,pvc,hpa
```

Aplikaci zpřístupněte v dalším terminálu:

```bash
kubectl -n envoy-gateway-system port-forward \
  service/erp-jsp-weighted-proxy 8089:8089
```

Otevřete [http://localhost:8089/login](http://localhost:8089/login).

## Každé další spuštění

Pro běžné opakované nasazení stejné verze spusťte znovu:

```bash
./install.sh
```

Skript zachová existující databázový Secret a nyní přeskočí stahování `postgres:18`, pokud už je image lokálně dostupný. Pokud už existuje backend deployment, **restartuje aplikační deploymenty** (`backend`, `backend-canary`, oba frontendy a mock skladu), aby načetly nově sestavené obrazy. PostgreSQL deployment přímo nerestartuje, pokud se nezměnila jeho konfigurace.

Pozor: výchozí `BUILD_CANARY_FROM_STABLE=1` nastaví canary obrazy jako kopie právě sestavených stable obrazů. Pokud chcete zachovat vlastní, předem připravené canary obrazy, použijte:

```bash
BUILD_CANARY_FROM_STABLE=0 ./install.sh
```

V tomto režimu musí canary obrazy existovat pod tagy `erp-backend:canary` a `erp-base-frontend:jsp-canary`, jinak skript skončí chybou.

**Opakované spuštění není bezvýpadkový rollout celé aplikace:** skript aplikační deploymenty restartuje a u jediné repliky frontendu může dojít ke krátké nedostupnosti. Změny poměru canary provozu také udržujte v manifestu `kubernetes-JSP-weighted-canary.yml`, protože skript ho při každém spuštění znovu aplikuje.











# KANARY DEPLOYMENT
kdyz mam sestavene image pro stable(lokal) i pro cannary, s jakym parametrem mam spustit install.sh?

Spusť ho s **`BUILD_CANARY_FROM_STABLE=0`**:

```bash
BUILD_CANARY_FROM_STABLE=0 ./install.sh
```

Tím použiješ již sestavené stable i canary image. Hodnota `1` by přepsala tvoje canary tagy stabilními image.

Po nasazení budou váhy **stable 100 % / canary 0 %**; podíl provozu pro canary pak nastavíš zvlášť.
















# KANARY
Pro další nasazení **nové canary verze použij `BUILD_CANARY_FROM_STABLE=0`**.

- **`0`** — ponechá připravené canary image beze změny.
- **`1`** — přepíše canary tagy stabilními image (`local` → `canary`). Hodí se pro první test směrování se stejnou verzí aplikace, ne pro nasazení nové verze.

Postup z kořene repozitáře, po úpravě kandidátního kódu:

```bash
./build-images-canary.sh
BUILD_CANARY_FROM_STABLE=0 ./install.sh
```

Předtím musí existovat stabilní image s tagy `local` / `jsp-local` a mock skladu. **Nepouštěj local build nad kandidátním kódem**, pokud chceš zachovat původní stabilní verzi.

Pozor: `install.sh` restartuje i stabilní Deploymenty a znovu aplikuje manifest s vahami **stable 100 / canary 0**. Není to tedy pouze aktualizace canary; požadovanou canary váhu nastav až po ověření nasazení.








# co se stane kdyz spustim instalaci :
BUILD_CANARY_FROM_STABLE=1 ./install.sh
Při spuštění:

```bash
BUILD_CANARY_FROM_STABLE=1 ./install.sh
```

`install.sh` **přepíše canary tagy tak, aby ukazovaly na stejné image jako stable**:

```bash
docker tag erp-backend:local erp-backend:canary
docker tag erp-base-frontend:jsp-local erp-base-frontend:jsp-canary
```

Výsledek:
- **Stable image se nezmění.**
- Předem sestavená nová canary verze už nebude dostupná pod těmito canary tagy.
- Instalace nasadí do obou větví **stejnou verzi aplikace**, jen jako oddělené Deploymenty.
- Při opakované instalaci skript restartuje stable i canary Deploymenty.
- Manifest nastaví provoz na **stable 100 % / canary 0 %**.

**Pokud chceš nasadit vlastní, již sestavenou canary verzi, použij `BUILD_CANARY_FROM_STABLE=0`.**











# NEXT BUILD
* mam pro dalsi cannary deployemrnt pouzit BUILD_CANARY_FROM_STABLE=0
nebo
BUILD_CANARY_FROM_STABLE=1
?
Pro další nasazení **nové canary verze použij `BUILD_CANARY_FROM_STABLE=0`**.

- **`0`** — ponechá připravené canary image beze změny.
- **`1`** — přepíše canary tagy stabilními image (`local` → `canary`). Hodí se pro první test směrování se stejnou verzí aplikace, ne pro nasazení nové verze.

Postup z kořene repozitáře, po úpravě kandidátního kódu:

```bash
./build-images-canary.sh
BUILD_CANARY_FROM_STABLE=0 ./install.sh
```

Předtím musí existovat stabilní image s tagy `local` / `jsp-local` a mock skladu. **Nepouštěj local build nad kandidátním kódem**, pokud chceš zachovat původní stabilní verzi.

Pozor: `install.sh` restartuje i stabilní Deploymenty a znovu aplikuje manifest s vahami **stable 100 / canary 0**. Není to tedy pouze aktualizace canary; požadovanou canary váhu nastav až po ověření nasazení.








# kdyz nenastavim promennouBUILD _CANARY_FROM_STABLE, jak se pak interpretuje jako 0 nebo jako 1?


Když proměnnou nenastavíš, **bere se jako `1`**. V `install.sh` je výchozí hodnota nastavena takto:

```bash
BUILD_CANARY_FROM_STABLE="${BUILD_CANARY_FROM_STABLE:-1}"
```

Takže obyčejné `./install.sh` přepíše canary tagy stable imagi. Pokud chceš zachovat vlastní canary image, spusť:

```bash
BUILD_CANARY_FROM_STABLE=0 ./install.sh
```








################################
# co se stane kdyz spustim instalaci :
# BUILD_CANARY_FROM_STABLE=1 ./install.sh


#Při spuštění:

```bash
BUILD_CANARY_FROM_STABLE=1 ./install.sh
```

`install.sh` **přepíše canary tagy tak, aby ukazovaly na stejné image jako stable**:

```bash
docker tag erp-backend:local erp-backend:canary
docker tag erp-base-frontend:jsp-local erp-base-frontend:jsp-canary
```

Výsledek:
- **Stable image se nezmění.**
- Předem sestavená nová canary verze už nebude dostupná pod těmito canary tagy.
- Instalace nasadí do obou větví **stejnou verzi aplikace**, jen jako oddělené Deploymenty.
- Při opakované instalaci skript restartuje stable i canary Deploymenty.
- Manifest nastaví provoz na **stable 100 % / canary 0 %**.

**Pokud chceš nasadit vlastní, již sestavenou canary verzi, použij `BUILD_CANARY_FROM_STABLE=0`.**