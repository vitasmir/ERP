# Blue-green ERP deployment with Docker Compose

This deployment file is for the repository's JSP WAR frontend
(`erp-base-frontend`) and Spring Boot backend. It runs both application
versions against the **same existing PostgreSQL database**. It does not define
a database service, so it will not silently create a second database or
volume.

## Prerequisites

- Build and publish four immutable images: current and candidate backend and
  frontend. Do not use `latest` for a release.
- The existing database and this deployment must share the external Docker
  network. By default the network is `projects-network`, matching
  `docker-compose-JSP.yml`.
- Keep the database credentials outside source control. Provide them through
  the deployment environment or a protected `.env` file.
- Put the proxy behind the deployment's TLS terminator and route public
  traffic to its published port (default `8088`). For HTTPS termination
  before this proxy, configure Tomcat's `RemoteIpValve` to honor forwarded
  scheme/host headers from the trusted proxy; the JSP frontend validates
  same-origin form submissions against the servlet request URL.

Required variables:

```text
ERP_BACKEND_BLUE_IMAGE
ERP_BACKEND_GREEN_IMAGE
ERP_FRONTEND_BLUE_IMAGE
ERP_FRONTEND_GREEN_IMAGE
DB_URL
DB_USERNAME
DB_PASSWORD
```

Optional variables are `DOCKER_NETWORK` (default `projects-network`) and
`HTTP_PORT` (default `8088`). For an existing Compose database, `DB_URL` will
commonly be `jdbc:postgresql://postgres:5432/erp`, provided its network alias
is `postgres`.

## Deploy a candidate

1. Start the existing blue containers and proxy:

   ```sh
   docker compose -f docker-compose-blue-green.yml up -d \
     backend-blue frontend-blue proxy
   ```

   The Nginx config in `deploy/nginx/active.conf` initially routes to blue.

2. Pull and start only the green backend:

   ```sh
   docker compose -f docker-compose-blue-green.yml pull backend-green
   docker compose -f docker-compose-blue-green.yml up -d backend-green
   docker compose -f docker-compose-blue-green.yml logs -f backend-green
   ```

   Spring Boot runs the configured Flyway migrations during backend startup.
   Wait for startup and migration logs, then verify the backend using your
   deployment's smoke tests. This repository does not currently expose a
   dedicated health endpoint.

3. Start green frontend and smoke-test it before changing public traffic:

   ```sh
   docker compose -f docker-compose-blue-green.yml pull frontend-green
   docker compose -f docker-compose-blue-green.yml up -d frontend-green
   ```

   For example, from the proxy container, request
   `http://frontend-green:8080/` and verify the relevant application flows.

4. Switch traffic. Edit `deploy/nginx/active.conf`, changing
   `server frontend-blue:8080;` to `server frontend-green:8080;`, then
   validate and reload Nginx:

   ```sh
   docker compose -f docker-compose-blue-green.yml exec proxy nginx -t
   docker compose -f docker-compose-blue-green.yml exec proxy nginx -s reload
   ```

5. Monitor the new release. Keep blue running until the rollback window has
   passed; then it can be stopped:

   ```sh
   docker compose -f docker-compose-blue-green.yml stop frontend-blue backend-blue
   ```

## Rollback

Change the Nginx upstream back to `frontend-blue:3000`, validate with
`nginx -t`, and reload Nginx. This rolls back application traffic, **not**
database changes. It is safe only if the new migration remains compatible
with the previous application version.

Flyway is enabled at application startup, so starting green performs the
migration while blue is still live. Design schema changes using expand/contract:
first add backward-compatible schema, deploy code that works with both
schemas, migrate existing data as a compatible step, and only remove old
schema in a later release after blue is no longer needed. Take and verify a
database backup before production migrations. Do not rely on an automatic
Flyway downgrade for rollback.

The JSP frontend stores login sessions in Tomcat memory, so switching frontend
slots does not transfer active sessions; users will need to sign in again.
