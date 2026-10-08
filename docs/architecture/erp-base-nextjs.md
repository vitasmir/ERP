# Architektura `erp-base-nextjs`

První React varianta je Next.js App Router frontend s klientskými React moduly
a serverovým Backend-for-Frontend (BFF). Prohlížeč volá pouze same-origin
frontendové API; přístupový token se připojuje až na serveru při volání
Spring API.

## Komponentní diagram

```mermaid
flowchart TB
    Browser["Prohlížeč"]
    Next["Next.js 16 App Router<br/>Node.js 22"]

    subgraph Pages["App Router stránky"]
        Login["/login"]
        Apps["/apps<br/>launcher"]
        Module["/[module] a [...slug]<br/>ERP moduly"]
        Shop["/shop, /eshop<br/>veřejný obchod"]
    end

    subgraph UI["React UI"]
        Shell["Shell / navigace"]
        ModuleComponents["React moduly<br/>admin, operations,<br/>workforce, catalog..."]
        PublicComponents["Veřejné stránky a storefront"]
        Assets["CSS assety<br/>a React UI styly"]
    end

    subgraph BFF["Same-origin API a session server"]
        AuthRoutes["/api/auth/login<br/>/api/auth/logout"]
        ApiRoute["/api/backend/[...path]<br/>metodově omezený proxy"]
        CartRoute["/api/cart"]
        CheckoutRoute["/api/shop/checkout"]
        Proxy["proxy.ts<br/>route guard + headers"]
        Session["Session store<br/>opaque cookie + soubory"]
        Policy["policy.ts<br/>validace cesty,<br/>public API a Origin"]
    end

    Backend["erp-backend<br/>Spring REST /api/v1"]
    DB[("PostgreSQL")]

    Browser --> Next
    Next --> Pages
    Pages --> UI
    Module --> Shell
    Shell --> ModuleComponents
    Shop --> PublicComponents
    ModuleComponents -->|"fetch same-origin"| ApiRoute
    PublicComponents -->|"same-origin cart/checkout"| CartRoute
    PublicComponents --> CheckoutRoute
    Login --> AuthRoutes
    Proxy --> Session
    ApiRoute --> Policy
    ApiRoute --> Session
    AuthRoutes --> Session
    CartRoute --> Session
    CheckoutRoute --> Session
    ApiRoute -->|"server-side Bearer"| Backend
    AuthRoutes -->|"server-side Bearer"| Backend
    CartRoute --> Backend
    CheckoutRoute --> Backend
    Backend --> DB
    Assets -.-> Browser
```

## Tok stránky a API

- App Router skládá stránky pro přihlášení, launcher, jednotlivé ERP moduly
  a veřejný e-shop. Sdílený `Shell` poskytuje navigaci a session profil.
- Klientské React komponenty používají `src/lib/client-api.ts`. Ten posílá
  požadavky na `/api/backend/*`; UI nedostává backend token ani nevolá API
  host přímo.
- Catch-all BFF route ověřuje cestu, metodu, Origin a session, omezuje velikost
  request body, doplní Bearer token a přepošle JSON/binary response z
  `/api/v1/*`. Explicitní veřejná API výjimka slouží anonymním stránkám.
- Login/logout route oddělují autentizaci. Cart/checkout routes ukládají
  anonymní košík a údaje doručení na serveru a volají backend jen pro potřebná
  data nebo vytvoření objednávky.

## Propojený model frontend + backend

Tento diagram spojuje konkrétní BFF a React UI této varianty s interními
vrstvami Spring backendu. Detail backendových modulů a odpovědností je také
v [samostatném dokumentu backendu](./erp-backend.md).

