# ERP Next.js 16.3 React frontend

Independent React rewrite of `erp-base-php`, built with Next.js 16.3.8.
Every screen is implemented as a typed TSX component; there are no Twig files
or runtime template engines. The original CSS and JavaScript assets are copied
from the PHP frontend, with the shared calendar using the same native modal
dialog fix as Next.js 2 so it opens above purchase dialogs. TypeScript route controllers preserve its backend
requests, forms, validation, sessions, redirects and access checks.

## Pages and workflows

The React pages cover authentication, the application launcher, administration,
dashboard, accounting/PDF, CRM, documents, projects, helpdesk, marketing,
website/public pages, HR, planning, sales, purchase, manufacturing, POS,
inventory/history, promotions, eCommerce and the public storefront.
Published website page content renders authored HTML, CSS and JavaScript, so
only trusted editors should be allowed to publish page content.

The Node.js server maintains opaque HttpOnly sessions for backend tokens, guest
carts and delivery details. Login rotates the cookie; authenticated sessions
expire after 30 idle minutes or eight hours. Card checkout retains the original
demo validation and does not persist or send card credentials. The UI presents
a delivery charge, while the existing backend charges merchandise only.

## Development

Start the existing backend and PostgreSQL, then:

```bash
cd erp-base-nextjs3
npm ci
npm run dev
```

Open `http://localhost:3000`. Configure `BACKEND_URL`, `SESSION_DIR`, and
`COOKIE_SECURE` with `.env.local` as needed. Node.js 22 or newer is required.
For production locally, use `npm run build && npm start`.

## Docker

From the repository root:

```bash
docker compose -f docker-compose-NEXTJS3.yml up --build -d
```

The UI uses port 3000, the backend 8080, PostgreSQL 5434 and warehouse mock
8091. The external `projects-network` must exist. This compose file uses the
existing ERP PostgreSQL data volume and its own `erp-nextjs3-sessions` volume.
Do not run frontend compose variants concurrently: their backend and published
ports overlap. Switch variants with their corresponding Compose file, and do
not use `down -v`.

The standalone image runs as a non-root Node.js user. The filesystem session
store supports one frontend replica; multiple replicas require shared storage
with distributed locking.

## Validation

```bash
npm run typecheck
npm run build
npm test
```
