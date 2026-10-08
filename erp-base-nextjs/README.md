# ERP Next.js frontend

React / TypeScript frontend migrated from `erp-base-php`, using Next.js App
Router. The Spring Boot backend remains the owner of business rules and data.
The PHP and JSP projects are retained as references.

## Run

From the repository root:

```bash
docker compose up --build -d
```

Open `http://localhost:4201`. The external network `projects-network` must exist.

For local development, start the backend and PostgreSQL, then:

```bash
cd erp-base-nextjs
npm ci
npm run dev
```

The development UI is on port 3000. `BACKEND_URL` defaults to
`http://localhost:8080`; see `.env.example` for overrides.

## Boundaries

- React components call same-origin `/api/backend/*`, never the backend directly.
- The server forwards requests to `/api/v1/*` and attaches the backend token.
  Spring continues to enforce per-module read/write permissions.
- Login tokens stay in server-side session files. The browser only receives an
  opaque HttpOnly, SameSite cookie. Login rotates the session identifier.
- Login expires after 30 idle minutes or eight hours in total. Compose persists
  sessions and guest carts in `erp-nextjs-sessions`; run only one frontend replica
  with this filesystem store. A distributed session store is needed for scaling.
- Mutations verify the request origin. Only explicit public API routes can be
  accessed without login. Public settings expose the delivery fee only.
- Public storefront cart and delivery data are stored server-side. Card payment
  remains a demo action, not a payment gateway; no card credentials are collected.
  The existing checkout API charges merchandise only, not the delivery fee.
- Module forms and actions are React components. The old imperative JavaScript is
  not loaded. Existing CSS is retained alongside responsive shared React styling.

Use `COOKIE_SECURE=true` for HTTPS deployment. Back up or restrict the session
volume as sensitive runtime data; do not commit it. PHP sessions cannot be reused
by the new application: sign in again after migration.

## Validate

```bash
npm run typecheck
npm run build
npm test
```

Tests include session expiry, API/origin boundaries, cart validation and module
regressions. The runtime test requires a production build and launches an isolated
Next.js server plus a mock backend; it does not modify the ERP database.
