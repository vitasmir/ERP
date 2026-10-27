# Implementační plán: ERP jako produkční SaaS

Datum: 13. 10. 2026. Plán vychází ze současného repozitáře, nikoli z auditu
běžící infrastruktury. Nejde o potvrzení bezpečnosti ani účetní legislativní
shody. Níže uvedené výkonnostní a provozní cíle jsou návrhy k odsouhlasení.

## 1. Potvrzené zadání a hranice první verze

- Hlavní frontend: **JSP / Java Servlet**, společný Spring Boot backend.
- Produkt: **SaaS pro více zákaznických firem**.
- Izolace: **samostatná instance frontendu a backendu i samostatná databáze
  pro každého zákazníka**. Nepřecházet nyní na sdílenou aplikaci s `tenant_id`.
- Nasazení bez Kubernetes, pomocí Docker Compose na spravovaných Linux VM.
- První pilot: katalog, sklad, nákup, prodej, uživatelé a role; fakturace
  pouze po odsouhlasení jejího rozsahu a ověření s účetním.
- Veřejný checkout a platby kartou nepovolovat do dokončení skutečného
  platebního a expedičního procesu. Veřejný web zapnout jen při samostatném
  ověření. Ostatní moduly nejsou automaticky produkčně hotové jen tím,
  že mají obrazovku nebo API.

Před zahájením doménových rozšíření potvrdit počet pilotních zákazníků,
uživatelů, požadované moduly, jurisdikci, rozpočet, datové objemy a SLA.
Jedna zákaznická firma není totéž co více právních subjektů uvnitř jednoho
zákaznického ERP; druhá varianta vyžaduje další doménový návrh.

## 2. Co již existuje a na co navázat

| Oblast | Ověřený současný stav | Dopad na plán |
| --- | --- | --- |
| Backend | Spring Boot, JPA, Flyway, validační pravidla; [konfigurace](../erp-backend/src/main/resources/application.yml) | Zachovat backend, doplnit produkční profil a provozní nástroje. |
| Přístup | [ApiAccess](../erp-backend/src/main/java/com/example/erp/users/ApiAccess.java) ukládá hashe náhodných tokenů do DB a kontroluje modulová oprávnění | Nevytvářet druhý autorizační systém; doplnit lifecycle identity a testy přístupové matice. |
| Přihlášení JSP | [LoginServlet](../erp-base-frontend/src/main/java/com/example/erp/frontend/base/LoginServlet.java) má serverovou session, obnovu session při loginu, 30 minut neaktivity a absolutní limit 8 hodin | Doplnit konfiguraci cookies, proxy, ochrany formulářů a restartové chování. |
| Sklad a nákup | [InventoryStockService](../erp-backend/src/main/java/com/example/erp/inventory/InventoryStockService.java) používá transakce a zamykání; [PurchaseReceipts](../erp-backend/src/main/java/com/example/erp/purchase/PurchaseReceipts.java) řeší opakovaný příjem; existuje historie a vážené ocenění | Zachovat a rozšířit na další kritické zápisy; nedělat sklad znovu. |
| Faktury | [AccountInvoice](../erp-backend/src/main/java/com/example/erp/accounting/AccountInvoice.java) má workflow a optimistic locking; [InvoiceRecords](../erp-backend/src/main/java/com/example/erp/accounting/InvoiceRecords.java) eviduje platby, události a přílohy | Doplnit úplné údaje dokladů, opravy, číslování a účetní ověření. |
| Testy | Backend má jednotkové i PostgreSQL integrační testy; ve frontendovém `src/test` nebyly nalezeny testy | Doplnit automatizované JSP/E2E a nasazovací testy. |
| SaaS | [Company](../erp-backend/src/main/java/com/example/erp/companies/Company.java) je obchodní evidence; v prohledaných backendových zdrojích není tenantový kontext | SaaS identitu a provisioning oddělit od seznamu společností. |
| Nasazení | [JSP Compose](../docker-compose-JSP.yml) používá vývojové DB údaje, publikuje DB/API porty a sdílenou externí síť | Vytvořit samostatnou produkční konfiguraci, neměnit vývojové workflow. |
| Runtime | [Frontend POM](../erp-base-frontend/pom.xml) cílí na Servlet 6.1, [Dockerfile](../erp-base-frontend/Dockerfile) používá Tomcat 10.1 | Sjednotit kontrakt; preferovat kompatibilní Tomcat 11 a ověřit kompletní WAR/JSP. |
| Inicializace | Historické Flyway migrace obsahují demo záznamy i [seed administrátora](../erp-backend/src/main/resources/db/migration/V35__seed_admin_user.sql) | Produkční onboarding nesmí kopírovat demo data nebo standardní účet. |
| Checkout | [EshopCheckoutController](../erp-backend/src/main/java/com/example/erp/sales/EshopCheckoutController.java) označuje CARD jako uhrazené podle vstupu; ověřuje zásobu bez rezervace či výdeje | Veřejný checkout není podkladem pro reálné karetní platby a expedici. |

