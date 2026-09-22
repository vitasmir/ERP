# ERP Base frontend

První Odoo-like modul `base` implementovaný jako JSP WAR aplikace nasazená v Tomcat 10.1.

Obsahuje základní navigaci ERP, přehled uživatelů, role a rychlé akce. Spouští se jako služba `frontend` v kořenovém `docker-compose.yml`.

```bash
docker compose up --build
```

Aplikace: `http://localhost:4200`
