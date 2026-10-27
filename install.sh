#!/usr/bin/env bash
set -Eeuo pipefail

NAMESPACE="erp-jsp-weighted"
EXPECTED_KUBE_CONTEXT="${EXPECTED_KUBE_CONTEXT:-docker-desktop}"
EXPECTED_DOCKER_CONTEXT="${EXPECTED_DOCKER_CONTEXT:-desktop-linux}"
ENVOY_GATEWAY_VERSION="v1.9.2"
BUILD_CANARY_FROM_STABLE="${BUILD_CANARY_FROM_STABLE:-1}"

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

build_image() {
  local image="$1"
  local context="$2"

  if docker build -t "$image" "$context"; then
    return
  fi

  printf 'BuildKit failed for %s; retrying with Docker legacy builder.\n' "$image" >&2
  DOCKER_BUILDKIT=0 docker build -t "$image" "$context"
}

for command in docker kubectl helm grep; do
  command -v "$command" >/dev/null 2>&1 || fail "Required command not found: $command"
done

for file in \
  deploy/envoy-gateway.yml \
  deploy/metrics-server-docker-desktop-patch.yml \
  erp-backend/Dockerfile \
  erp-base-frontend/Dockerfile \
  kubernetes-JSP-weighted-canary.yml \
  erp-remote-warehouse/Dockerfile
do
  [[ -f "$file" ]] || fail "Run this script from a complete repository checkout; missing $file"
done

docker info >/dev/null 2>&1 || fail "Docker Engine is not available."
DOCKER_CONTEXT="$(docker context show)"
[[ "$DOCKER_CONTEXT" == "$EXPECTED_DOCKER_CONTEXT" ]] ||
  fail "Docker context is '$DOCKER_CONTEXT'; expected '$EXPECTED_DOCKER_CONTEXT'. Set EXPECTED_DOCKER_CONTEXT to override."

KUBE_CONTEXT="$(kubectl config current-context)"
[[ "$KUBE_CONTEXT" == "$EXPECTED_KUBE_CONTEXT" ]] ||
  fail "Kubernetes context is '$KUBE_CONTEXT'; expected '$EXPECTED_KUBE_CONTEXT'. Set EXPECTED_KUBE_CONTEXT to override."

printf 'Docker context: %s\nKubernetes context: %s\n' "$DOCKER_CONTEXT" "$KUBE_CONTEXT"
kubectl wait --for=condition=Ready nodes --all --timeout=180s ||
  fail "Kubernetes nodes are not Ready."

RESTART_EXISTING_DEPLOYMENTS=0
if kubectl -n "$NAMESPACE" get deployment backend >/dev/null 2>&1; then
  RESTART_EXISTING_DEPLOYMENTS=1
fi

DEFAULT_STORAGE_CLASS="$(
  kubectl get storageclass \
    -o jsonpath='{range .items[*]}{.metadata.annotations.storageclass\.kubernetes\.io/is-default-class}{"\n"}{end}'
)"
grep -qx 'true' <<<"$DEFAULT_STORAGE_CLASS" ||
  fail "No default StorageClass found. Configure one before creating the PostgreSQL PVC."

printf '\nInstalling or updating Envoy Gateway %s...\n' "$ENVOY_GATEWAY_VERSION"
helm upgrade --install eg oci://docker.io/envoyproxy/gateway-helm \
  --version "$ENVOY_GATEWAY_VERSION" \
  --namespace envoy-gateway-system \
  --create-namespace \
  --wait --timeout 5m

kubectl wait --for=condition=Established --timeout=120s \
  crd/gatewayclasses.gateway.networking.k8s.io \
  crd/gateways.gateway.networking.k8s.io \
  crd/httproutes.gateway.networking.k8s.io \
  crd/envoyproxies.gateway.envoyproxy.io
kubectl -n envoy-gateway-system rollout status \
  deployment/envoy-gateway --timeout=300s
kubectl apply -f deploy/envoy-gateway.yml
kubectl wait --for=condition=Accepted \
  gatewayclass/erp-envoy --timeout=120s

if ! kubectl top nodes >/dev/null 2>&1; then
  printf '\nInstalling Metrics Server for backend HPA...\n'
  kubectl apply -f \
    https://github.com/kubernetes-sigs/metrics-server/releases/download/v0.9.0/components.yaml

  if [[ "$KUBE_CONTEXT" == "docker-desktop" ]]; then
    printf '%s\n' \
      'Applying the Docker Desktop development TLS patch (not suitable for production).'
    kubectl -n kube-system patch deployment metrics-server \
      --type=strategic \
      --patch-file deploy/metrics-server-docker-desktop-patch.yml
  fi

  kubectl -n kube-system rollout status \
    deployment/metrics-server --timeout=180s
  kubectl wait --for=condition=Available \
    apiservice/v1beta1.metrics.k8s.io --timeout=120s
  kubectl top nodes