V repozitáři nebyla nalezena složka `.github` s CI workflow. Toto tvrzení
nevylučuje externí pipeline. Observabilita, zálohy a restore musí být ověřeny
na cílové infrastruktuře, nestačí existence dokumentace.

## 3. Cílová architektura první SaaS verze

```text
Zákazník A -> HTTPS erp-a.example.cz -> proxy -> JSP A -> backend A -> DB A
Zákazník B -> HTTPS erp-b.example.cz -> proxy -> JSP B -> backend B -> DB B

Oddělená správa SaaS:
  registr zákazníků, tarif, stav, doména, verze, provisioning, operátorský audit

Společné provozní služby:
  registry obrazů, monitoring, logy, šifrované off-site zálohy, SMTP
```

Zákaznické instance mohou zpočátku sdílet VM nebo PostgreSQL server,
ale to není izolace na úrovni hostitele ani vysoká dostupnost. Každá musí mít:

- Vlastní Compose projekt, interní sítě, DB, omezenou aplikační DB roli,
  tajné údaje a datové svazky. Žádné sdílení vývojové `projects-network`.
- Backend pouze ve své interní síti. Žádná automatická volba DB podle
  klientského `tenant_id`, query parametru nebo neověřené HTTP hlavičky.
- Přesné směrování schválené domény na konkrétní frontend; neznámý Host
  odmítnout. Backend URL frontendu je pevná pro danou instanci.
- Host-only cookies, izolované sessions, klíče, exporty a přístup k zálohám.
  Přihlášení zákazníka A nesmí fungovat u B.
- Samostatnou roli pro migrace; runtime nesmí vytvářet databáze ani měnit
  schéma. Sdílený DB cluster musí omezovat přístupy pomocí rolí i `pg_hba`
  nebo ekvivalentní politiky spravované služby.
- Záznam verze aplikace, schématu a stavu instance v registru SaaS.
- CPU/RAM/DB connection limity a kvóty, aby jeden zákazník nevyčerpal host.

Správa SaaS nemá používat zákaznického administrátora ani vyžadovat HR
záznam operátora. Obchodní data se nekopírují do společného registru.
Technický přístup podpory musí být časově omezený, schválený a auditovaný.

## 4. Backlog, pořadí a akceptační kritéria

P0 = blokuje první placený pilot. P1 = před běžným prodejem a rozšířením
služby. P2 = až podle potřeby a kapacity. Pokud se funkce nabízí zákazníkovi,
její produkční kritéria se stávají povinná bez ohledu na prioritu.

### S0 — Rozsah produktu a rozhodnutí o provozu

**P0 · 3–5 člověkodnů · bez závislosti**

- Sepsat podporované moduly a procesy, limity pilotu, odpovědnost za účetnictví.
- Rozlišit zákazníka SaaS, právní subjekt v ERP a obchodního partnera.
- Vybrat hosting, DNS, registry, region dat, poskytovatele e-mailů a vlastníka provozu.
- Odsouhlasit SLA, RPO/RTO, politiku osobních údajů a model podpory.

**Hotovo:** schválená specifikace pilotu, matice odpovědností a seznam
nepodporovaných funkcí. Nehotové funkce lze vypnout i v backendu.

### S1 — Reprodukovatelný build, runtime a CI

**P0 · 5–8 člověkodnů · závisí na S0**

- Sjednotit Java/Servlet/Tomcat kompatibilitu; otestovat Tomcat 11.
- Přidat CI: backend unit + PostgreSQL integration, build WAR,
  skutečné spuštění Tomcatu a kompilace JSP, smoke test.
