# Architektura `erp-base-nextjs3` (React/TSX, bez Twig)

`erp-base-nextjs3` zachovává serverový request/response model a TypeScript
controller port z varianty Next.js 2, ale místo Twig souborů vrací běžné
React TSX komponenty vykreslené na serveru. React komponenty nahrazují
templating vrstvu; browser-side hydration ani Twig runtime nejsou potřeba.

## Komponentní diagram

```mermaid
flowchart TB
    Browser["Prohlížeč<br/>HTML formuláře + PHP assets"]
    Next["Next.js 16.3<br/>Node.js 22<br/>standalone server"]
    Route["Catch-all App Router<br/>GET / HEAD / POST"]

    subgraph Server["Node.js BFF a controller runtime"]
        Dispatch["server.ts<br/>Origin, session, auth,<br/>module permission"]
        Session["session.ts<br/>opaque cookie + soubory"]
        Controllers["TypeScript route controllers<br/>admin / auth / commerce /<br/>operations / shop"]
        Context["context.ts<br/>Backend + FormData + Page"]
    end

    subgraph View["Server-side React"]
        Registry["components/render.tsx<br/>view registry + assets"]
        Shared["components/shared.tsx<br/>document, shell, helpers"]
        Pages["components/pages/*.tsx<br/>core / business / operations"]
        Inline["InlinePageScript markers<br/>scripts emitted after assets"]
    end

    Assets["public/assets<br/>PHP CSS/JS unchanged"]
    Layout["public/compatibility.css"]
    Backend["erp-backend<br/>Spring REST /api/v1"]
    DB[("PostgreSQL")]

    Browser --> Route --> Dispatch
    Dispatch --> Session
    Dispatch --> Controllers --> Context
    Context -->|"server-side JSON/Bearer"| Backend
    Controllers -->|"Page(view, data)"| Registry
    Registry --> Shared
    Registry --> Pages
    Pages --> Inline
    Shared -->|"React SSR -> HTML document"| Browser
    Inline -->|"after shared/page scripts"| Browser
    Assets -.-> Browser
    Layout -.-> Browser
    Backend --> DB
```

## Vykreslení stránky

1. Route handler předá request do dispatcheru, který kontroluje Origin,
   session, přihlášení a oprávnění modulu.
2. TypeScript controller přečte query/form data, volá backend přes serverový
   `Backend` klient a sestaví `Page` (`view`, `data`, HTTP status).
3. Registry v `render.tsx` namapuje `view` na pojmenovanou React komponentu,
   stránkové styly a skripty. `AppDocument` obalí stránky společným HTML
   dokumentem, sidebar/topbar shell nebo veřejnou/login variantou.
4. React server renderer vrátí statické HTML. `InlinePageScript` dovoluje
   přesunout potřebné inline chování za společné a stránkové skripty, aby se
   zachovalo pořadí jako v PHP stránce.
5. Prohlížeč používá tradiční GET/POST formuláře a originální JavaScript.
   React komponenty se po načtení nehydratují.

```mermaid
sequenceDiagram
    actor User as Prohlížeč
    participant Route as Next route
    participant Dispatch as server.ts
    participant Controller as TS controller
    participant Backend as Spring API
    participant Renderer as React SSR

    User->>Route: GET /purchase
    Route->>Dispatch: Request + session cookie
    Dispatch->>Dispatch: Guard + session + permission check
    Dispatch->>Backend: GET /api/v1/purchase/overview
    Backend-->>Dispatch: JSON
    Dispatch->>Controller: Context / route dispatch
    Controller->>Backend: načtení dat pro view
    Backend-->>Controller: JSON response
    Controller-->>Dispatch: Page(view, data)
    Dispatch->>Renderer: renderPage(Page, path, session)
    Renderer-->>User: úplný HTML dokument
    User->>Route: POST formulář
    Route->>Dispatch: Origin-checked FormData
    Dispatch->>Controller: mapování + validace
    Controller->>Backend: server-side mutace
    Backend-->>Controller: status
    Controller-->>User: redirect/feedback response
```

## Bezpečnostní a datové hranice

Opaque HttpOnly session cookie odkazuje na serverový session soubor, který
obsahuje token, roli/profil a stav guest cart/doručení. Token neopouští
server-side controller/Backend klient. Node runtime spravuje rotaci,
idle/absolutní expiraci a same-origin guard pro POST. Samotná práva modulů
a validace business pravidel znovu vynucuje backend.

CSS/JavaScript jsou kopie původních PHP assetů; layout změny jsou oddělené
v `compatibility.css`. Veřejné routy shop a web/public nepoužívají přihlášený
app shell. Žádné `.twig` soubory ani Twig balíček nejsou součástí projektu.

`docker-compose-NEXTJS3.yml` spouští UI na `3000`, backend na `8080`,
PostgreSQL na host portu `5434` a warehouse mock na `8091`. Vlastní volume
`erp-nextjs3-sessions` je oddělen od sessions ostatních frontendu variant.
