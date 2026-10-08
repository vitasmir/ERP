# Architektura `erp-base-nextjs2` (Twig.js)

Tato varianta přenáší PHP controller a template tok do TypeScriptu/Node.js.
Next.js poskytuje HTTP server a route handler; vlastní dispatch mapuje URL
do portovaných controllerů a Twig.js server-side renderuje HTML z běžných
Twig šablon. PHP interpreter v runtime není potřeba.

## Komponentní diagram

```mermaid
flowchart TB
    Browser["Prohlížeč<br/>původní HTML/CSS/JS"]
    Next["Next.js 16.3<br/>Node.js 22<br/>standalone server"]
    CatchAll["App Router catch-all<br/>GET / HEAD / POST"]

    subgraph Runtime["Port PHP frontend runtime"]
        Guard["server.ts dispatch<br/>metody, Origin, hlavičky"]
        Session["session.ts<br/>opaque ID, files,<br/>lock, expiry"]
        Access["Route auth + module<br/>permission read-check"]
        Controllers["TypeScript controllers<br/>admin / auth / commerce /<br/>operations / shop"]
        Context["context.ts<br/>Backend client, form,<br/>redirect, Page contract"]
        Renderer["templates.ts<br/>Twig.js render"]
    end

    Templates["templates/**/*.twig<br/>base + module views"]
    Assets["public/assets<br/>PHP CSS/JS<br/>+ compatibility.css"]
    Backend["erp-backend<br/>/api/v1/*"]
    DB[("PostgreSQL")]

    Browser -->|"GET stránky / POST formuláře"| Next
    Next --> CatchAll --> Guard
    Guard --> Session
    Guard --> Access
    Access --> Controllers
    Controllers --> Context
    Context -->|"HTTP + server-side Bearer"| Backend
    Controllers --> Renderer
    Renderer --> Templates
    Templates -->|"kompletní serverové HTML"| Browser
    Assets -.-> Browser
    Backend --> DB
```

## Zpracování požadavku

- Vstupní `src/app/[[...path]]/route.ts` předává GET/HEAD/POST do centrálního
  `dispatch`. Dispatch ověřuje metodu, původ mutací a session, načítá
  `FormData`, chrání privátní cesty a provádí API read-check modulu.
- Rodiny TypeScript controllerů zpracují původní cesty a formulářová pole,
  zavolají `Backend` helper a vrátí buď `Response` (redirect/PDF/error),
  nebo `Page` s názvem Twig šablony a daty.
- `templates.ts` vykreslí dokument na serveru; prohlížeč obdrží tradiční HTML.
  Nepoužívá React hydration. Tím zůstává kompatibilní původní imperative
  JavaScript a markup hooky.
- Backend API komunikace je server-to-server pod `/api/v1/*`; prohlížeč
  backend token nevidí. Negativní backend statusy se mapují na původní
  formulářový feedback/redirect nebo dokumentovou odpověď.

```mermaid
sequenceDiagram
    actor User as Prohlížeč
    participant Route as Next route handler
    participant Dispatch as Node dispatch
    participant Controller as TS controller
    participant Twig as Twig.js renderer
    participant Backend as Spring API

    User->>Route: GET /inventory
    Route->>Dispatch: Request + cookie
    Dispatch->>Dispatch: Session/auth/access check
    Dispatch->>Backend: GET /api/v1/inventory/overview
    Backend-->>Dispatch: JSON
    Dispatch->>Controller: Context + data
    Controller->>Backend: další server-side API GET
    Backend-->>Controller: JSON
    Controller-->>Dispatch: Page(view, data)
    Dispatch->>Twig: template + context
    Twig-->>User: HTML + CSS/JS asset odkazy
```

## Session, assety a nasazení

Serverová session ukládá token, profil a podle potřeby i košík/doručení.
Prohlížeč drží náhodné opaque HttpOnly/SameSite cookie. Session se obnovuje
při aktivitě, má idle a absolutní expiraci a zapisuje se atomicky do souborů.
Při přihlášení/odhlášení se identifikátor rotuje.

Origin guard chrání mutující požadavky; security headers a `no-store`
chrání dynamické stránky. Původní PHP CSS/JavaScript jsou statické soubory
v `public/assets`, k nim se připojují úzce vymezené layout compatibility
styly. HTML struktura formulářů a ID/class hooky jsou zachovány.

Compose soubor `docker-compose-NEXTJS2.yml` používá vlastní session volume
`erp-nextjs2-sessions`, host UI port `3000`, backend `8080` a PostgreSQL
`5434`. File session store předpokládá jednu instanci frontendu, dokud nebude
nahrázen sdíleným úložištěm s distribuovaným zámkem.