- Build obrazů až po úspěšných testech; registry, immutable tag/digest,
  SBOM a kontrola závislostí/obrazů. Produkci nesestavovat na zákaznickém serveru.
- Oddělit dev/staging/prod konfigurace. Runtime non-root, limity prostředků,
  graceful shutdown a healthchecks.
- Aktualizovat neplatné startovací návody a jasně označit K8S manifesty jako
  nepoužívanou alternativu; jejich odstranění není součástí tohoto plánu.

**Hotovo:** čistý checkout v CI vytvoří verifikované obrazy bez ručních kroků;
neprocházející test zablokuje publikaci a release.

### S2 — Produkční síť, HTTPS, secrets a databázová oprávnění

**P0 · 5–8 člověkodnů · závisí na S0, S1**

- Přidat produkční Compose šablonu a HTTPS reverse proxy.
- Externě vystavit jen potřebné HTTPS/HTTP pro TLS přesměrování; DB a API
  nepublikovat veřejně. SSH pouze správcům.
- Secrets mimo Git a obrazy; definovat rotaci a zákaz přihlášení s vývojovými
  DB údaji. Oddělit runtime, migrace a zálohovací oprávnění.
- Nastavit automatickou obnovu TLS, firewall a důvěryhodné proxy hlavičky.
- Staging s oddělenými daty a secrets; nepřenášet produkční osobní údaje
  do vývoje bez anonymizace.

**Hotovo:** externí kontrola najde jen schválené porty; TLS a obnova fungují;
runtime role nemůže číst jinou zákaznickou DB ani měnit schéma.

### S3 — Registr zákazníků a automatický provisioning

**P0 · 5–9 člověkodnů · závisí na S0, S1, S2**

- Minimální oddělená správa SaaS: `tenantId`, slug/doména, kontakt, tarif,
  stav, instance, verze, limity a časové údaje. Bez sdílených ERP dat.
- Idempotentní postup: rezervace identity -> DB/role/secrets -> migrace ->
  konfigurace firmy -> pozvánka administrátora -> start -> healthcheck ->
  zpřístupnění domény.
- Stavový automat s chybou a možností bezpečného opakování; žádný neúspěšný
  provisioning nesmí vypadat jako aktivní zákazník.
- Vyřešit historická demo data bez úprav již aplikovaných Flyway migrací.
  Navrhnout čistý verzovaný bootstrap nebo ověřený baseline nové DB;
  migraci existujících DB testovat zvlášť. Nepoužívat slepé mazání všech dat.
- Nový zákaznický správce pomocí jednorázové expirované pozvánky,
  bez standardního hesla a bez přejímání demo HR údajů.
- Zastavení zákazníka odvolá sessions a zamezí přístupu v backendu, ne jen
  skryje UI. Rušení probíhá podle smluvní retenční politiky.

**Hotovo:** A a B vzniknou stejným automatizovaným postupem; nové instance
obsahují jen systémové číselníky a zákaznickou konfiguraci. Opakování
nezdvojí zdroje a neotevře nehotovou instanci.

### S4 — Identita, oprávnění a ochrana JSP

**P0 · 5–8 člověkodnů · závisí na S1, S2; tenantové testy vyžadují S3**

- Životní cyklus uživatele: pozvání, bezpečný reset hesla, deaktivace,
  revokace sessions; MFA minimálně pro SaaS operátory a tenant administrátory.
- Rate limiting přihlášení, audit změn rolí a přístupová matice backendu.
  HR pozice a oprávnění vyhodnotit odděleně; nové role nesmí vznikat implicitně
  jen z pracovního názvu.
- Secure/HttpOnly/SameSite a host-only session cookies, zákaz session ID
  v URL; testy timeoutu, logoutu a restartu.
- Explicitní CSRF ochrana formulářů a správná práce za HTTPS proxy;
  zachovat a otestovat existující kontrolu Origin.
- Inventura všech servletových cest včetně veřejných stránek a exportů,
  jednotné escapování výstupu a politika CSP slučitelná s inline skripty.
- Chyby přístupu/spojení nezamlčovat; API a UI nemají vracet zdánlivý úspěch.
- Samostatný bezpečnostní přezkum až nad hotovým stagingem a jeho konfigurací.