fi


if docker image inspect postgres:18 >/dev/null 2>&1; then
  printf 'Using existing Docker image postgres:18.\n'
else
  printf 'PostgreSQL image postgres:18 is missing; downloading it...\n'
  docker pull postgres:18
fi

if [[ "$BUILD_CANARY_FROM_STABLE" == "1" ]]; then
  printf '%s\n' \
    'Creating initial canary tags from the stable images (same application code).'
  docker tag erp-backend:local erp-backend:canary
  docker tag erp-base-frontend:jsp-local erp-base-frontend:jsp-canary
elif [[ "$BUILD_CANARY_FROM_STABLE" != "0" ]]; then
  fail "BUILD_CANARY_FROM_STABLE must be 1 or 0."
fi

docker image inspect \
  erp-backend:local erp-backend:canary \
  erp-base-frontend:jsp-local erp-base-frontend:jsp-canary \
  erp-remote-warehouse:local postgres:18 >/dev/null ||
  fail "One or more required Docker images are missing."

printf '\nPreparing namespace %s...\n' "$NAMESPACE"
kubectl create namespace "$NAMESPACE" \
  --dry-run=client -o yaml | kubectl apply -f -

if kubectl -n "$NAMESPACE" get secret erp-postgres >/dev/null 2>&1; then
  printf 'Keeping existing Secret %s/erp-postgres.\n' "$NAMESPACE"
else
  POSTGRES_PASSWORD=""
  POSTGRES_PASSWORD_CONFIRM=""
  read -r -s -p "Enter a new PostgreSQL password: " POSTGRES_PASSWORD
  printf '\n'
  read -r -s -p "Confirm PostgreSQL password: " POSTGRES_PASSWORD_CONFIRM
  printf '\n'

  [[ -n "$POSTGRES_PASSWORD" ]] || fail "The PostgreSQL password cannot be empty."
  [[ "$POSTGRES_PASSWORD" == "$POSTGRES_PASSWORD_CONFIRM" ]] ||
    fail "The PostgreSQL passwords do not match."

  printf '%s' "$POSTGRES_PASSWORD" |
    kubectl -n "$NAMESPACE" create secret generic erp-postgres \
      --from-file=POSTGRES_PASSWORD=/dev/stdin
  unset POSTGRES_PASSWORD POSTGRES_PASSWORD_CONFIRM
fi

printf '\nValidating and applying the canary deployment manifest...\n'
kubectl apply --dry-run=server -f kubernetes-JSP-weighted-canary.yml
if [[ "$RESTART_EXISTING_DEPLOYMENTS" == "1" ]]; then
  printf '%s\n' \
    'Restarting the stable backend first so database migrations complete before frontend rollout.'
  kubectl -n "$NAMESPACE" rollout restart deployment/backend
  kubectl -n "$NAMESPACE" rollout status deployment/backend --timeout=300s
fi

kubectl apply -f kubernetes-JSP-weighted-canary.yml

if [[ "$RESTART_EXISTING_DEPLOYMENTS" == "1" ]]; then
  printf '%s\n' \
    'Restarting remaining deployments so they load the newly built local images.'
  for deployment in backend-canary frontend frontend-canary remote-warehouse-mock; do
    echo "!!!!!!! Restarting deployment $deployment"
    kubectl -n "$NAMESPACE" rollout restart "deployment/$deployment"
  done
fi

printf '\nWaiting for database and application rollouts...\n'
kubectl -n "$NAMESPACE" rollout status statefulset/postgres --timeout=300s
kubectl -n "$NAMESPACE" rollout status deployment/backend --timeout=300s
kubectl -n "$NAMESPACE" rollout status deployment/backend-canary --timeout=300s
kubectl -n "$NAMESPACE" rollout status deployment/remote-warehouse-mock --timeout=300s
kubectl -n "$NAMESPACE" rollout status deployment/frontend --timeout=300s
kubectl -n "$NAMESPACE" rollout status deployment/frontend-canary --timeout=300s
kubectl -n "$NAMESPACE" wait --for=condition=Programmed \
  gateway/erp --timeout=180s

printf '\nDeployment status:\n'
kubectl -n "$NAMESPACE" get pods,pvc,hpa
kubectl -n "$NAMESPACE" describe httproute frontend

cat <<'EOF'

Installation completed. The manifest starts with traffic weights stable=100,
canary=0. Both versions are deployed, but new traffic is initially routed to
stable. The initial canary tags use the same source as stable; build candidate
images separately before increasing the canary weight.

To access the application, run this in a separate terminal:
  kubectl -n envoy-gateway-system port-forward \
    service/erp-jsp-weighted-proxy 8089:8089

Then open:
  http://localhost:8089/login

Metrics Server's Docker Desktop TLS workaround disables kubelet certificate
verification and is for local development only, not production.
EOF







