# ERP Next.js 16.3 frontend

Faithful Node.js port of `erp-base-php`, independent of `erp-base-nextjs`.
Next.js **16.3.8** serves the original HTML templates through TypeScript route
handlers. Twig.js renders them on the server; **PHP is not required at runtime**.
React is installed as required by Next.js, but does not hydrate or replace the
original pages. This preserves the DOM expected by the PHP frontend's scripts.

## Preserved UI and workflows

- CSS and JavaScript files in `public/assets` are copied from PHP. All remain
  unchanged except for the shared calendar's native modal dialog fix.
- The sidebar, navigation, launcher tiles, search, filters, detail drawer,
  dialogs, date/color pickers and module forms use the original markup/scripts.
- GET/POST URLs, form names, backend API calls and feedback are retained for
  administration, dashboard, accounting/PDF, CRM, documents, projects, helpdesk,
  marketing, website/public pages, HR, planning, sales, purchase, manufacturing,
  POS, inventory/history, promotions, eCommerce and the public storefront.
- Authentication tokens, guest carts and delivery details stay server-side.
  Login rotates the opaque HttpOnly session cookie and expires after 30 idle
  minutes or eight hours. The new cookie is independent of PHP and Next.js v1;
  sign in again when switching applications.
- The storefront's card fields retain the PHP demo behavior: validation only,
  no real payment gateway, no persisted card number or CVC. Checkout continues
  to charge merchandise through the existing backend, not the displayed
  delivery surcharge.

The copied HR and purchase templates need syntax adaptation: Twig.js does
not support PHP Twig arrow-function filters, so equivalent `where` filters are
used. A small compatibility stylesheet restores standard `[hidden]` behavior:
original module CSS otherwise overrides it, leaving searched-out tiles and
hidden payment labels visible. The shared calendar uses a native modal dialog
so it appears above other native dialogs, such as the purchase order form;
the compatibility stylesheet preserves its original appearance. Other original
asset files remain byte-identical. Page markup is otherwise unchanged.
Script JSON escapes HTML delimiters. The
storefront reads `/api/v1/settings/public` for the delivery fee rather than
requesting administrative settings anonymously.

## Local development

Start the existing backend and PostgreSQL, then:

```bash
cd erp-base-nextjs2
npm ci
npm run dev
```

Open `http://localhost:3000`. `BACKEND_URL` defaults to `http://localhost:8080`.
See `.env.example` for session storage and HTTPS cookie options. Node.js 22 or
newer is required. For production locally, use `npm run build && npm start`.

## Docker

From the repository root:

```bash
docker compose -f docker-compose-NEXTJS2.yml up --build -d
```

The UI uses port **3000**, the backend **8080**, PostgreSQL **5434**, and the
warehouse mock **8091**. The external `projects-network` must already exist
(`docker network create projects-network` on first setup).

Do not run the PHP, first Next.js and second Next.js Compose variants together:
they share service names and backend ports. Stop the active variant with its
own Compose file before switching; do not use `down -v`, which deletes data.
The dedicated Compose file keeps this variant's configuration and session
volume separate from the first Next.js frontend.

The standalone Docker image runs as a non-root Node.js user and includes both
templates and static assets. Login sessions and carts persist in the separate
`erp-nextjs2-sessions` volume. Use `COOKIE_SECURE=true` under HTTPS and restrict
access to that volume. The file session store supports one frontend replica;
multiple replicas require a shared store with distributed request locking.

## Validation

```bash
cd erp-base-nextjs2
npm run typecheck
npm run build
npm test
```

Tests exercise controller requests, validations, template rendering, unchanged
assets, authentication, public checkout and standalone production HTTP serving.
The production HTTP test starts isolated servers with the same packaged server,
templates and assets used by Docker and never touches the ERP database.
When local PHP and the existing Symfony dependencies are available, a
differential test compares generated HTML against the original PHP Twig engine.