**Hotovo:** automatické testy rolí, resetu, MFA, cookies a formulářů;
žádný token/cookie zákazníka A neopravňuje přístup do B.

### S5 — Audit, chyby a pozorovatelnost

**P0 · 4–7 člověkodnů · závisí na S1, S2**

- Health/readiness endpointy s DB kontrolou; liveness bez zbytečné závislosti
  na externích službách. Monitoring JSP i backendu.
- Strukturované logy: request ID, instance/tenant, operace; žádná hesla,
  tokeny ani celé osobní či obchodní payloady.
- Metriky latence, chyb, DB poolu, CPU/RAM, disku, počtu sessions a záloh.
- Audit kritických změn napříč moduly: actor, čas, objekt, akce, výsledek.
  Navázat na existující skladovou a fakturační historii.
- Jednotný error kontrakt, srozumitelná JSP hlášení, zákaz stacktrace
  klientům a bezpečný postup po nejasném výsledku zápisu.

**Hotovo:** uměle vyvolaná chyba i nedostupnost služby dorazí určenému
operátorovi do 5 minut; požadavek lze dohledat přes frontend a backend.

### S6 — Dokončení kritických obchodních procesů

**P0 pro prodávané moduly · 8–14 člověkodnů · závisí na S0, S1; audit na S5**

- Zachovat současné příjemky, transakce a ocenění. Doplnit inventury,
  korekce a reversní pohyby s referencí a oprávněním.
- Prodej -> rezervace -> expedice -> výdej -> faktura -> úhrada; jasné
  stavové přechody, žádné záporné zásoby či dvojí výdeje.
- Idempotence a konflikt souběhu také pro platby, vystavení dokladů
  a další finanční/skladové zápisy; znovu použít existující princip příjemek.
- Číselné řady podle firmy, typu a období, neměnné snapshoty partnerů,
  produktů, cen, měny a DPH na vystaveném dokladu.
- Zaokrouhlování, zlomková množství a jednotky potvrdit podle pilotu.
  Současné celočíselné skladové množství není dostačující pro každý retail.
- Fakturaci doplnit o dodavatele/odběratele, IČO/DIČ, adresy, daňové údaje,
  splatnost, opravy/dobropisy a export do dohodnutého účetního systému.
- Netvrdit plnohodnotné účetnictví bez samostatného návrhu účetního deníku,
  závěrek a kontroly účetním.

**Hotovo:** referenční scénáře odsouhlasí zástupce zákazníka a účetní;
testy souběhu a opakování prokážou přesně jeden zápis; skladové i finanční
součty sedí před i po opravách.

### S7 — Zálohy, obnova a ochrana dat

**P0 · 5–9 člověkodnů · závisí na S2, S3**

- Automatické šifrované off-site zálohy, kontrola integrity, alert na
  selhání/stáří zálohy, oddělená oprávnění a přístup ke klíčům.
- Pro cílové RPO 15 minut použít WAL/PITR nebo službu s ekvivalentním
  garantovaným mechanismem. Pouhý denní `pg_dump` tento cíl nesplní.
- Pokud více zákaznických DB sdílí PostgreSQL cluster, PITR se vztahuje
  na cluster: obnovit do izolovaného dočasného clusteru a přenést pouze
  konkrétní DB. Nikdy nevracet celý živý cluster kvůli jednomu zákazníkovi.
- Zálohovat i potřebnou konfiguraci a samostatně uložené soubory.
  Dnešní fakturační přílohy/PDF jsou v DB; objektové úložiště není pro pilot
  automaticky nutné. Změna úložiště vyžaduje konzistentní obnovu obou částí.
- Definovat retenci, export při odchodu, odstranění osobních údajů,
  právní výjimky pro účetní doklady a zánik dat v zálohách.
- Obnova tenant A nesmí přepsat tenant B. Pravidelná restore zkouška,
  nejen hlášení „backup úspěšný“.

**Hotovo:** skutečná obnova do izolovaného prostředí splní RPO <= 15 minut
a RTO <= 4 hodiny na dohodnutém objemu; ověřeny doklady, přílohy a historie.

### S8 — Výkon, kapacity a stabilita

**P0 · 5–8 člověkodnů · závisí na S3, S5, S6**

- Stránkování, filtrování a omezení exportů; odstranit načítání celé agendy
  a N+1 dotazy v kritických přehledech podle měření.
