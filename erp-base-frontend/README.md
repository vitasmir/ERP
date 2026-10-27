# ERP Base frontend (legacy)

Původní JSP WAR implementace `erp-base-frontend`, ponechaná jako migrační reference.
Aktivní frontend spouštěný přes kořenový Docker Compose je nová aplikace
[`erp-base-php`](../erp-base-php) postavená na Symfony 7.4.

Obsahuje základní navigaci ERP, přehled uživatelů, role a rychlé akce. Spouští se jako služba `frontend` v kořenovém `docker-compose.yml`.

```bash
docker compose up --build
```

Aplikace: `http://localhost:4200`

## Sklad

Modul `/inventory` používá stávající katalog produktů a skladové lokace.
Produkty se spravují v modulu eCommerce; není nutné vytvářet druhý katalog.
Pohled **Sklad** umožňuje hledat podle názvu, SKU a lokace a filtrovat zásoby
pod minimem. Produktový pohled zachovává objednávky z centrálního skladu.

Uživatel s oprávněním k úpravám skladu může u položky zvolit **Příjem** nebo
**Výdej**, zadat kladné celočíselné množství, doklad a volitelnou poznámku.
Při příjmu se ukládá také minimum a pořizovací cena; výdej je nemění.
Výdej nad dostupné množství ani přetečení skladového množství nejsou povoleny.
Role pouze pro čtení nemají zobrazené formuláře pro změnu zásob.

Pohled **Historie** má filtr podle skladové položky a stránkování po 20
pohybech. Každý pohyb uchovává produkt, SKU, jednotku, lokaci, množství,
stav po pohybu, pořizovací cenu, doklad, poznámku, uživatele a čas.
Historické názvy a SKU zůstanou zachovány i po přejmenování produktu.
Časy se zobrazují v pásmu Europe/Prague.

Migrace `V48__inventory_movements.sql` zaznamená stávající nenulové zásoby jako
počáteční stav. Nové příjmy z nákupních objednávek se zapisují do stejné
historie. Objednávka z centrálního skladu sama o sobě není skladový pohyb.
E-shop nadále pouze kontroluje dostupnost; automatická expedice nebo
rezervace zásob není součástí tohoto doplnění.

Historii nelze měnit ani mazat. Opravu zaúčtujte opačným pohybem s odkazem
na původní doklad. Produkt s historií nelze smazat ani změnit jeho jednotku;
pro vyřazení použijte deaktivaci v katalogu.
Při chybě spojení před opakováním operace ověřte historii: backend mohl
pohyb uložit, i když se potvrzení nepodařilo doručit.

### Ověření

```bash
cd ../erp-backend
mvn -Dtest=InventoryItemTests,InventoryIntegrationTests test
cd ../erp-base-frontend
mvn package
```

Integrační testy používají izolovaný PostgreSQL 18 přes Testcontainers;
vyžadují běžící Docker a nemění data vašeho ERP.
