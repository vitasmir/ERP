# Architektura `erp-base-php` (Symfony)

PHP projekt je server-rendered frontend postavený na Symfony 7.4, PHP 8.4
a Twig. Jeho aplikační role je BFF: zpracovává browser routy, session,
formuláře a HTML; obchodní operace a trvalá data deleguje do Spring backendu.

## Komponentní diagram

```mermaid
flowchart TB
    Browser["Prohlížeč<br/>HTML formuláře + assets"]
    Apache["Apache<br/>document root: public/"]
    PHP["PHP 8.4 + Symfony 7.4"]

    subgraph Symfony["Symfony aplikační vrstvy"]
        FrontController["public/index.php<br/>Symfony Runtime"]
        Router["Attribute routes<br/>config/routes.yaml"]
        Subscriber["FrontendRequestSubscriber<br/>session, auth, Origin,<br/>module checks, headers"]
        Controllers["Controllers<br/>Auth / Admin / Shop /<br/>Operations / Independent"]
        Support["ModuleControllerSupport<br/>field validation,<br/>feedback, HTTP helpers"]
        ApiClient["BackendApiClient<br/>Symfony HttpClient"]
        Session["Symfony session<br/>server-side token/profile"]
        Twig["Twig templates<br/>base + module views"]
        Assets["public/assets<br/>CSS / JavaScript"]
    end

    Backend["erp-backend<br/>Spring REST /api/v1"]
    DB[("PostgreSQL")]

    Browser -->|"HTTP request/form"| Apache
    Apache --> PHP --> FrontController
    FrontController --> Router
    Router --> Subscriber
    Subscriber --> Session
    Subscriber --> Controllers
    Controllers --> Support
    Controllers --> ApiClient
    ApiClient -->|"HTTP JSON/Bearer"| Backend
    Controllers -->|"view model"| Twig
    Twig -->|"HTML response"| Browser
    Assets -.-> Browser
    Backend --> DB
```

## Request lifecycle

1. Apache směruje požadavky do `public/`; `public/index.php` zavádí Symfony
   Runtime. Attribute route definice v `src/Controller` mapují URL a metody.
2. `FrontendRequestSubscriber` na hlavním requestu ověří session lifetime,
   origin mutací a login pro privátní cesty. GET vstup do modulu může ověřit
   oprávnění read požadavkem na odpovídající backend endpoint.
3. Controller načte Twig context nebo validuje formulářové hodnoty
   (`ModuleControllerSupport`), zavolá `BackendApiClient` a vrátí HTML,
   redirect, PDF/binary nebo chybu.
4. `BackendApiClient` povoluje pouze `/api/v1/*`, používá timeouty, připojí
   Bearer token ze Symfony session a volá `BACKEND_URL`.
5. Response subscriber nastaví no-cache a bezpečnostní hlavičky pro dynamické
   stránky; statické `/assets/` obsluhuje přímo Apache.

## Propojený model frontend + backend

Kombinovaný diagram ukazuje, jak Symfony request a `BackendApiClient` vstupují
do stejného REST/auth/domain/persistence toku. Backendové vrstvy a moduly jsou
detailně popsány v [dokumentu backendu](./erp-backend.md).

```mermaid
flowchart LR
    Browser["Browser<br/>HTML forms + Twig UI"]

    subgraph Frontend["erp-base-php · Apache / PHP / Symfony"]
        Apache["Apache<br/>document root public/"]
        Kernel["Symfony Runtime + Kernel"]
        Subscriber["FrontendRequestSubscriber<br/>Origin + login + access"]
        Session[("Symfony session<br/>token + profile")]
        Cookie["Symfony HttpOnly cookie"]
        Controllers["Route controllers<br/>auth / admin / modules / shop"]
        Support["ModuleControllerSupport<br/>validation + form mapping"]
        ApiClient["BackendApiClient<br/>Symfony HttpClient"]
        Twig["Twig renderer<br/>base + module templates"]
        Assets["public/assets"]
    end

    subgraph Backend["erp-backend · Spring Boot REST /api/v1"]
        AuthApi["AuthController<br/>login / me / logout"]
        Access["ApiAccess<br/>Bearer + role/module checks"]
        ApiControllers["Domain REST controllers<br/>companies / catalog / sales /<br/>purchase / inventory / CRM / ..."]
        Services["Domain services<br/>rules + transactions"]
        Repositories["Spring Data JPA repositories"]
        Model["JPA entities / domain model"]
        UserRepo["UserRepository"]
        Flyway["Flyway migrations"]
        DB[("PostgreSQL 18<br/>ERP data")]
    end

    Browser -->|"HTTP request/form"| Apache --> Kernel
    Kernel --> Subscriber
    Subscriber --> Session
    Session --> Cookie
    Cookie -->|"session id on requests"| Subscriber
    Subscriber --> Controllers --> Support
    Controllers -->|"POST /api/v1/auth/login"| AuthApi
    AuthApi --> UserRepo --> DB
    AuthApi -->|"token kept in Symfony session"| Session
    Controllers --> ApiClient
    ApiClient -->|"server-side Bearer + /api/v1/*"| Access
    Access --> ApiControllers
    ApiControllers --> Services
    ApiControllers -.->|"simple read/write"| Repositories
    Services --> Repositories
    Repositories --> Model --> DB
    Flyway -->|"schema/data versions"| DB
    Controllers -->|"view model"| Twig
    Twig -->|"HTML response"| Browser
    Assets -.-> Browser
```

```mermaid
sequenceDiagram
    actor User as Prohlížeč
    participant Apache as Apache/PHP
    participant Symfony as Symfony kernel
    participant Session as Symfony session
    participant Controller as Controller
    participant Client as BackendApiClient
    participant Backend as Spring API

    User->>Apache: POST /purchase (form)
    Apache->>Symfony: Request
    Symfony->>Session: Načíst session/cookie
    Symfony->>Symfony: Subscriber ověří Origin,<br/>přihlášení a expiraci
    Symfony->>Controller: Route + Request
    Controller->>Controller: Validovat/mapovat formulář
    Controller->>Client: mutate POST /api/v1/purchase/...
    Client->>Backend: JSON + Bearer token
    Backend-->>Client: HTTP status/JSON
    Client-->>Controller: Backend response
    Controller-->>Symfony: Redirect nebo Twig Response
    Symfony-->>User: HTTP response + security headers
```

## Session, views a deployment

Symfony framework nastavuje HttpOnly, SameSite=Lax cookie (Secure je
automaticky navázán na HTTPS). Serverová session obsahuje backend token,
uživatelský profil a časovou expiraci; po neaktivitě nebo absolutní expiraci
se invaliduje. Prohlížeč backend token nedostává.

Twig šablony v `templates/` dědí společný `base.html.twig`; `public/assets`
obsahuje UI styly a skripty. Controller groups pokrývají autentizaci,
administraci, samostatné moduly, operace, eCommerce a storefront.

Docker image skládá Composer vendor dependencies a Apache/PHP runtime
(`APP_ENV=prod`, `APP_DEBUG=0`). `BACKEND_URL` se předává přes environment.
V `docker-compose-PHP.yml` je UI host port `4201` → kontejner port `80`;
backend je `8080`, PostgreSQL `5434`. PHP frontend není databázovým klientem
a sám ERP tabulky nemění.
