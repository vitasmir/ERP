<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="fragments/base-header.jspf" %>
<style>.module-access-error{margin:0 0 24px;padding:18px 22px;border:1px solid #d34b4b;background:#fff0f0;color:#c62828;font-size:18px;font-weight:700}</style>
<% java.util.Set<String> allowedModules = (java.util.Set<String>) request.getAttribute("allowedModules"); %>
<% if (request.getParameter("error") != null) { %><div class="page-alert error-alert module-access-error" role="alert"><%= escapeHtml(request.getParameter("error")) %></div><% } %>
<section class="launcher-head"><div><span class="eyebrow">ERP WORKSPACE</span><h2>Vše, co vaše firma potřebuje.</h2><p>Vyberte aplikaci a začněte pracovat. Každý modul sdílí stejná data a oprávnění.</p></div><div class="launcher-stat"><strong><%= allowedModules == null ? 0 : allowedModules.size() %></strong><span>aplikací<br>v katalogu</span></div></section>
<section class="launcher-tools"><div class="launcher-search"><span>⌕</span><input id="module-search" placeholder="Hledat aplikaci nebo funkci..." aria-label="Hledat aplikaci nebo funkci"></div><div class="launcher-filter"><button class="module-filter active" data-filter="all">Vše</button><button class="module-filter" data-filter="core">Základ</button><button class="module-filter" data-filter="operations">Provoz</button><button class="module-filter" data-filter="sales">Obchod</button></div></section>
<section class="module-grid" id="module-grid">
<% if (allowedModules != null) { %>
<% if (allowedModules.contains("base")) { %>
  <button class="module-tile" data-module="base" data-category="core" data-title="Base" data-subtitle="Uživatelé, role a společnosti" data-description="Centrální správa ERP instance. Nastavte, kdo má přístup ke kterým datům a modulům."><span class="module-icon icon-base">E</span><b>Base</b><small>Správa systému</small></button>
<% } if (allowedModules.contains("accounting")) { %>
  <button class="module-tile" data-module="accounting" data-category="core" data-title="Účetnictví" data-subtitle="Finance a doklady" data-description="Účetní agenda, faktury, bankovní pohyby a finanční přehledy na jednom místě."><span class="module-icon icon-accounting">∿</span><b>Účetnictví</b><small>Finance a doklady</small></button>
<% } if (allowedModules.contains("crm")) { %>
  <button class="module-tile" data-module="crm" data-category="sales" data-title="CRM" data-subtitle="Příležitosti a zákazníci" data-description="Sledujte obchodní příležitosti od prvního kontaktu až po uzavřenou objednávku."><span class="module-icon icon-crm">◆</span><b>CRM</b><small>Obchodní vztahy</small></button>
<% } if (allowedModules.contains("sales")) { %>
  <button class="module-tile" data-module="sales" data-category="sales" data-title="Prodej" data-subtitle="Nabídky a objednávky" data-description="Tvorba nabídek, objednávek a cenových pravidel pro zákazníky."><span class="module-icon icon-sales">▥</span><b>Prodej</b><small>Nabídky a objednávky</small></button>
<% } if (allowedModules.contains("purchase")) { %>
  <button class="module-tile" data-module="purchase" data-category="operations" data-title="Nákup" data-subtitle="Dodavatelé a nákupní objednávky" data-description="Řiďte dodavatele, poptávky, nákupní objednávky a příjem zboží."><span class="module-icon icon-purchase">═</span><b>Nákup</b><small>Dodavatelský řetězec</small></button>
<% } if (allowedModules.contains("inventory")) { %>
  <button class="module-tile" data-module="inventory" data-category="operations" data-title="Sklad" data-subtitle="Zásoby a pohyb zboží" data-description="Stav zásob, skladové lokace, příjem, výdej a inventury v reálném čase."><span class="module-icon icon-inventory">⬡</span><b>Sklad</b><small>Zásoby a logistika</small></button>
<% } if (allowedModules.contains("manufacturing")) { %>
  <button class="module-tile" data-module="manufacturing" data-category="operations" data-title="Výroba" data-subtitle="Plánování a výrobní příkazy" data-description="Plánujte výrobu, kusovníky, pracovní postupy a spotřebu materiálu."><span class="module-icon icon-manufacturing">▰</span><b>Výroba</b><small>Výrobní plánování</small></button>
<% } if (allowedModules.contains("promotions")) { %>
  <button class="module-tile" data-module="promotions" data-category="sales" data-title="Promo kampaně" data-subtitle="Akce a letákové nabídky" data-description="Plánujte promo akce, alokujte zboží do prodejen a sledujte prodeje i příspěvky dodavatelů."><span class="module-icon icon-promotions">✦</span><b>Promo</b><small>Akce a kampaně</small><i class="module-badge">MVP</i></button>
<% } if (allowedModules.contains("pos")) { %>
  <button class="module-tile" data-module="pos" data-category="sales" data-title="Pokladna" data-subtitle="Prodej na prodejně" data-description="Rychlé pokladní rozhraní, směny, platby a denní uzávěrky pro prodejny."><span class="module-icon icon-pos">▤</span><b>Pokladna</b><small>Prodejna</small></button>
<% } if (allowedModules.contains("hr")) { %>
  <button class="module-tile" data-module="hr" data-category="core" data-title="Lidé" data-subtitle="Zaměstnanci a docházka" data-description="Evidence zaměstnanců, týmů, pracovních rolí, dovolených a přístupů."><span class="module-icon icon-hr">●</span><b>Lidé</b><small>HR a docházka</small></button>
<% } if (allowedModules.contains("documents")) { %>
  <button class="module-tile" data-module="documents" data-category="core" data-title="Dokumenty" data-subtitle="Soubory a schvalování" data-description="Ukládejte dokumenty u záznamů, nastavte schvalovací kroky a najděte vše během okamžiku."><span class="module-icon icon-documents">▣</span><b>Dokumenty</b><small>Soubory a procesy</small></button>
<% } if (allowedModules.contains("project")) { %>
  <button class="module-tile" data-module="project" data-category="operations" data-title="Projekty" data-subtitle="Úkoly a termíny" data-description="Rozdělte práci do projektů, úkolů a milníků. Sledujte odpovědnosti i termíny."><span class="module-icon icon-project">◢</span><b>Projekty</b><small>Úkoly a plánování</small></button>
<% } if (allowedModules.contains("helpdesk")) { %>
  <button class="module-tile" data-module="helpdesk" data-category="operations" data-title="Helpdesk" data-subtitle="Požadavky a podpora" data-description="Přijímejte požadavky, přiřazujte je týmům a dodržujte SLA vůči interním i externím zákazníkům."><span class="module-icon icon-helpdesk">＋</span><b>Helpdesk</b><small>Podpora a SLA</small></button>
<% } if (allowedModules.contains("website")) { %>
  <button class="module-tile" data-module="website" data-category="sales" data-title="Web" data-subtitle="Veřejný web a obsah" data-description="Spravujte obsah webu, katalog a zákaznické formuláře napojené na ERP data."><span class="module-icon icon-website">◓</span><b>Web</b><small>Obsah a katalog</small></button>
<% } if (allowedModules.contains("ecommerce")) { %>
  <button class="module-tile" data-module="ecommerce" data-category="sales" data-title="eCommerce" data-subtitle="Katalog a online obchod" data-description="Navrhněte úvodní stránku, spravujte kategorie a ověřte skladovou dostupnost i doručení."><span class="module-icon icon-ecommerce">◫</span><b>eCommerce</b><small>Online obchod</small><i class="module-badge">NOVÉ</i></button>
<% } if (allowedModules.contains("marketing")) { %>
  <button class="module-tile" data-module="marketing" data-category="sales" data-title="Marketing" data-subtitle="Kampaně a komunikace" data-description="Segmentujte zákazníky, připravujte kampaně a měřte jejich dopad na obchod."><span class="module-icon icon-marketing">➤</span><b>Marketing</b><small>Kampaně a e-maily</small></button>
<% } if (allowedModules.contains("planning")) { %>
  <button class="module-tile" data-module="planning" data-category="operations" data-title="Plánování" data-subtitle="Směny a kapacity" data-description="Plánujte směny, zdroje a kapacity týmů tak, aby provoz držel krok s poptávkou."><span class="module-icon icon-planning">◀</span><b>Plánování</b><small>Směny a kapacity</small></button>
<% } if (allowedModules.contains("dashboard")) { %>
  <button class="module-tile" data-module="dashboard" data-category="core" data-title="Dashboard" data-subtitle="Přehledy a KPI" data-description="Sestavte si pracovní přehled z klíčových ukazatelů napříč všemi moduly ERP."><span class="module-icon icon-dashboard">▦</span><b>Dashboard</b><small>KPI a reporting</small></button>
<% } } %>
</section><p class="module-empty" id="module-empty">Žádná aplikace neodpovídá hledání.</p>
<aside class="module-drawer" id="module-drawer" aria-hidden="true"><button class="drawer-close" id="drawer-close" aria-label="Zavřít detail">×</button><span class="drawer-icon module-icon"></span><span class="eyebrow">APLIKACE ERP</span><h2 id="drawer-title"></h2><p class="drawer-subtitle" id="drawer-subtitle"></p><p id="drawer-description"></p><div class="drawer-status"><span class="online"></span><span>Modul připraven k návrhu</span></div><div class="drawer-section"><span class="eyebrow">CO BUDE OBSAHOVAT</span><ul><li>Role a oprávnění podle pracovních týmů</li><li>Seznamy, formuláře a filtrování záznamů</li><li>Napojení na společná ERP data</li><li>Auditní stopa a přehledy výkonu</li></ul></div><button class="primary drawer-action" id="drawer-action">Otevřít modul <span>→</span></button></aside>
<%@ include file="fragments/base-footer.jspf" %>
<%!
  private String escapeHtml(String value) {
    if (value == null) return "";
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;");
  }
%>