```mermaid
flowchart LR
    Browser["Browser<br/>React UI"]

    subgraph Frontend["erp-base-nextjs · Next.js / Node.js"]
        AppRouter["App Router<br/>login / apps / module / shop"]
        Components["React components<br/>shell + ERP modules"]
        ClientApi["client-api.ts<br/>same-origin fetch"]
        AuthRoute["/api/auth/login<br/>/api/auth/logout"]
        Proxy["/api/backend/[...path]<br/>path + Origin + body guard"]
        ShopRoutes["/api/cart<br/>/api/shop/checkout"]
        Session[("Session files<br/>token + profile + cart")]
        Cookie["Opaque HttpOnly cookie"]
        Renderer["Next.js React rendering"]
    end

    subgraph Backend["erp-backend · Spring Boot REST /api/v1"]
        AuthApi["AuthController<br/>/auth/login, /auth/me, /auth/logout"]
        Access["ApiAccess<br/>Bearer validation<br/>role/module permission"]
        ApiControllers["Domain REST controllers<br/>companies, users, catalog,<br/>sales, purchase, inventory,<br/>accounting, CRM, HR, ..."]
        Services["Domain services<br/>transactions + business rules"]
        Repositories["Spring Data JPA repositories"]
        Model["JPA entities / domain model"]
        UserRepo["UserRepository<br/>credential lookup"]
        Flyway["Flyway SQL migrations"]
        DB[("PostgreSQL 18<br/>ERP data")]
    end

    Browser -->|"page navigation"| AppRouter
    AppRouter --> Components
    Components -->|"client actions"| ClientApi
    ClientApi -->|"same-origin requests"| Proxy
    Browser -->|"login form"| AuthRoute
    AuthRoute -->|"POST /api/v1/auth/login"| AuthApi
    AuthApi --> UserRepo --> DB
    AuthApi -->|"access token"| Session
    Session --> Cookie
    Cookie -->|"sent on later requests"| Proxy
    ShopRoutes --> Session
    Proxy -->|"server-side Bearer + /api/v1/*"| Access
    ShopRoutes -->|"allowed public/authorized API calls"| Access
    Access --> ApiControllers
    ApiControllers --> Services
    ApiControllers -.->|"simple read/write"| Repositories
    Services --> Repositories
    Repositories --> Model --> DB
    Flyway -->|"schema/data versions"| DB
    Components --> Renderer --> Browser
```

```mermaid
sequenceDiagram
    actor User as Prohlížeč
    participant UI as React komponenta
    participant API as Next.js same-origin route
    participant Session as Server session store
    participant ERP as Spring API

    User->>UI: Odeslání formuláře / změna dat
    UI->>API: fetch /api/backend/{resource}
    API->>API: Ověřit metodu, API cestu, Origin a limit těla
    API->>Session: Načíst session podle HttpOnly cookie
    Session-->>API: Token uživatele, případně null
    API->>ERP: /api/v1/{resource}<br/>Authorization: Bearer (server-side)
    ERP-->>API: JSON, PDF nebo HTTP chyba
    API-->>UI: Same-origin response, no-store
    UI-->>User: Aktualizovaný React UI / chyba
```

## Session a hranice důvěry

Session soubor obsahuje backend token, jméno/roli a expiraci. Prohlížeč drží
pouze náhodné opaque ID v HttpOnly, SameSite cookie; samotný token není
v localStorage ani React state. Session store používá soubory s omezenými
právy a limity absolutní i idle expirace. `proxy.ts` přesměruje neautorizované
vstupy do chráněných UI cest na login; API route nezávisle kontroluje session.

PostgreSQL a obchodní pravidla nejsou součást frontendové persistence.
Statické CSS je servírováno z `public/assets` a nové sdílené React styly
doplňují původní vzhled. Více instancí vyžaduje sdílené úložiště sessions
a distribuované zamykání.

## Běh

Compose varianta `docker-compose-NEXTJS.yml` staví samostatný Node.js 22
standalone image; frontend je na portu `3000`, backend na `8080` a PostgreSQL
na host portu `5434`. Frontendová a backendová služba komunikují přes
`projects-network`. V lokálním vývoji může `BACKEND_URL` výchozí hodnotou
ukazovat na `http://localhost:8080`.