- DB indexy dle reálných plánů, limity poolů/JVM a tenantové kvóty.
- Timeouty a řízení opakování; mutace neopakovat automaticky bez idempotence.
- Zátěž více zákazníků na hostu, detekce „noisy neighbor“, dlouhé exporty
  a PDF mimo synchronní request jen pokud to měření vyžaduje.
- Otestovat nedostupnost DB/SMTP, zaplnění disku, restart procesu a hostu.

**Hotovo:** na odsouhlasených datech a infrastruktuře dosaženy cíle oddílu 6;
zátěž A nezpůsobí neřízené vyčerpání prostředků B.

### S9 — Bezpečný release a provozní postupy

**P0 · 4–7 člověkodnů · závisí na S1, S3, S5, S7**

- Release jednoho kanárkového tenantu, následně dávky; evidence verze každé
  instance, kompatibilita a monitoring po nasazení.
- Flyway spouštět řízeným jednorázovým krokem před aplikací, s migrační rolí.
  Migrace dopředné; expand/contract a test upgrade z předchozího release.
- Rollback obrazu jen při kompatibilním schématu; při nekompatibilní migraci
  rozhodnout fix-forward/restore a dopad na nově zapsaná data.
- První verze může mít oznámené servisní okno a nové přihlášení po restartu
  JSP. Neslibovat bezvýpadkové releasy bez sdílených sessions a ověření.
- Runbook pro výpadek, restore, rotaci secrets, incident, kapacitu a odchod
  zákazníka. Zajistit provoz i bez jediného konkrétního vývojáře.

**Hotovo:** jiný operátor provede provisioning, release i obnovu pouze podle
runbooku; neúspěšný canary zastaví rollout ostatních zákazníků.

### S10 — SaaS komerce, podpora a smluvní provoz

**P0 minimum; P1 automatizace · 5–8 člověkodnů · závisí na S0, S3, S5, S7**

- Tarif, počty uživatelů/moduly, kvóty, trial, aktivace, pozastavení a ukončení.
- První pilot může mít ruční vyúčtování SaaS předplatného a operátorské
  založení zákazníka. Zákaznické ERP faktury nejsou SaaS billing.
- Před širším prodejem automatizovat billing, upomínky a zákaznický portál;
  zpracování webhooků idempotentní a oddělené od obchodních dat ERP.
- Smlouva, DPA/GDPR, seznam subdodavatelů, region dat, SLA, retenční politika,
  kontakty podpory a postup při incidentu; právní odsouhlasení.
- Onboarding, import/export dat, školení, záznam zákaznických souhlasů
  a schvalovaný přístup podpory.

**Hotovo:** lze zákazníka přijmout, vyúčtovat, podpořit, pozastavit a ukončit
bez ručního neauditovaného zásahu do obchodní DB.

### S11 — End-to-end akceptace a placený pilot

**P0 · 3–5 člověkodnů + 2–4 týdny sledovaného pilotu · závisí na S0–S10**

- E2E: login/role, katalog, nákup, částečná příjemka, skladový výdej,
  podporovaná fakturace, export a případné opravy.
- Dva izolovaní zákazníci: kopie tokenu/cookie, cizí UUID, exporty/přílohy,
  pozvánky, reset hesla, směrování, zálohy a přístup podpory.
- UAT s reprezentativními importovanými daty; pilot se známými limity.
- Uzavřít bezpečnostní a provozní přezkum, účetní ověření, kapacity a runbooky.
- Schválené go/no-go; jasný rollback a komunikační plán.

**Hotovo:** všechny P0 brány splněny; žádné nevyřešené kritické či závažné
nálezy v rozsahu prodávané služby; pilot má vlastníka a proces incidentů.

## 5. Co nedělat před prvním pilotem

- Nepřepisovat všechny moduly ani více frontendů současně.
- Nepřidávat Kubernetes, mikroservisy nebo sdílenou multi-tenant DB jen
  kvůli označení „SaaS“.
- Nepožadovat hned centrální SSO, Redis, message broker nebo objektové
  úložiště, pokud konkrétní provozní potřeba nevyžaduje jejich zavedení.
