# Architektura `erp-base-frontend` (legacy JSP)

Tento projekt je Java Servlet/JSP WAR frontend na Tomcatu. Nejde o Spring
Boot backend: je to samostatná webová aplikace, která drží uživatelskou
`HttpSession`, vykresluje JSP a volá `erp-backend` přes Java `HttpClient`.

## Komponentní diagram

```mermaid
flowchart TB
    Browser["Prohlížeč<br/>HTML formuláře + assets"]
    Tomcat["Tomcat 10.1 / Java 21"]
    WAR["erp-base-frontend.war"]

    subgraph Web["Servlet/JSP aplikace"]
        Filter["SessionFilter<br/>přístup, session, Origin,<br/>modulové read-checky"]
        Login["LoginServlet"]
        Pages["AppPageServlet"]
        ModuleServlets["Modulové HttpServlety<br/>admin, HR, sklad,<br/>nákup, prodej..."]
        Requests["BackendRequests<br/>Bearer token context"]
        JSP["JSP views<br/>WEB-INF/views"]
        Static["public assets<br/>CSS / JavaScript"]
        HttpClient["Java HttpClient<br/>JSON a binární odpovědi"]
    end

    Backend["erp-backend<br/>Spring REST :8080"]
    DB[("PostgreSQL")]

    Browser -->|"HTTP, formuláře"| Tomcat
    Tomcat --> WAR
    WAR --> Filter
    Filter -->|"HttpSession + kontrola tokenu"| Pages
    Filter --> Login
    Filter --> ModuleServlets
    Pages --> ModuleServlets
    ModuleServlets --> Requests
    Requests --> HttpClient
    HttpClient -->|"HTTP /api/v1/*<br/>Bearer token"| Backend
    ModuleServlets --> JSP
    JSP -->|"HTML response"| Browser
    Static -.-> Browser
    Backend --> DB
```

## Požadavek modulu

1. Tomcat předá HTTP požadavek anotovanému servletu přes `SessionFilter`.
2. Filtr nastaví UTF-8 a bezpečnostní/no-cache hlavičky, odmítne mutaci bez
   stejného Originu, kontroluje session expiraci a chráněné cesty.
3. Pro GET do modulu provede read-check přes odpovídající API endpoint; při
   odpovědi `403` přesměruje uživatele do launcheru.
4. Token uložený v `HttpSession` se předá přes `BackendRequests` do servletu.
   Servlet použije Java `HttpClient` pro JSON API požadavek a vloží výsledek
   do request atributů.
5. Servlet forwarduje do JSP v `WEB-INF/views`; JSP vytváří HTML stránky a
   formuláře. POST servlet validuje/mapuje a zapíše přes backend, poté obvykle
   vrací redirect s feedbackem.

## Balíčky a provozní model

- `base`: session/access filter, login/logout a launcher.
- Jednotlivé Servlet třídy pokrývají administraci, účetnictví, CRM, HR,
  sklad, nákup, prodej, katalog/eCommerce, helpdesk, projekty a další moduly.
- JSP šablony a assety zůstávají uvnitř WAR; v prohlížeči není samostatná
  React aplikace ani browser-to-backend token.
- Backend URL se čte z `BACKEND_URL` (výchozí `http://localhost:8080`).
  Přílohy a PDF mohou být přenášeny jako binární HTTP response.

Build vytváří `erp-base-frontend.war` pomocí Maven/Java 21 a kopíruje jej jako
ROOT aplikaci do Tomcat 10.1. V `docker-compose-JSP.yml` je host port `4201`
mapován na Tomcat `8080`; backend je na `8080` a PostgreSQL na `5434`.

> Historická poznámka: README uvnitř projektu může popisovat jiný stav než
> jeho aktuální Maven WAR/Servlet zdrojový kód. Tento diagram vychází z
> `pom.xml`, `Dockerfile`, `web.xml` a aktuálního Java/JSP stromu.
