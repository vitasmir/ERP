# Blue-green ERP deployment with Docker Compose

This configuration uses the real services from `docker-compose-JSP.yml`:
PostgreSQL, the Spring Boot backend, the JSP WAR frontend, and the remote
warehouse mock. It builds the green backend and frontend from their repository
Dockerfiles; blue uses images from the currently deployed release.

## Before deployment

- Set `ERP_BACKEND_BLUE_IMAGE` and `ERP_FRONTEND_BLUE_IMAGE` to immutable
  images of the current release. If the existing deployment has no tagged
  images, build and publish its current revision before deploying the newer
  source as green.
- Set `POSTGRES_USER` and `POSTGRES_PASSWORD` to the credentials for the
  existing database. `POSTGRES_DB` defaults to `erp`.
- Keep database credentials outside source control.
- Keep `DOCKER_NETWORK` set to the existing external network; it defaults to
  `projects-network`.
- Use the same Compose project name as the existing `docker-compose-JSP.yml`
  deployment. This is essential: the Postgres named volume is scoped to the
  Compose project. When running from the repository root with the default
  project name, use `-p erp`; if the existing deployment used another `-p`
  value, reuse that value instead.
- Route public traffic to the Nginx proxy's published port (default `8088`).
  For HTTPS termination before this proxy, configure Tomcat's `RemoteIpValve`
  to honor forwarded scheme/host headers from the trusted proxy; the JSP
  frontend validates same-origin form submissions against the servlet request
  URL.

Optional variables:

- `ERP_BACKEND_GREEN_IMAGE` and `ERP_FRONTEND_GREEN_IMAGE` (default to local
  tags `erp-backend:green` and `erp-jsp:green`).
- `POSTGRES_DB` (default `erp`), `POSTGRES_HOST_PORT` (default `5434`),
  `REMOTE_WAREHOUSE_HOST_PORT` (default `8091`), `HTTP_PORT` (default `8088`),
  and `DOCKER_NETWORK` (default `projects-network`).

## Deploy green

1. Start the persistent database and current blue version, plus the mock and
   proxy:

   ```sh
   docker compose -p erp -f docker-compose-blue-green.yml up -d \
     postgres backend-blue frontend-blue remote-warehouse-mock proxy
   ```

   The proxy initially routes to `frontend-blue`. Before directing live
   ingress to it, verify that the configured blue images and shared database
   are the expected existing release and data.

2. Build and start the candidate backend. Its startup runs Flyway migrations:

   ```sh
   docker compose -p erp -f docker-compose-blue-green.yml up -d --build backend-green
   docker compose -p erp -f docker-compose-blue-green.yml logs -f backend-green
   ```

   Wait for successful application and migration startup before proceeding.
   The backend does not currently expose a dedicated health endpoint. Take
   and verify a database backup before doing this in production.

3. Build and start the candidate JSP frontend, then smoke-test it without
   switching public traffic:

   ```sh
   docker compose -p erp -f docker-compose-blue-green.yml up -d --build frontend-green
   docker compose -p erp -f docker-compose-blue-green.yml exec proxy \
     wget -qO /dev/null http://frontend-green:8080/
   ```

   Also verify important application flows against the green backend.

4. Switch traffic by editing `deploy/nginx/active.conf` from
   `server frontend-blue:8080;` to `server frontend-green:8080;`, then check
   and reload Nginx:

   ```sh
   docker compose -p erp -f docker-compose-blue-green.yml exec proxy nginx -t
   docker compose -p erp -f docker-compose-blue-green.yml exec proxy nginx -s reload
   ```

5. Monitor green and keep blue running through the rollback window. Stop blue
   only after accepting the new release:

   ```sh
   docker compose -p erp -f docker-compose-blue-green.yml stop \
     frontend-blue backend-blue
   ```

The remote warehouse mock remains a single shared service, like in the JSP
Compose stack. PostgreSQL remains a single service with the existing named
volume; do not change the Compose project name or volume when upgrading.

## Rollback and migration compatibility

To roll back traffic, restore `server frontend-blue:8080;` in
`deploy/nginx/active.conf`, run `nginx -t`, and reload Nginx. This restores
application traffic, **not** database changes. It is safe only if the new
migration leaves the schema compatible with the previous application.

Flyway is enabled at backend startup, so starting green migrates the shared
database while blue is still running. Use expand/contract migrations: add
backward-compatible schema first, deploy code that works with both schemas,
and remove old schema only in a later release after blue is no longer needed.
Do not rely on an automatic Flyway downgrade for rollback.

The JSP frontend stores login sessions in Tomcat memory, so switching frontend
slots does not transfer active sessions; users will need to sign in again.