- Nezapínat neověřenou platební metodu CARD jako reálnou platbu.
- Nechápat existující Compose volumes jako zálohu a DB jako audit sama o sobě.
- Neměnit již aplikované migrační soubory kvůli odstranění demo dat.

**P1/P2 po pilotu:** automatický billing a self-service, federované SSO,
pokročilé reporty, další moduly, další VM/HA, horizontální škálování JSP se
sdílenými sessions a případně skutečný veřejný e-shop.

Pokud se e-shop prodává už při prvním spuštění, přidat samostatný povinný
balík: ověřená platební brána/webhooky, rezervace a expirace zásob, expedice,
refundace, reklamace, kontaktní a doručovací údaje, notifikace a legislativa.
Tento balík není zahrnut do níže uvedeného základního odhadu.

## 6. Navržené měřitelné produkční brány

| Brána | Návrh podmínky pro schválení |
| --- | --- |
| Izolace | Dva zákazníci projdou negativními testy přihlášení, CRUD, exportů, příloh, resetů a obnov; DB role A nemá přístup k DB B. |
| Build | Každý release má testované obrazy, přesný digest, výsledky CI a verzi migrací. |
| Bootstrap | Čistý tenant bez demo dat, standardního účtu nebo společných secrets; opakovaný provisioning bezpečný. |
| Transakce | Souběžný a opakovaný kritický zápis nemění zásoby nebo úhrady dvakrát; konflikty explicitní. |
| Obnova | RPO <= 15 minut, RTO <= 4 hodiny; skutečná restore zkouška včetně příloh a klíčů. |
| Alerting | Detekce testovacího výpadku, zaplnění disku a neúspěšné zálohy do 5 minut. |
| Zátěž pilotu | Výchozí návrh: 2 tenanty současně, každý 25 aktivních uživatelů, 100 tisíc produktů a 1 milion skladových pohybů. Potvrdit s pilotem a připravit realistická data. |
| Latence | Za této smíšené zátěže p95 běžných API čtení <= 500 ms, zápisů <= 1 s, celého JSP requestu <= 2 s; exporty/PDF vyhodnocovat samostatně. Chyby 5xx < 0,5 %, žádné ztracené zápisy. |
| Stabilita | Zátěž alespoň 60 minut plus test restartu a chyb závislostí; explicitní timeouty a žádné falešné úspěchy. |
| Dostupnost | Pro první verzi návrh 99,5 % měsíčně; smluvně určit metodiku a servisní okna. Vyšší SLA vyžaduje architekturu HA a nový rozpočet. |
| Release | Kanárkové nasazení a testovaný rollback/fix-forward; při změně schématu ověřena kompatibilita. |
| Odpovědnost | Jmenovaný provozní vlastník, podpora, schválené smluvní podmínky a rozsah účetní funkce. |

Tyto hodnoty nejsou naměřené parametry současného ERP ani závazná SLA.
Odsouhlasit v S0; změna cíle může změnit architekturu, cenu i harmonogram.

## 7. Odhad a realizace

Základní balíky S0–S11: **62–104 člověkodnů** technické a produktové práce.
Jde o orientační odhad, nikoli závaznou nabídku; zahrnuje minimum SaaS
komerce, ne plnou automatizaci billingu, celý e-shop, plné účetnictví
ani dokončení všech ostatních ERP modulů.

Pro dva zkušené vývojáře s dostupným DevOps/QA, účetním a produktovým
vlastníkem plánovat přibližně **12–20 kalendářních týdnů včetně rezervy
a sledovaného pilotu**. U jednoho vývojáře spíše **4–7 měsíců** podle
provozních a obchodních požadavků. Upřesnit po S0 a první migrační zkoušce.

Pořadí kritické cesty:

1. S0 -> S1 -> S2 -> S3: rozsah, reprodukovatelný runtime a izolovaný tenant.
2. Paralelně S4/S5/S6/S7: identita, audit, obchodní procesy a obnovitelnost.
3. S8/S9/S10: kapacity, release a provoz SaaS.
4. S11: akceptace dvou zákazníků a řízený pilot.

První sprint má dodat schválený scope, kompatibilní Tomcat/WAR, zelené CI
a návrh produkční Compose šablony. Druhý milník je automatické založení
dvou izolovaných zákazníků bez demo dat. **Další zákazníky neprodávat před
úspěšnou obnovou a testem jejich vzájemné izolace.**
