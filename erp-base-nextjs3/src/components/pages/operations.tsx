import { Fragment, type CSSProperties } from "react";
import type { ViewProps } from "../shared";
import { InlinePageScript, list, record, text, value } from "../shared";
import type { Row } from "../../lib/context";

const get = (row: Row, key: string, fallback = "") => value(row, key, fallback);
const rows = (row: Row, key: string) => list(row[key]);
const isTrue = (row: Row, key: string) => row[key] === true || get(row, key) === "1";
const hiddenInput = (name: string, val: string) => <input type="hidden" name={name} value={val} />;
const strings = (input: unknown): string[] => Array.isArray(input)
  ? (input as unknown[]).map((item) => text(item)).filter(Boolean) : [];

function Messages({ data, className }: { data: Row; className: string }) {
  return <>
    {[get(data, "error"), get(data, "actionError")].filter(Boolean).map((message, index) =>
      <p className={`${className} error`} role="alert" key={`${index}-${message}`}>{message}</p>)}
    {get(data, "message") && <p className={className} role="status">{get(data, "message")}</p>}
  </>;
}

function EmployeeForm({ employee, data }: { employee: Row | null; data: Row }) {
  const overview = record(data.overview);
  const suffix = employee ? get(employee, "id", "new") : "new";
  const roles = rows(data, "roleOptions");
  const selectedRole = roles.some((role) => get(role, "name") === get(employee ?? {}, "jobTitle"))
    ? get(employee ?? {}, "jobTitle") : get(employee ?? {}, "userRoleName");
  return <form method="post" className="workforce-form">
    {hiddenInput("action", employee ? "update" : "create")}
    {employee && hiddenInput("id", get(employee, "id"))}
    <label>Jméno a příjmení<input name="fullName" maxLength={200} defaultValue={get(employee ?? {}, "fullName")} required /></label>
    <label>Tým<select name="teamId" id={`team-${suffix}`} defaultValue={get(employee ?? {}, "teamId")} required>
      {!employee && <option value="" disabled>Vyberte tým</option>}
      {rows(overview, "teams").map((team) => <option key={get(team, "id")} value={get(team, "id")} data-team-name={get(team, "name")}>{get(team, "name")}</option>)}
    </select></label>
    <label>Pracovní role<select name="jobTitle" id={`job-title-${suffix}`} defaultValue={employee ? selectedRole : ""} required>
      {!employee && <option value="" disabled>Vyberte roli</option>}
      {roles.map((role) => <option key={get(role, "name")} value={get(role, "name")}>{get(role, "name")}</option>)}
    </select></label>
    <label>Nástup<input type="date" name="employmentStartDate" defaultValue={get(employee ?? {}, "employmentStartDate")} required /></label>
    <label>Zástupce<select name="deputyEmployeeId" defaultValue={get(employee ?? {}, "deputyEmployeeId")} data-deputy-role-for={`job-title-${suffix}`} data-deputy-team-for={`team-${suffix}`} required>
      <option value="">Vyberte zástupce</option>
      {rows(overview, "employees").filter((deputy) => get(deputy, "id") !== get(employee ?? {}, "id")).map((deputy) =>
        <option key={get(deputy, "id")} value={get(deputy, "id")} data-user-role={get(deputy, "userRoleName")} data-team-id={get(deputy, "teamId")} data-team-name={get(deputy, "teamName")}>{get(deputy, "fullName")}</option>)}
    </select></label>
    <button type="submit" className={employee ? "secondary" : "primary"}>{employee ? "Uložit profil" : "Založit zaměstnance"}</button>
  </form>;
}

const hrScript = `(() => {
  const employeeRows = document.querySelectorAll('[data-employee-team-id]');
  const filterEmployees = (teamId) => {
    let count = 0;
    employeeRows.forEach((row) => {
      row.hidden = Boolean(teamId) && row.dataset.employeeTeamId !== teamId;
      if (!row.hidden && row.classList.contains('employee-record')) count++;
    });
    const counter = document.getElementById('employee-count');
    if (counter) counter.textContent = count + ' zaměstnanci';
    document.getElementById('employees')?.scrollIntoView({ behavior: 'smooth' });
  };
  document.querySelectorAll('[data-team-filter]').forEach((button) => button.addEventListener('click', () => filterEmployees(button.dataset.teamFilter || '')));
  document.querySelectorAll('[data-show-all-employees]').forEach((button) => button.addEventListener('click', () => filterEmployees('')));
  const normalized = (value) => value.normalize('NFD').replace(/[\\u0300-\\u036f]/g, '').trim().toLowerCase();
  document.querySelectorAll('[data-deputy-role-for]').forEach((select) => {
    const role = document.getElementById(select.dataset.deputyRoleFor);
    const team = document.getElementById(select.dataset.deputyTeamFor);
    if (!role || !team) return;
    const filter = () => {
      let count = 0;
      const teamName = normalized(team.selectedOptions[0]?.dataset.teamName || '');
      [...select.options].forEach((option) => {
        const sameTeam = option.dataset.teamId === team.value || normalized(option.dataset.teamName || '') === teamName;
        const matches = !option.value || normalized(option.dataset.userRole || '') === normalized(role.value) && sameTeam;
        option.hidden = !matches; option.disabled = !matches;
        if (matches && option.value) count++;
      });
      select.required = count > 0;
      if (select.selectedOptions[0]?.disabled) select.value = '';
    };
    role.addEventListener('change', filter); team.addEventListener('change', filter); filter();
  });
})();`;

export function HrPage({ data }: ViewProps) {
  const overview = record(data.overview);
  const employees = rows(overview, "employees");
  const teams = rows(overview, "teams");
  const roleOptions = rows(data, "roleOptions");
  const edit = isTrue(data, "edit");
  const admin = isTrue(data, "admin");
  const availability = data.availability === null || data.availability === undefined ? null : record(data.availability);
  const selectedEmployeeId = get(data, "selectedEmployeeId");
  return <main className="hr-page">
    <header className="hr-header"><a href="/apps" className="back-link">← Aplikace</a><div><span className="eyebrow">PEOPLE / HR</span><h1>Lidé</h1></div><a href="#employees" className="primary">Přehled týmu</a></header>
    <Messages data={data} className="hr-message" />
    <section className="hr-metrics">
      <article><span>Aktivní zaměstnanci</span><strong>{get(overview, "activeEmployeeCount", "-")}</strong><small>v evidovaných týmech</small></article>
      <article><span>Nástupy</span><strong>{get(overview, "onboardingCount", "-")}</strong><small>čekají na aktivaci</small></article>
      <article><span>Týmy</span><strong>{get(overview, "teamCount", "-")}</strong><small>napříč organizací</small></article>
    </section>
    {edit && <>
      <section className="workforce-section"><h2>Týmy</h2>
        <form method="post" className="workforce-form">{hiddenInput("action", "createTeam")}<label>Nový tým<input name="name" maxLength={150} required /></label><button className="primary" type="submit">Založit tým</button></form>
        <div className="team-list">{teams.map((team) => <div className="team-row" key={get(team, "id")}>
          <form method="post">{hiddenInput("action", "updateTeam")}{hiddenInput("teamId", get(team, "id"))}<input name="name" defaultValue={get(team, "name")} maxLength={150} required />
            <span className="team-member-count">({employees.filter((employee) => get(employee, "teamId") === get(team, "id")).length})</span><button className="secondary" type="submit">Přejmenovat</button></form>
          <form method="post">{hiddenInput("action", "deleteTeam")}{hiddenInput("teamId", get(team, "id"))}<button className="secondary danger-action" type="submit">Smazat</button></form>
          <button className="secondary team-filter-button" type="button" data-team-filter={get(team, "id")}>Zobrazit členy týmu</button>
          <button className="secondary team-filter-button" type="button" data-show-all-employees>Zobrazit všechny zaměstnance</button>
        </div>)}</div>
      </section>
      <section className="workforce-section"><h2>Nový zaměstnanec</h2><EmployeeForm employee={null} data={data} /></section>
    </>}
    <section id="employees" className="hr-section">
      <div className="section-head"><div><span className="eyebrow">ZAMĚSTNANCI</span><h2>Organizace a nástupy</h2></div><span className="employee-count" id="employee-count">{employees.length} zaměstnanci</span></div>
      <div className="employee-table-wrap"><table className="employee-table">
        <thead><tr><th>STAV</th><th>ZAMĚSTNANEC</th><th>PRACOVNÍ ROLE</th><th>TÝM</th><th>ZÁSTUPCE</th><th>NÁSTUP</th><th>AKTIVACE</th><th>DOSTUPNOST</th><th>ÚČET</th></tr></thead>
        <tbody>{employees.map((employee) => {
          const employeeName = get(employee, "fullName");
          const nameParts = employeeName.split(" ").filter(Boolean);
          const initials = (nameParts.length > 1 ? `${nameParts[0][0]}${nameParts.at(-1)?.[0] ?? ""}` : employeeName.slice(0, 2)).toUpperCase();
          const role = roleOptions.find((candidate) => get(candidate, "name") === get(employee, "jobTitle")) ?? {};
          const status = get(employee, "status");
          return <Fragment key={get(employee, "id")}>
            <tr className="employee-record" data-employee-team-id={get(employee, "teamId")}>
              <td><span className={`status-chip status-${status.toLowerCase()}`}>{status === "ONBOARDING" ? "NÁSTUP" : status === "ACTIVE" ? "AKTIVNÍ" : "NEAKTIVNÍ"}</span></td>
              <td><div className="employee-main"><span className="employee-avatar">{initials}</span><strong>{employeeName}</strong></div></td>
              <td><span className="employee-role-mark" style={{ "--role-color": get(role, "color", "#D9ED62") } as CSSProperties}>{get(role, "initial", get(employee, "jobTitle", "?").slice(0, 1).toUpperCase())}</span>{get(employee, "jobTitle")}</td>
              <td>{get(employee, "teamName")}</td><td>{get(employee, "deputyName")}</td><td>{get(employee, "employmentStartDate")}</td>
              <td className="employee-action-cell">{edit && <form method="post">{hiddenInput("id", get(employee, "id"))}{hiddenInput("action", status === "ACTIVE" ? "deactivate" : "activate")}
                {status === "ACTIVE" || get(employee, "employmentStartDate") <= new Date().toISOString().slice(0, 10)
                  ? <button className="secondary" type="submit">{status === "ACTIVE" ? "Ukončit pracovní poměr" : "Aktivovat nástup"}</button>
                  : <button className="secondary" type="button" disabled title="Aktivace bude možná po datu nástupu">Aktivovat po nástupu</button>}</form>}</td>
              <td className="employee-action-cell"><a href={`/hr?employeeId=${encodeURIComponent(get(employee, "id"))}#availability`}>Dostupnost a kvalifikace</a></td>
              <td className="employee-action-cell">{admin && <a href={isTrue(employee, "hasUserAccount") ? "/users" : `/users?employeeId=${encodeURIComponent(get(employee, "id"))}`}>{isTrue(employee, "hasUserAccount") ? "Účet v Uživatelích" : "Vytvořit účet"}</a>}</td>
            </tr>
            {edit && <tr className="employee-edit-row" data-employee-team-id={get(employee, "teamId")}><td colSpan={9}><details className="workforce-edit"><summary>Upravit zaměstnance</summary><EmployeeForm employee={employee} data={data} /></details></td></tr>}
          </Fragment>;
        })}</tbody>
      </table></div>
    </section>
    {availability && <section id="availability" className="workforce-section">
      <h2>Dostupnost a kvalifikace</h2>{employees.filter((employee) => get(employee, "id") === selectedEmployeeId).map((employee) => <h3 key={get(employee, "id")}>{get(employee, "fullName")}</h3>)}
      <h3>Kvalifikace</h3><ul>{strings(availability.qualifications).map((qualification, index) => <li key={`${index}-${qualification}`}>{qualification}</li>)}</ul>
      {edit && <form method="post" className="workforce-form">{hiddenInput("action", "qualification")}{hiddenInput("id", selectedEmployeeId)}<label>Pracovní role<input name="roleName" maxLength={150} required /></label><button type="submit" className="secondary">Přidat kvalifikaci</button></form>}
      <h3>Absence</h3>{rows(availability, "absences").length ? rows(availability, "absences").map((absence) => <div className="workforce-event" key={get(absence, "id")}>
        <p>{get(absence, "startAt")} – {get(absence, "endAt")} / {get(absence, "reason")}</p>{edit && <form method="post">{hiddenInput("action", "removeAbsence")}{hiddenInput("id", selectedEmployeeId)}{hiddenInput("absenceId", get(absence, "id"))}<button type="submit" className="secondary">Zrušit absenci</button></form>}
      </div>) : <p>Žádné absence.</p>}
      {edit && <form method="post" className="workforce-form">{hiddenInput("action", "absence")}{hiddenInput("id", selectedEmployeeId)}<label>Od<input type="datetime-local" name="startAt" required /></label><label>Do<input type="datetime-local" name="endAt" required /></label><label>Důvod<input name="reason" maxLength={240} required /></label><button type="submit" className="secondary">Zapsat absenci</button></form>}
    </section>}
    <InlinePageScript source={hrScript} />
  </main>;
}

const shopInlineStyles = `@media(min-width:1200px){.shop-products{grid-template-columns:repeat(4,minmax(0,1fr));gap:12px}.shop-product{padding:12px;grid-template-columns:74px 1fr;gap:10px;min-height:220px}.shop-product-image,.product-placeholder{width:74px;height:100px}.product-info h3{font-size:15px}.product-info p{font-size:10px}.product-buy{gap:7px;flex-wrap:wrap}.product-buy strong{font-size:17px}.stock-label{width:100%;margin-right:0}.product-buy form,.add-button{width:100%}}`;

export function ShopPage({ data }: ViewProps) {
  const shop = data.shop == null ? null : record(data.shop);
  const categories = rows(shop ?? {}, "categoryOptions");
  const products = rows(shop ?? {}, "products");
  const cart = rows(shop ?? {}, "cart");
  const selectedCategoryId = get(shop ?? {}, "selectedCategoryId");
  const delivery = record(shop?.delivery);
  return <>
    <style>{shopInlineStyles}</style>
    <header className="shop-header"><a className="shop-brand" href="/eshop"><span className="brand-mark">A</span><span><strong>ALL MARKET</strong><small>lokální nákup bez čekání</small></span></a>
      <nav><a href="#catalog">Katalog</a><a href="#delivery">Doručení</a><a className="admin-link" href="/ecommerce">Administrace</a></nav><a className="cart-jump" href="#cart">Košík <b>{get(shop ?? {}, "cartCount", "0")}</b></a>
    </header>
    <main className="shop-shell">
      {get(data, "error") && <p className="shop-error" role="alert">{get(data, "error")}</p>}
      {!shop ? <section className="shop-error">Katalog se nepodařilo načíst.</section> : <>
        <section className="shop-hero"><div><span className="kicker">DNEŠNÍ VÝBĚR</span><h1>Dobré věci<br /><em>na dosah ruky.</em></h1><p>Čerstvé produkty z vašich oblíbených prodejen. Vyberte kategorii, sestavte košík a zvolte způsob doručení.</p><a className="hero-button" href="#catalog">Prohlédnout nabídku <span>↓</span></a></div><div className="hero-stamp"><strong>24 h</strong><span>rychlé doručení<br />domů nebo do boxu</span></div></section>
        <section className="store-layout" id="catalog"><aside className="category-nav"><div className="eyebrow">KATALOG</div><h2>Co hledáte?</h2>
          <a className={`category-filter ${selectedCategoryId ? "" : "active"}`} href="/eshop">Všechny produkty <span>{get(shop, "totalProductCount")}</span></a>
          {categories.map((category) => <a key={get(category, "id")} className={`category-filter ${get(category, "id") === selectedCategoryId ? "active" : ""}`} style={{ paddingLeft: `${14 + Number(get(category, "depth", "0")) * 16}px` }} href={`/eshop?categoryId=${encodeURIComponent(get(category, "id"))}`}>{get(category, "name")} <span>({get(category, "productCount")})</span></a>)}
          <div className="category-note" id="delivery"><strong>Doručení podle vás</strong><p>Domů od zítřka<br />Box od pozítří</p></div>
        </aside>
        <section className="catalog-area"><div className="catalog-heading"><div><span className="eyebrow">FRESH / LOCAL / TODAY</span><h2>Produkty pro každý den</h2></div><span className="result-count" id="result-count">{products.length} produktů</span></div>
          <div className="shop-products">{products.map((product) => { const available = Number(get(product, "availableQuantity", "0")); return <article className="shop-product" data-product-card data-category-id={get(product, "categoryId", "uncategorized")} key={get(product, "id")}>
            {get(product, "imageUrl") ? <img className="shop-product-image" src={get(product, "imageUrl")} alt={get(product, "name")} /> : <div className="product-placeholder"><span>FM</span></div>}
            <div className="product-info"><span className="product-sku">{get(product, "sku")}</span><h3>{get(product, "name")}</h3><p>{get(product, "description")}</p></div>
            <div className="product-buy"><div><strong>{get(product, "price")} Kč</strong><small>/ {get(product, "unit")}</small></div><span className="stock-label">{get(product, "availableQuantity")} {get(product, "unit")} skladem</span>
              <form method="post">{selectedCategoryId && hiddenInput("categoryId", selectedCategoryId)}{hiddenInput("action", "add")}{hiddenInput("productId", get(product, "id"))}{hiddenInput("quantity", "1")}
                <button className="add-button" type="submit" disabled={available <= 0}>{available <= 0 ? "Vyprodáno" : "Přidat do košíku"} <span>+</span></button>
              </form>
            </div>
          </article>; })}</div>
          <p className="empty-products" id="empty-products" style={{ display: products.length === 0 ? "block" : "none" }}>V této kategorii zatím nejsou žádné produkty.</p>
        </section></section>
        <aside className="cart-panel" id="cart"><div className="cart-heading"><div><span className="eyebrow">VÁŠ NÁKUP</span><h2>Košík</h2></div><span className="cart-count">{get(shop, "cartCount")} ks</span></div>
          {cart.length === 0 ? <div className="empty-cart"><span>○</span><h3>Košík čeká na svůj první nákup.</h3><p>Vyberte produkt v katalogu a počet kusů upravíte přímo tady.</p></div> : <>
            <div className="cart-lines">{cart.map((line) => { const product = record(line.product); const productId = get(product, "id"); return <article className="cart-line" key={productId}>
              <div className="cart-product-thumb">{get(product, "imageUrl") ? <img className="cart-product-image" src={get(product, "imageUrl")} alt={get(product, "name")} /> : <div className="cart-product-placeholder" aria-hidden="true">FM</div>}</div>
              <div className="cart-line-main"><strong>{get(product, "name")}</strong><small>{get(product, "price")} Kč / {get(product, "unit")}</small></div>
              <div className="quantity-control">
                <form method="post">{selectedCategoryId && hiddenInput("categoryId", selectedCategoryId)}{hiddenInput("action", "decrease")}{hiddenInput("productId", productId)}<button aria-label="Odebrat jeden kus">−</button></form>
                <form method="post">{selectedCategoryId && hiddenInput("categoryId", selectedCategoryId)}{hiddenInput("action", "set")}{hiddenInput("productId", productId)}<input name="quantity" defaultValue={get(line, "quantity")} min={1} max={get(product, "availableQuantity")} type="number" aria-label="Počet kusů" /></form>
                <form method="post">{selectedCategoryId && hiddenInput("categoryId", selectedCategoryId)}{hiddenInput("action", "increase")}{hiddenInput("productId", productId)}<button aria-label="Přidat jeden kus">+</button></form>
              </div>
              <strong className="line-total">{get(line, "lineTotal")} Kč</strong>
              <form method="post" className="remove-line">{selectedCategoryId && hiddenInput("categoryId", selectedCategoryId)}{hiddenInput("action", "remove")}{hiddenInput("productId", productId)}<button aria-label="Odstranit produkt z košíku">×</button></form>
            </article>; })}</div>
            <div className="cart-summary"><span>Celkem</span><strong>{get(shop, "cartTotal")} Kč</strong></div><button className="checkout-button" type="button">Pokračovat k doručení <span>→</span></button><p className="checkout-note">Objednávku dokončíte v dalším kroku.</p>
          </>}
        </aside>
      </>}
    </main>
    {isTrue(data, "orderCompleted") && <section className="order-complete-dialog" role="dialog" aria-modal="true" aria-labelledby="order-complete-heading"><div><span className="order-complete-mark">✓</span><span className="eyebrow">OBJEDNÁVKA</span><h2 id="order-complete-heading">Objednávka byla úspěšně dokončena</h2><p>Děkujeme za váš nákup. Vracíme vás na úvodní stránku e-shopu.</p><a className="checkout-submit" href="/eshop">Pokračovat do e-shopu <span>→</span></a></div></section>}
    {shop && get(shop, "checkoutOpen") === "true" && cart.length > 0 && <section id="delivery-step" className="delivery-step"><div className="delivery-step-heading"><span className="eyebrow">DORUČENÍ</span><h2>Adresa zákazníka</h2><p>Vyplňte údaje, na které má být nákup doručen.</p></div>
      <form method="post" className="delivery-form">{hiddenInput("action", "delivery")}
        <label>Jméno<input name="firstName" defaultValue={get(delivery, "firstName")} autoComplete="given-name" required /></label><label>Příjmení<input name="lastName" defaultValue={get(delivery, "lastName")} autoComplete="family-name" required /></label>
        <label>Telefon<input name="phone" type="tel" defaultValue={get(delivery, "phone")} placeholder="+420 123 456 789" autoComplete="tel" required /></label><label>Ulice a číslo domu<input name="street" defaultValue={get(delivery, "street")} autoComplete="street-address" required /></label>
        <label>Město<input name="city" defaultValue={get(delivery, "city")} autoComplete="address-level2" required /></label><label>PSČ<input name="postalCode" defaultValue={get(delivery, "postalCode")} placeholder="602 00" pattern="[0-9]{3} ?[0-9]{2}" autoComplete="postal-code" required /></label>
        <button className="checkout-submit" type="submit">Pokračovat k platbě <span>→</span></button>
      </form></section>}
    {shop && isTrue(shop, "paymentOpen") && cart.length > 0 && <section id="payment-step" className="delivery-step payment-step" role="dialog" aria-labelledby="payment-heading"><div className="delivery-step-heading"><span className="eyebrow">PLATBA</span><h2 id="payment-heading">Platební údaje</h2><p>Zvolte způsob úhrady objednávky.</p></div>
      <form method="post" className="payment-options" data-subtotal={get(shop, "cartTotal")} data-delivery-fee={get(shop, "deliveryFee")}>{hiddenInput("action", "payment")}
        <div className="payment-methods"><label className="payment-option"><input type="radio" name="paymentMethod" value="card" defaultChecked required /><span><strong>VISA / MasterCard</strong><small>Platba kartou online</small></span></label><label className="payment-option"><input type="radio" name="paymentMethod" value="cod" /><span><strong>Platba při doručení</strong><small>Uhradíte kurýrovi při převzetí</small></span></label></div>
        <div className="card-details" data-card-details><label>Číslo karty<span className="card-number-fields">
          <input name="cardNumber1" inputMode="numeric" autoComplete="cc-number" maxLength={4} pattern="[0-9]{4}" placeholder="1234" /><input name="cardNumber2" inputMode="numeric" maxLength={4} pattern="[0-9]{4}" placeholder="5678" /><input name="cardNumber3" inputMode="numeric" maxLength={4} pattern="[0-9]{4}" placeholder="9012" /><input name="cardNumber4" inputMode="numeric" maxLength={4} pattern="[0-9]{4}" placeholder="3456" />
        </span></label><label>Platnost<input name="cardExpiry" inputMode="numeric" autoComplete="cc-exp" placeholder="MM/RR" /></label><label>CVV<input name="cardCvc" inputMode="numeric" autoComplete="cc-csc" placeholder="123" /></label></div>
        <div className="payment-summary"><span>Mezisoučet</span><strong>{get(shop, "cartTotal")} Kč</strong><span data-delivery-fee-label hidden>Poplatek za doručení</span><strong data-delivery-fee-label hidden>{get(shop, "deliveryFee")} Kč</strong><span>Celkem</span><strong data-payment-total>{get(shop, "cartTotal")} Kč</strong></div>
        <button className="checkout-submit" type="submit">Dokončit objednávku <span>→</span></button>
      </form></section>}
  </>;
}

function PurchaseForm({ order, data }: { order: Row | null; data: Row }) {
  const lines = order ? rows(order, "lines") : [{}];
  return <form method="post" className="new-order-form" data-purchase-form>
    {hiddenInput("action", order ? "update" : "create")}{hiddenInput("id", get(order ?? {}, "id"))}
    <label>Dodavatel<input name="supplierName" defaultValue={get(order ?? {}, "supplierName")} required maxLength={200} placeholder="Název dodavatele" /></label>
    <label>Požadováno<input type="date" name="requestedOn" defaultValue={get(order ?? {}, "requestedOn")} required /></label>
    <label>Očekávané dodání<input type="date" name="expectedDeliveryDate" defaultValue={get(order ?? {}, "expectedDeliveryDate")} required /></label>
    <label>Dodavatelský sklad<select name="sourceWarehouseId" defaultValue={get(order ?? {}, "sourceWarehouseId")} required><option value="">Vyberte sklad</option>{rows(data, "warehouses").filter((warehouse) => get(warehouse, "ownerType") === "SUPPLIER").map((warehouse) => <option key={get(warehouse, "id")} value={get(warehouse, "id")}>{get(warehouse, "name")}</option>)}</select></label>
    <label>Firemní cílový sklad<select name="destinationWarehouseId" defaultValue={get(order ?? {}, "destinationWarehouseId")} required><option value="">Vyberte sklad</option>{rows(data, "warehouses").filter((warehouse) => get(warehouse, "ownerType") === "COMPANY").map((warehouse) => <option key={get(warehouse, "id")} value={get(warehouse, "id")}>{get(warehouse, "name")}</option>)}</select></label>
    <fieldset className="purchase-lines" data-purchase-lines><legend>Položky objednávky</legend>{lines.map((line, index) => <div className="purchase-line" data-purchase-line key={`${index}-${get(line, "productId")}`}>
      <label>Produkt<select name="productId[]" required data-product-select defaultValue={get(line, "productId")}><option value="">Vyberte produkt</option>{rows(data, "products").map((product) => <option key={get(product, "id")} value={get(product, "id")} data-price={get(product, "purchasePrice", "0.00")}>{get(product, "name")} ({get(product, "sku")})</option>)}</select></label>
      <label>Množství<input type="number" name="quantity[]" defaultValue={get(line, "quantity")} min={1} step={1} required placeholder="1" /></label>
      <label>Cena / ks<input type="number" name="unitPrice[]" defaultValue={get(line, "unitPrice")} min={0} step="0.01" required placeholder="0.00" data-unit-price /></label>
      <button className="remove-line" type="button" data-remove-line aria-label="Odebrat položku">×</button>
    </div>)}</fieldset>
    <button className="add-line" type="button" data-add-line>+ Přidat další produkt</button><output className="order-total" data-order-total>Celkem: 0,00 Kč</output><button className="secondary" type="submit">{order ? "Uložit změny" : "Vytvořit objednávku"}</button>
  </form>;
}

function formatDate(value: string) {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value);
  return match ? `${Number(match[3])}. ${Number(match[2])}. ${match[1]}` : value;
}

const purchaseScript = `(() => {
  document.addEventListener('click', (event) => {
    const target = event.target;
    if (!(target instanceof Element)) return;
    const open = target.closest('[data-open-order-dialog]');
    if (open) document.querySelector('[data-order-dialog]')?.showModal();
    const close = target.closest('[data-close-order-dialog]');
    if (close) close.closest('dialog')?.close();
    const dialog = target.closest('[data-order-dialog]');
    if (dialog && target === dialog) dialog.close();
    const add = target.closest('[data-add-line]');
    if (add) {
      const form = add.closest('[data-purchase-form]');
      const lines = form?.querySelector('[data-purchase-lines]');
      const original = lines?.querySelector('[data-purchase-line]');
      if (lines && original) {
        const clone = original.cloneNode(true);
        clone.querySelectorAll('input').forEach((input) => { input.value = ''; });
        const select = clone.querySelector('select');
        if (select) select.value = '';
        lines.append(clone);
      }
    }
    const remove = target.closest('[data-remove-line]');
    if (remove) {
      const lines = remove.closest('[data-purchase-lines]');
      if (lines && lines.querySelectorAll('[data-purchase-line]').length > 1) remove.closest('[data-purchase-line]')?.remove();
    }
    updateAllTotals();
  });
  const updateTotal = (form) => {
    const lines = form.querySelector('[data-purchase-lines]');
    const output = form.querySelector('[data-order-total]');
    if (!lines || !output) return;
    const total = [...lines.querySelectorAll('[data-purchase-line]')].reduce((sum, line) =>
      sum + Number(line.querySelector('[name="quantity[]"]')?.value || 0) * Number(line.querySelector('[name="unitPrice[]"]')?.value || 0), 0);
    output.textContent = 'Celkem: ' + total.toLocaleString('cs-CZ', { style: 'currency', currency: 'CZK' });
  };
  const updateAllTotals = () => document.querySelectorAll('[data-purchase-form]').forEach(updateTotal);
  document.addEventListener('input', (event) => {
    if (event.target instanceof Element) {
      const form = event.target.closest('[data-purchase-form]');
      if (form) updateTotal(form);
    }
  });
  document.addEventListener('change', (event) => {
    const target = event.target;
    if (target instanceof HTMLSelectElement && target.matches('[data-product-select]')) {
      const price = target.selectedOptions[0]?.dataset.price || '0.00';
      const priceInput = target.closest('[data-purchase-line]')?.querySelector('[data-unit-price]');
      if (priceInput) priceInput.value = price;
      const form = target.closest('[data-purchase-form]');
      if (form) updateTotal(form);
    }
  });
  document.querySelectorAll('[data-purchase-form]').forEach(updateTotal);
})();`;

export function PurchasePage({ data }: ViewProps) {
  const overview = record(data.overview);
  const orders = rows(overview, "orders");
  const products = rows(data, "products");
  const warehouses = rows(data, "warehouses");
  return <>
    <main className="purchase-page">
      <header className="purchase-header"><a href="/apps" className="back-link">← Aplikace</a><div><span className="eyebrow">PROVOZ / NÁKUP</span><h1>Nákup</h1><p>Řiďte nákupní požadavky, objednávky dodavatelům a očekávané příjmy zboží.</p></div>
        <div className="purchase-actions"><a href="#orders" className="primary">Přehled nákupu</a><button type="button" className="primary add-order-button" data-open-order-dialog>Přidej objednávku</button></div></header>
      <Messages data={data} className="purchase-message" />
      <section className="purchase-metrics"><article><span>Požadavky k objednání</span><strong>{overview ? Number(get(overview, "requestedValue", "0")).toFixed(2) : "-"} Kč</strong><small>čeká na odeslání dodavateli</small></article>
        <article><span>Objednáno u dodavatelů</span><strong>{overview ? Number(get(overview, "orderedValue", "0")).toFixed(2) : "-"} Kč</strong><small>očekáváme na sklad</small></article>
        <article><span>Otevřené požadavky</span><strong>{get(overview, "requestedCount", "-")}</strong><small>vyžaduje rozhodnutí nákupu</small></article></section>
      <section id="orders" className="purchase-section"><div className="section-head"><div><span className="eyebrow">NÁKUPNÍ POŽADAVKY A OBJEDNÁVKY</span><h2>Plán zásobování</h2></div><span className="order-count">{orders.length} dokumenty</span></div>
        <div className="order-table-wrap"><table className="order-table"><thead><tr><th>Dokument</th><th>Dodavatel</th><th>Tok skladu</th><th>Požadováno</th><th>Dodání</th><th className="amount-column">Celkem</th><th>Stav / akce</th></tr></thead><tbody>
          {orders.map((order) => { const status = get(order, "status"); const source = warehouses.find((warehouse) => get(warehouse, "id") === get(order, "sourceWarehouseId")) ?? {}; const destination = warehouses.find((warehouse) => get(warehouse, "id") === get(order, "destinationWarehouseId")) ?? {}; return <tr key={get(order, "id")}>
            <td data-label="Dokument"><span className={`status-chip status-${status.toLowerCase()}`}>{status === "REQUESTED" ? "POŽADAVEK" : "OBJEDNÁNO"}</span><strong>{get(order, "orderNumber")}</strong><details><summary>Obsah objednávky</summary><div className="order-detail-lines">
              {rows(order, "lines").map((line, index) => { const product = products.find((candidate) => get(candidate, "id") === get(line, "productId")) ?? {}; const activeImage = rows(product, "images").find((image) => isTrue(image, "active")); const imageUrl = get(product, "imageUrl", get(activeImage ?? {}, "imageUrl")); return <article className="order-detail-line" key={`${index}-${get(line, "productId")}`}>
                {imageUrl ? <img src={imageUrl} alt={get(product, "name", get(line, "productId"))} loading="lazy" /> : <div className="order-detail-image-placeholder">Bez obrázku</div>}
                <div><h3>{get(product, "name", get(line, "productId"))}</h3><p>{get(product, "sku")} · {get(product, "unit")}</p><strong>{get(line, "quantity")} ks · {Number(get(line, "unitPrice", "0")).toFixed(2)} Kč/ks</strong></div>
              </article>; })}
            </div></details></td>
            <td data-label="Dodavatel">{get(order, "supplierName")}</td><td data-label="Tok skladu"><span className="warehouse-flow"><span>{get(source, "name", get(order, "sourceWarehouseId"))}</span><span className="warehouse-arrow">→</span><span>{get(destination, "name", get(order, "destinationWarehouseId"))}</span></span></td>
            <td data-label="Požadováno"><span className="order-date">{get(order, "requestedOn") ? formatDate(get(order, "requestedOn")) : "-"}</span><small className="order-quantity">{order.quantity == null ? "-" : `${get(order, "receivedQuantity")} / ${get(order, "quantity")}`} ks</small></td>
            <td data-label="Dodání">{get(order, "expectedDeliveryDate") ? formatDate(get(order, "expectedDeliveryDate")) : "-"}</td><td data-label="Celkem" className="amount-column"><strong>{Number(get(order, "totalAmount", "0")).toFixed(2)} Kč</strong></td>
            <td data-label="Stav / akce">{status === "REQUESTED" ? <>
              <details className="workforce-edit"><summary>Upravit</summary><PurchaseForm order={order} data={data} /></details><form method="post">{hiddenInput("id", get(order, "id"))}<button className="secondary" type="submit">Vystavit objednávku</button></form>
            </> : status === "ORDERED" && order.quantity != null ? <form method="post">{hiddenInput("id", get(order, "id"))}{hiddenInput("action", "receive")}{hiddenInput("quantity", String(Number(get(order, "quantity")) - Number(get(order, "receivedQuantity"))))}<button className="secondary" type="submit">Přijmout na sklad</button></form>
              : <span className="ordered-label">{status === "RECEIVED" ? "Přijato na sklad" : "Čeká na dodání"}</span>}</td>
          </tr>; })}
        </tbody></table></div>
      </section>
    </main>
    <dialog className="order-dialog" data-order-dialog aria-labelledby="new-order-title"><div className="order-dialog-head"><div><span className="eyebrow">NOVÝ DOKUMENT</span><h2 id="new-order-title">Přidej objednávku</h2></div><button type="button" className="dialog-close" aria-label="Zavřít" data-close-order-dialog>×</button></div>
      <PurchaseForm order={null} data={data} />
    </dialog>
    <InlinePageScript source={purchaseScript} />
  </>;
}

function ShiftForm({ shift, data }: { shift: Row | null; data: Row }) {
  return <form method="post" className="workforce-form">
    {hiddenInput("action", shift ? "update" : "create")}
    {shift && <>{hiddenInput("id", get(shift, "id"))}{hiddenInput("version", get(shift, "version"))}</>}
    <label>Zaměstnanec<select name="employeeId" defaultValue={get(shift ?? {}, "employeeId")}>{isTrue(data, "manageAll") && <option value="">Neobsazeno</option>}
      {rows(data, "employees").map((employee) => <option key={get(employee, "id")} value={get(employee, "id")}>{get(employee, "fullName")} / {get(employee, "teamName")}</option>)}
    </select></label>
    <label>Pracovní role<input name="roleName" defaultValue={get(shift ?? {}, "roleName")} maxLength={150} list="role-options" required /></label>
    <label>Pracoviště<select name="department" defaultValue={get(shift ?? {}, "department")} required>{rows(data, "workplaces").map((workplace) => <option key={get(workplace, "name")} value={get(workplace, "name")}>{get(workplace, "name")}</option>)}</select></label>
    <label>Začátek<input type="datetime-local" name="startAt" defaultValue={get(shift ?? {}, "startAt")} required /></label>
    <label>Konec<input type="datetime-local" name="endAt" defaultValue={get(shift ?? {}, "endAt")} required /></label>
    <button className={shift ? "secondary" : "primary"} type="submit">{shift ? "Uložit směnu" : "Vytvořit směnu"}</button>
  </form>;
}

export function PlanningPage({ data }: ViewProps) {
  const overview = record(data.overview);
  const shifts = rows(overview, "shifts");
  const edit = isTrue(data, "edit");
  const events = data.events === null || data.events === undefined ? null : list(data.events);
  const notifications = rows(data, "notifications");
  return <main className="planning-page">
    <header className="planning-header"><a href="/apps" className="back-link">← Aplikace</a><div><span className="eyebrow">PROVOZ / PLÁNOVÁNÍ</span><h1>Plánování</h1></div><a href="#schedule" className="primary">Plán směn</a></header>
    <Messages data={data} className="planning-message" />
    <section className="planning-metrics">
      <article><span>Naplánované směny</span><strong>{get(overview, "shiftCount", "-")}</strong><small>sloty v aktuálním plánu</small></article>
      <article><span>Neobsazené sloty</span><strong>{get(overview, "openShiftCount", "-")}</strong><small>čekají na přiřazení</small></article>
      <article><span>Kapacita týmu</span><strong>{get(overview, "plannedHours", "-")} h</strong><small>{get(overview, "draftShiftCount", "-")} směn v návrhu</small></article>
    </section>
    {edit && <section className="workforce-section"><h2>Nová směna</h2><ShiftForm shift={null} data={data} />
      <datalist id="role-options">{strings(data.roleOptions).map((role) => <option key={role} value={role} />)}</datalist></section>}
    {isTrue(data, "manageAll") && <section className="workforce-section"><details><summary>Pracoviště a kapacity</summary>
      {rows(data, "workplaces").map((workplace) => <p key={get(workplace, "name")}>{get(workplace, "name")}: {get(workplace, "capacity")}</p>)}
      <form method="post" className="workforce-form">{hiddenInput("action", "workplace")}<label>Pracoviště<input name="name" maxLength={150} list="workplace-options" required /></label><label>Souběžné sloty<input name="capacity" type="number" min={1} max={10000} defaultValue={1} required /></label><button type="submit" className="secondary">Uložit kapacitu</button></form>
      <datalist id="workplace-options">{rows(data, "workplaces").map((workplace) => <option key={get(workplace, "name")} value={get(workplace, "name")} />)}</datalist>
    </details></section>}
    <section id="schedule" className="planning-section">
      {edit && <form method="post" id="publish-plan" className="workforce-actions">{hiddenInput("action", "publishPlan")}<button className="secondary" type="submit">Publikovat vybrané směny</button></form>}
      <div className="section-head"><div><span className="eyebrow">TÝDENNÍ PLÁN</span><h2>Směny a kapacity</h2></div><span className="shift-count">{shifts.length} sloty</span></div>
      <div className="shift-table-wrap"><table className="shift-table"><thead><tr><th>Směna</th><th>Začátek</th><th>Konec</th><th>Akce</th></tr></thead><tbody>
        {shifts.length ? shifts.map((shift) => { const status = get(shift, "status"); const shiftId = get(shift, "id"); return <Fragment key={shiftId}>
          <tr className="shift-row"><td data-label="Směna"><span className={`status-chip status-${status.toLowerCase()}`}>{status === "PUBLISHED" ? "PUBLIKOVÁNO" : "NÁVRH"}</span><strong>{get(shift, "roleName")}</strong><small>{get(shift, "department")} · {get(shift, "employeeName", "Neobsazeno")}</small></td>
            <td data-label="Začátek">{get(shift, "startAt").replaceAll("T", " ")}</td><td data-label="Konec">{get(shift, "endAt").replaceAll("T", " ")}</td>
            <td data-label="Akce" className="shift-action-cell"><div className="workforce-actions">{edit && status === "DRAFT" && <>
              <label><input type="checkbox" form="publish-plan" name="shiftIds[]" value={shiftId} /> Vybrat</label>
              <form method="post">{hiddenInput("action", "publish")}{hiddenInput("id", shiftId)}<button className="secondary" type="submit">Publikovat směnu</button></form>
              <form method="post" data-confirm-delete data-confirm-message="Opravdu chcete tento koncept smazat?">{hiddenInput("action", "delete")}{hiddenInput("id", shiftId)}<button className="secondary" type="submit">Smazat koncept</button></form>
            </>}<a href={`/planning?audit=${encodeURIComponent(shiftId)}#audit`}>Historie</a></div></td>
          </tr>
          {edit && <tr className="shift-edit-row"><td colSpan={4}><details className="workforce-edit"><summary>Upravit směnu</summary><ShiftForm shift={shift} data={data} /></details></td></tr>}
        </Fragment>; }) : overview && <tr><td colSpan={4}>Žádné směny.</td></tr>}
      </tbody></table></div>
    </section>
    {events && <section id="audit" className="workforce-section"><h2>Historie směny</h2>
      {events.length ? events.map((event, index) => <p className="workforce-event" key={`${get(event, "occurredAt")}-${index}`}><time>{get(event, "occurredAt")}</time> <strong>{get(event, "action")}</strong> / {get(event, "actor")}<br />{get(event, "details")}</p>) : <p>Žádné záznamy.</p>}
    </section>}
    <section className="workforce-section"><h2>Moje oznámení</h2>
      {notifications.length ? notifications.map((notice) => <div className="workforce-event" key={get(notice, "id")}><p>{get(notice, "message")}</p><time>{get(notice, "createdAt")}</time>
        {notice.readAt == null && <form method="post">{hiddenInput("action", "read")}{hiddenInput("id", get(notice, "id"))}<button type="submit" className="secondary">Označit jako přečtené</button></form>}</div>) : <p>Žádná oznámení.</p>}
    </section>
  </main>;
}

const inventoryStyles = `
.inventory-image-modal{position:fixed;inset:0;z-index:1000;display:grid;place-items:center;padding:24px;background:rgba(15,29,24,.82);cursor:zoom-out}
.inventory-image-modal-content{position:relative;display:grid;place-items:center;max-width:calc(100vw - 48px);max-height:calc(100vh - 48px)}
.inventory-image-modal img{display:block;max-width:calc(100vw - 48px);max-height:calc(100vh - 48px);width:auto;height:auto;object-fit:contain;box-shadow:0 20px 60px rgba(0,0,0,.35);cursor:default}
.inventory-image-modal-close{position:absolute;z-index:1;top:-18px;right:-18px;width:36px;height:36px;border:0;border-radius:50%;background:#fffdf8;color:#17362b;font-size:26px;line-height:1;cursor:pointer;box-shadow:0 4px 14px rgba(0,0,0,.25)}
@media(max-width:520px){.inventory-image-modal{padding:12px}.inventory-image-modal img{max-width:calc(100vw - 24px);max-height:calc(100vh - 24px)}}
.product-image-popup{position:fixed;z-index:1000;width:min(420px,calc(100vw - 32px));height:min(420px,calc(100vh - 32px));display:grid;place-items:center;padding:10px;background:#fffdf9;border:1px solid #d8d5ce;box-shadow:0 18px 50px rgba(20,29,38,.3);pointer-events:none}
.product-image-popup img{display:block;max-width:100%;max-height:100%;width:auto;height:auto;object-fit:contain}`;

function InventoryHistory({ data }: { data: Row }) {
  const movements = data.movements == null ? null : record(data.movements);
  const page = Number(get(movements ?? {}, "page", "0"));
  const totalPages = Number(get(movements ?? {}, "totalPages", "0"));
  const itemId = get(data, "selectedItemId");
  const historyError = get(data, "historyError");
  return <section id="history" className="inventory-section inventory-view-panel" role="tabpanel" aria-labelledby="tab-history" hidden={get(data, "selectedView") !== "history"}>
    <div className="section-head"><div><span className="eyebrow">AUDIT ZÁSOB</span><h2>Historie příjmů a výdejů</h2></div></div>
    <p className="inventory-help">Pohyby nelze upravovat ani mazat. Opravu evidujte jako opačný pohyb s odkazem na původní doklad.
      Počáteční stav zachycuje zásoby při zavedení evidence. Časy jsou v pásmu Europe/Prague.</p>
    <form method="get" className="inventory-history-filter">{hiddenInput("view", "history")}<label htmlFor="history-item">Skladová položka</label>
      <select id="history-item" name="itemId" defaultValue={itemId}><option value="">Všechny položky</option>{rows(data, "allItems").map((item) =>
        <option key={get(item, "id")} value={get(item, "id")}>{get(item, "productName")} / {get(item, "locationName")}</option>)}</select>
      <button className="secondary" type="submit">Zobrazit</button>
    </form>
    {historyError && <p className="inventory-message error" role="alert">{historyError}</p>}
    {movements && <>
      <div className="stock-table-wrap"><table className="stock-table movement-table"><caption className="inventory-help">Příjmy, výdeje a stav po každém pohybu</caption>
        <thead><tr><th scope="col">Datum / ID</th><th scope="col">Produkt / lokace</th><th scope="col">Typ</th><th scope="col">Množství</th><th scope="col">Stav po pohybu</th><th scope="col">Doklad / poznámka</th><th scope="col">Uživatel</th></tr></thead>
        <tbody>{rows(movements, "items").length ? rows(movements, "items").map((movement) => {
          const type = get(movement, "type");
          return <tr key={get(movement, "id")}><td data-label="Datum / ID"><time>{get(movement, "createdAtFormatted")}</time><br />#{get(movement, "id")}</td>
            <td data-label="Produkt / lokace"><strong>{get(movement, "productName")}</strong><span>{get(movement, "sku")}</span><span>{get(movement, "locationName")}</span></td>
            <td data-label="Typ"><span className={`status-chip ${type === "ISSUE" ? "status-low" : "status-ok"}`}>{type === "ISSUE" ? "VÝDEJ" : type === "RECEIPT" ? "PŘÍJEM" : "POČÁTEČNÍ STAV"}</span></td>
            <td data-label="Množství">{type === "ISSUE" ? "−" : "+"}{get(movement, "quantity")} {get(movement, "unit")}</td><td data-label="Stav po pohybu">{get(movement, "balanceAfter")} {get(movement, "unit")}</td>
            <td data-label="Doklad / poznámka"><strong>{get(movement, "reference")}</strong><span className="movement-note">{get(movement, "note")}</span></td><td data-label="Uživatel">{get(movement, "actorName")}</td></tr>;
        }) : <tr><td colSpan={7}>Pro tento výběr nejsou evidovány žádné pohyby.</td></tr>}</tbody>
      </table></div>
      <nav className="inventory-pagination" aria-label="Stránkování historie"><span>Celkem {get(movements, "totalElements", "0")} pohybů · Strana {totalPages === 0 ? 0 : page + 1} / {totalPages}</span><div>
        {page > 0 && <a className="secondary" href={`/inventory?view=history&page=${page - 1}${itemId ? `&itemId=${encodeURIComponent(itemId)}` : ""}`}>Předchozí</a>}
        {page + 1 < totalPages && <a className="secondary" href={`/inventory?view=history&page=${page + 1}${itemId ? `&itemId=${encodeURIComponent(itemId)}` : ""}`}>Další</a>}
      </div></nav>
    </>}
  </section>;
}

export function InventoryPage({ data }: ViewProps) {
  const overview = data.overview == null ? null : record(data.overview);
  const selectedView = get(data, "selectedView", "stock");
  const canEdit = isTrue(data, "canEdit");
  const products = rows(data, "products");
  const groups = rows(data, "groups");
  return <div className="inventory-page">
    <style>{inventoryStyles}</style>
    <header className="inventory-header"><a href="/apps" className="back-link">← Aplikace</a><div><span className="eyebrow">PROVOZ / SKLAD</span><h1>Sklad</h1><p>Zásoby podle lokace, příjem, výdej a dohledatelná historie pohybů.</p></div>
      <div className="view-switch" role="tablist" aria-label="Pohled skladu">
        {[["stock", "Sklad"], ["products", "Produkty"], ["history", "Historie"]].map(([tab, label]) =>
          <button id={`tab-${tab}`} key={tab} className={`view-tab ${tab === selectedView ? "active" : ""}`} type="button" role="tab" aria-selected={tab === selectedView} aria-controls={tab} tabIndex={tab === selectedView ? 0 : -1} data-inventory-view={tab}>{label}</button>)}
      </div>
    </header>
    {get(data, "loadError") && <p className="inventory-message error" role="alert">{get(data, "loadError")}</p>}
    {get(data, "actionError") && <p className="inventory-message error" role="alert">{get(data, "actionError")}</p>}
    {get(data, "message") && <p className="inventory-message" role="status">{get(data, "message")}</p>}
    <section className="inventory-metrics">
      <article><span>Zásoba celkem</span><strong>{overview ? get(overview, "totalQuantity") : "-"} jednotek</strong><small>součet evidovaných kusů a měrných jednotek</small></article>
      <article><span>Hodnota zásob</span><strong>{overview ? Number(get(overview, "stockValue", "0")).toFixed(2) : "-"} Kč</strong><small>v pořizovacích cenách</small></article>
      <article><span>Pod minimem</span><strong>{overview ? get(overview, "lowStockCount") : "-"}</strong><small>lokace vyžadují doplnění</small></article>
    </section>
    <div className="inventory-filters" hidden={selectedView === "history"}>
      <label htmlFor="inventory-search">Hledat produkt, SKU nebo lokaci<input id="inventory-search" type="search" placeholder="Název nebo SKU" /></label>
      <label className="inventory-filter-check"><input id="inventory-low-stock" type="checkbox" /> Jen pod minimem</label><span id="inventory-filter-count" role="status" aria-live="polite" />
    </div>
    <section id="stock" className="inventory-section inventory-view-panel" role="tabpanel" aria-labelledby="tab-stock" hidden={selectedView !== "stock"}>
      <div className="section-head"><div><span className="eyebrow">SKLADOVÉ POLOŽKY</span><h2>Stav zásob podle lokace</h2></div><span className="item-count">{get(data, "itemCount", "0")} lokace</span></div>
      <div className="category-tree">{groups.map((group) => <details className="category-branch" open key={get(group, "path")}><summary><span>Kategorie</span> <strong>{get(group, "path")}</strong></summary>
        <div className="stock-table-wrap"><table className="stock-table"><thead><tr><th>Lokace</th><th>Položka</th><th>Obrázek</th><th>Skladem</th><th>Minimum</th><th>Jednotková cena</th><th>Pohyb zásob</th></tr></thead><tbody>
          {rows(group, "items").map((item) => { const low = isTrue(item, "lowStock"); const itemId = get(item, "id"); return <tr key={itemId} className={low ? "low-stock" : ""} data-stock-row data-low-stock={String(low)} data-search={`${get(item, "productName")} ${get(item, "sku")} ${get(item, "locationName")}`}>
            <td data-label="Lokace"><span className={`status-chip ${low ? "status-low" : "status-ok"}`}>{low ? "DOPLNIT" : "V POŘÁDKU"}</span><strong>{get(item, "locationName")}</strong></td>
            <td data-label="Položka"><strong>{get(item, "productName")}</strong><span>{get(item, "sku")}</span></td>
            <td data-label="Obrázek"><div className="stock-item-image">{get(item, "imageUrl") ? <img src={get(item, "imageUrl")} alt={get(item, "productName")} /> : <span>FM</span>}</div></td>
            <td data-label="Skladem"><strong>{get(item, "quantity")} {get(item, "unit")}</strong></td>
            <td data-label="Minimum">{canEdit ? <input form={`receive-form-${itemId}`} type="number" name="reorderLevel" min={0} max={2147483647} step={1} defaultValue={get(item, "reorderLevel")} aria-label={`Minimum ${get(item, "productName")}`} required /> : get(item, "reorderLevel")} {get(item, "unit")}</td>
            <td data-label="Jednotková cena">{canEdit ? <input form={`receive-form-${itemId}`} type="number" name="unitCost" min={0} max="9999999999.99" step="0.01" defaultValue={Number(get(item, "unitCost", "0")).toFixed(2)} aria-label={`Jednotková cena ${get(item, "productName")}`} required /> : Number(get(item, "unitCost", "0")).toFixed(2)} Kč</td>
            <td data-label="Pohyb zásob">{canEdit ? <form id={`receive-form-${itemId}`} method="post" className="stock-movement-form">{hiddenInput("id", itemId)}
              <label>Typ<select name="action" data-stock-action><option value="receive">Příjem</option><option value="dispatch">Výdej</option></select></label>
              <label>Množství ({get(item, "unit")})<input type="number" name="quantity" min={1} max={2147483647} step={1} defaultValue={1} data-available={get(item, "quantity")} required /></label>
              <label>Doklad / reference<input name="reference" maxLength={120} placeholder="Číslo dokladu" required /></label>
              <details><summary>Poznámka</summary><textarea name="note" rows={2} maxLength={500} aria-label="Poznámka k pohybu" /></details>
              <button className="secondary" type="submit">Zaúčtovat příjem</button>
            </form> : <span>Pouze pro čtení</span>}
              <a className="inventory-history-link" href={`/inventory?view=history&itemId=${encodeURIComponent(itemId)}`}>Historie položky</a></td>
          </tr>; })}
        </tbody></table></div>
      </details>)}</div>
    </section>
    <section id="products" className="inventory-section inventory-view-panel" role="tabpanel" aria-labelledby="tab-products" hidden={selectedView !== "products"}>
      <div className="section-head"><div><span className="eyebrow">PRODUKTY VE SKLADU</span><h2>Produkty a dostupnost</h2></div><div className="warehouse-picker"><label htmlFor="warehouse-select">Vybraný sklad</label>
        <select id="warehouse-select" defaultValue={get(data, "selectedWarehouse")}>{rows(data, "warehouseOptions").map((warehouse) => <option key={get(warehouse, "locationName")} value={get(warehouse, "locationName")}>{get(warehouse, "locationName")}</option>)}</select></div>
        <span className="item-count">{products.length} produktů</span></div>
      <div className="warehouse-product-grid">{products.map((product) => <article key={get(product, "productId")} className="warehouse-product-card" data-product-card data-low-stock={String(isTrue(product, "lowStock"))} data-search={`${get(product, "productName")} ${get(product, "sku")}`}>
        <div className="warehouse-product-media">{get(product, "imageUrl") ? <img src={get(product, "imageUrl")} alt={get(product, "productName")} /> : <span>Bez obrázku</span>}</div>
        <div className="warehouse-product-body"><span className="product-sku">{get(product, "sku")}</span><h3>{get(product, "productName")}</h3><p>{get(product, "description") || "Bez popisu"}</p><small>{get(product, "categoryPath")} · {get(product, "locationCount")} lokace</small></div>
        <div className="warehouse-product-stock"><div><dt>Celkem na skladech</dt><dd>{get(product, "warehouseQuantity")} {get(product, "unit")}</dd></div><div><dt>Z hlavního skladu k dispozici</dt><dd>{get(product, "centralQuantity")} {get(product, "unit")}</dd></div></div>
        <div className="warehouse-location-list">{rows(product, "warehouses").map((warehouse) => <div className="warehouse-location-row" data-warehouse={get(warehouse, "locationName")} key={get(warehouse, "locationName")}><dl>
          <div><dt>Na vybraném skladu</dt><dd>{get(warehouse, "quantity")} {get(product, "unit")}</dd></div><div><dt>Objednat z hlavního skladu</dt><dd>{canEdit ? <form method="post" className="order-form">
            {hiddenInput("action", "order")}{hiddenInput("productId", get(product, "productId"))}{hiddenInput("locationName", get(warehouse, "locationName"))}
            <input type="number" name="quantity" min={0} max={2147483647} step={1} defaultValue={get(warehouse, "orderedFromCentral")} aria-label={`Objednat ${get(product, "productName")}`} required /><span>{get(product, "unit")}</span><button className="secondary" type="submit">Uložit</button>
          </form> : `${get(warehouse, "orderedFromCentral")} ${get(product, "unit")}`}</dd></div>
        </dl></div>)}</div>
      </article>)}</div>
    </section>
    <InventoryHistory data={data} />
  </div>;
}

const promoScript = `(() => {
  const modal = document.getElementById('campaign-modal');
  const form = modal?.querySelector('.campaign-create-form');
  document.addEventListener('change', (event) => {
    const target = event.target;
    if (target instanceof HTMLSelectElement && target.name === 'status' && target.form?.closest('.campaign-form')) target.form.requestSubmit();
  });
})();`;

export function PromoPage({ data }: ViewProps) {
  const campaigns = rows(data, "campaigns");
  const options = record(data.options);
  const statuses = strings(data.statuses);
  return <div className="promo-page">
    <header className="promo-header"><a href="/apps" className="back-link">← Aplikace</a><div><span className="eyebrow">PRODEJ / PROMO</span><h1>Promo kampaně</h1><p>Plánujte letákové akce, kontrolujte marži a upravujte jejich průběh.</p></div><button type="button" className="primary" id="add-campaign">+ Nová kampaň</button></header>
    {get(data, "loadError") && <p className="promo-message error" role="alert">{get(data, "loadError")}</p>}
    {get(data, "actionError") && <p className="promo-message error" role="alert">{get(data, "actionError")}</p>}
    {get(data, "message") && <p className="promo-message" role="status">{get(data, "message")}</p>}
    <section className="promo-features"><article><b>Plán akce</b><span>Datum, sortiment a cenový rozdíl</span></article><article><b>Alokace prodejen</b><span>Distribuce plánovaného množství</span></article><article><b>Výkon kampaně</b><span>Plán versus skutečný prodej</span></article></section>
    <section id="campaigns" className="campaign-section"><div className="section-head"><div><span className="eyebrow">AKTUÁLNÍ KAMPANĚ</span><h2>Řízení průběhu</h2></div><span className="campaign-count">{campaigns.length} kampaní</span></div>
      <div className="campaign-table-wrap"><table className="campaign-table"><thead><tr><th>Produkt</th><th>Kampaň</th><th>Období</th><th>Běžná cena</th><th>Akční cena</th><th>Plán / skutečnost</th><th>Stav</th><th>Akce</th></tr></thead><tbody>
        {campaigns.map((campaign) => { const id = get(campaign, "id"); const status = get(campaign, "status"); return <tr key={id}>
          <td data-label="Produkt"><div className="campaign-product-photo">{get(campaign, "imageUrl") ? <img src={get(campaign, "imageUrl")} data-image={get(campaign, "imageUrl")} alt={get(campaign, "name")} /> : <span aria-hidden="true">Bez fotografie</span>}</div></td>
          <td data-label="Kampaň"><strong>{get(campaign, "name")}</strong><span className={`status-chip status-${status.toLowerCase()}`}>{status}</span></td>
          <td data-label="Období">{get(campaign, "startsOn")}<br /> až {get(campaign, "endsOn")}</td>
          <td data-label="Běžná cena">{Number(get(campaign, "regularPrice", "0")).toFixed(2)} Kč</td><td data-label="Akční cena"><strong>{Number(get(campaign, "promoPrice", "0")).toFixed(2)} Kč</strong></td>
          <td data-label="Plán / skutečnost">{get(campaign, "plannedQuantity")} / {get(campaign, "actualQuantity")}</td>
          <td data-label="Stav"><form id={`campaign-status-${id}`} method="post" className="campaign-form">{hiddenInput("id", id)}
            <select name="status" aria-label="Stav kampaně" defaultValue={status}>{statuses.map((item) => <option key={item} value={item}>{item}</option>)}</select>
          </form></td>
          <td data-label="Akce"><button type="button" className="secondary campaign-edit" data-id={id} data-name={get(campaign, "name")} data-product-id={get(campaign, "productId")} data-supplier-id={get(campaign, "supplierId")} data-starts-on={get(campaign, "startsOn")} data-ends-on={get(campaign, "endsOn")} data-regular-price={Number(get(campaign, "regularPrice", "0")).toFixed(2)} data-promo-price={Number(get(campaign, "promoPrice", "0")).toFixed(2)} data-supplier-purchase-price={Number(get(campaign, "supplierPurchasePrice", "0")).toFixed(2)} data-planned-quantity={get(campaign, "plannedQuantity")} data-marketing-contribution={Number(get(campaign, "marketingContribution", "0")).toFixed(2)}>Upravit</button></td>
        </tr>; })}
      </tbody></table></div>
    </section>
    <div className="campaign-modal" id="campaign-modal" aria-hidden="true"><div className="campaign-modal-backdrop" />
      <section className="campaign-dialog" role="dialog" aria-modal="true" aria-labelledby="campaign-dialog-title"><button type="button" className="dialog-close" id="close-campaign" aria-label="Zavřít">×</button><span className="eyebrow">PRODEJ / PROMO</span><h2 id="campaign-dialog-title">Nová promo kampaň</h2><p>Zadejte plán akce, ceny a plánované množství.</p>
        <form method="post" action="/promo" className="campaign-create-form">{hiddenInput("action", "create")}{hiddenInput("id", "")}
          <label>Název kampaně<input name="name" required maxLength={200} /></label>
          <label>Produkt<select name="productId" required><option value="">Vyberte produkt</option>{rows(options, "products").map((product) => <option key={get(product, "id")} value={get(product, "id")} data-image={get(product, "imageUrl")}>{get(product, "name")} ({get(product, "unit")})</option>)}</select></label>
          <div className="product-preview" id="product-preview" aria-live="polite"><span>Vyberte produkt pro náhled fotografie</span><img id="product-preview-image" className="product-preview-image" alt="" hidden /></div>
          <label>Dodavatel<select name="supplierId" required><option value="">Vyberte dodavatele</option>{rows(options, "suppliers").map((supplier) => <option key={get(supplier, "id")} value={get(supplier, "id")}>{get(supplier, "name")}</option>)}</select></label>
          <div className="form-grid"><label>Začátek<input type="date" name="startsOn" required /></label><label>Konec<input type="date" name="endsOn" required /></label>
            <label>Běžná cena<input type="number" name="regularPrice" min={0} step="0.01" required /></label><label>Akční cena<input type="number" name="promoPrice" min={0} step="0.01" required /></label>
            <label>Nákupní cena<input type="number" name="supplierPurchasePrice" min={0} step="0.01" required /></label><label>Plánované množství<input type="number" name="plannedQuantity" min={0} step={1} required /></label>
            <label>Příspěvek dodavatele<input type="number" name="marketingContribution" min={0} step="0.01" defaultValue={0} required /></label>
          </div>
          <div className="dialog-actions"><button type="button" className="secondary" id="cancel-campaign">Zrušit</button><button type="submit" className="primary" id="campaign-submit">Přidat kampaň</button></div>
        </form>
      </section>
    </div>
    <InlinePageScript source={promoScript} />
  </div>;
}

function CategoryOptions({ categories, includeEmpty = true }: { categories: Row[]; includeEmpty?: boolean }) {
  return <>{includeEmpty && <option value="">Bez kategorie</option>}{categories.map((category) =>
    <option key={get(category, "id")} value={get(category, "id")}>{`${Number(get(category, "depth", "0")) > 0 ? "↳ " : ""}${get(category, "name")}`}</option>)}</>;
}

function CategoryTree({ categories, selectedCategoryId }: { categories: Row[]; selectedCategoryId: string }) {
  return <>{categories.map((category) => { const children = rows(category, "children"); const id = get(category, "id"); return <li key={id}>
    <div className="category-row"><a className={`category-filter-link ${id === selectedCategoryId ? "selected" : ""}`} href={`/ecommerce?categoryId=${encodeURIComponent(id)}#catalog`}><strong>{get(category, "name")}</strong></a><small>/{get(category, "slug")}</small>
      <div className="catalog-actions"><details><summary>Upravit</summary><form method="post" className="category-edit-form">{hiddenInput("action", "updateCategory")}{hiddenInput("categoryId", id)}{hiddenInput("parentId", get(category, "parentId"))}
        <input name="name" defaultValue={get(category, "name")} required /><input name="slug" defaultValue={get(category, "slug")} required /><button className="secondary" type="submit">Uložit</button></form></details>
        <form method="post" data-confirm-ecommerce data-confirm-message="Opravdu smazat kategorii?">{hiddenInput("action", "deleteCategory")}{hiddenInput("categoryId", id)}<button className="danger-button" type="submit">Smazat</button></form>
      </div>
    </div>{children.length > 0 && <ul><CategoryTree categories={children} selectedCategoryId={selectedCategoryId} /></ul>}
  </li>; })}</>;
}

const ecommerceScript = `(() => {
  document.body.dataset.defaultVat = document.querySelector('[data-default-vat]')?.dataset.defaultVat || '';
  document.addEventListener('submit', (event) => {
    const form = event.target;
    if (form instanceof HTMLFormElement && form.matches('[data-confirm-ecommerce]') &&
        !window.confirm(form.dataset.confirmMessage || 'Opravdu pokračovat?')) event.preventDefault();
  });
})();`;

const ecommerceInlineStyles = `.product-card-actions{display:flex;flex-direction:row;align-items:center;gap:7px;flex-wrap:wrap}.danger-button{background:#a8463d!important;border-color:#a8463d!important;color:#fff!important}.danger-button:hover{background:#8d342d!important;border-color:#8d342d!important}@media(min-width:1200px){.product-list{grid-template-columns:repeat(4,minmax(0,1fr));gap:12px}.product-card{padding:14px}.product-top{display:block}.product-card-actions{align-items:flex-start!important;margin-top:12px;flex-direction:row!important}.active-state{text-align:left}.delivery-form{grid-template-columns:repeat(2,minmax(0,1fr));gap:7px}.delivery-form button{grid-column:1/-1}.product-edit-form{grid-template-columns:1fr}.product-edit-form .secondary{grid-column:1}}`;

export function EcommercePage({ data }: ViewProps) {
  const ecommerce = data.ecommerce == null ? null : record(data.ecommerce);
  const categories = rows(ecommerce ?? {}, "categories");
  const categoryOptions = rows(data, "categoryOptions");
  const products = rows(ecommerce ?? {}, "products");
  const selectedCategoryId = get(data, "selectedCategoryId");
  return <main className="commerce-page" data-default-vat={get(data, "eshopDefaultVatRate")}>
    <style>{ecommerceInlineStyles}</style>
    <header className="commerce-header"><a href="/apps" className="back-link">← Aplikace</a><div><span className="eyebrow">OBCHOD / ECOMMERCE</span><h1>Obchod</h1><p>Navrhněte úvod, spravujte katalog a ověřte dostupnost zboží v každé lokalitě.</p></div><a href="#catalog" className="primary">Katalog produktů</a></header>
    <Messages data={data} className="commerce-message" />
    {ecommerce && <>
      <section className="commerce-grid">
        <article className="panel homepage-panel"><div className="panel-heading"><div><span className="eyebrow">ÚVODNÍ STRÁNKA</span><h2>Vzhled a sdělení</h2></div><span className="panel-note">Klikněte do náhledu nebo použijte šipky</span></div>
          <form method="post" id="homepage-form">{hiddenInput("action", "homepage")}
            <input type="hidden" name="textX" id="text-x" value={get(record(ecommerce.homepage), "textX")} /><input type="hidden" name="textY" id="text-y" value={get(record(ecommerce.homepage), "textY")} />
            <div className={`design-stage design-${get(record(ecommerce.homepage), "design").toLowerCase()}`} id="design-stage" tabIndex={0} aria-label="Náhled úvodní stránky">
              <div className="stage-copy" id="stage-copy" data-x={get(record(ecommerce.homepage), "textX")} data-y={get(record(ecommerce.homepage), "textY")}><span>Doručení ještě dnes</span><h3>{get(record(ecommerce.homepage), "headline")}</h3><p>{get(record(ecommerce.homepage), "subheadline")}</p></div>
            </div>
            <div className="editor-fields"><label>Design<select name="design" id="design-select" defaultValue={get(record(ecommerce.homepage), "design")}>{[["BOTANICAL", "Botanická sklizeň"], ["MARKET", "Městský trh"], ["MINIMAL", "Čistý minimalismus"]].map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
              <label>Nadpis<input name="headline" defaultValue={get(record(ecommerce.homepage), "headline")} maxLength={200} /></label><label>Podnadpis<input name="subheadline" defaultValue={get(record(ecommerce.homepage), "subheadline")} maxLength={500} /></label></div>
            <button className="primary" type="submit">Uložit úvodní stránku</button>
          </form>
        </article>
        <article className="panel category-panel"><div className="panel-heading"><div><span className="eyebrow">KATALOG</span><h2>Strom kategorií</h2></div><span className="panel-note">{categories.length} kořenů</span></div>
          <ul className="category-tree"><CategoryTree categories={categories} selectedCategoryId={selectedCategoryId} /></ul>
          <form method="post" className="compact-form">{hiddenInput("action", "category")}<label>Nová kategorie<input name="name" placeholder="Např. Nápoje" required /></label><label>Slug<input name="slug" placeholder="napoje" required /></label>
            <label>Nadřazená<select name="parentId" defaultValue=""><option value="">Kořen</option><CategoryOptions categories={categoryOptions} includeEmpty={false} /></select></label><button className="secondary" type="submit">Přidat kategorii</button>
          </form>
        </article>
      </section>
      <section className="product-admin panel"><div className="section-heading"><div><span className="eyebrow">SPRÁVA PRODUKTŮ</span><h2>Přidat výrobek do stromu e-shopu</h2></div><span className="panel-note">Katalog, cena, dostupnost a obrázek</span></div>
        <form method="post" className="product-form">{hiddenInput("action", "product")}
          <label>SKU<input name="sku" placeholder="NAP-001" required /></label><label>Název<input name="name" placeholder="Název produktu" required /></label><label>Jednotka<input name="unit" defaultValue="ks" required /></label>
          <label>Nákupní cena bez DPH<input name="purchasePrice" type="number" min={0} step="0.01" placeholder="0.00" required /></label><label>DPH (%)<input name="vatRate" type="number" min={0} max={100} step="0.01" placeholder="21" required /></label>
          <label>Kategorie<select name="categoryId" defaultValue=""><CategoryOptions categories={categoryOptions} /></select></label>
          <label>Obrázek produktu<input name="imageUrl" type="url" placeholder="https://.../produkt.jpg" data-image-input /></label><label className="check-field"><input type="checkbox" name="active" defaultChecked /> Aktivní v e-shopu</label>
          <label className="wide-field">Popis<textarea name="description" rows={2} placeholder="Krátký popis produktu" /></label><div className="image-preview" data-image-preview>Sem zadejte URL obrázku produktu</div><button className="primary" type="submit">Přidat produkt do e-shopu</button>
        </form>
      </section>
      <section className="catalog-section" id="catalog"><div className="section-heading"><div><span className="eyebrow">PRODUKTY A DOSTUPNOST</span><h2>Katalog napojený na inventory</h2></div><div className="catalog-filter-meta">{selectedCategoryId && <a href="/ecommerce#catalog" className="clear-filter">Zobrazit všechny produkty</a>}<span className="panel-note">{products.length} produktů</span></div></div>
        <div className="product-list">{products.map((product) => <article className="product-card" id={`product-${get(product, "id")}`} key={get(product, "id")}>
          <div className="product-top"><div>{get(product, "imageUrl") && <img className="product-image" src={get(product, "imageUrl")} alt={get(product, "name")} />}<span className="sku">{get(product, "sku")}</span><h3>{get(product, "name")}</h3><p>{get(product, "description")}</p></div>
            <div><strong className="price">{get(product, "price")} Kč / {get(product, "unit")}</strong><span className={`active-state ${isTrue(product, "active") ? "is-active" : "is-inactive"}`}>{isTrue(product, "active") ? "AKTIVNÍ" : "SKRYTÝ"}</span></div></div>
          <div className="availability">{rows(product, "availability").map((stock, index) => <div className="stock-row" key={`${get(stock, "locationName")}-${index}`}><span>{get(stock, "locationName")}</span><span className={`stock-${get(stock, "stockStatus").toLowerCase()}`}>{get(stock, "quantity")} {get(product, "unit")} · {get(stock, "stockStatus") === "AVAILABLE" ? "Dostupné" : get(stock, "stockStatus") === "LOW" ? "Nízká zásoba" : "Vyprodáno"}</span></div>)}</div>
          <details className="delivery-options"><summary>Dostupnost a doručení</summary><form method="post" className="delivery-form">{hiddenInput("action", "estimate")}{hiddenInput("productId", get(product, "id"))}<label>Množství<input type="number" name="quantity" defaultValue={1} min={1} /></label><label>PSČ<input name="postalCode" placeholder="60200" pattern="[0-9]{5}" required /></label><label>Způsob<select name="method"><option value="HOME">Domů</option><option value="BOX">Box</option></select></label><button className="secondary" type="submit">Zjistit doručení</button></form></details>
          <details className="product-edit"><summary>Upravit produkt</summary><form method="post" className="product-edit-form">{hiddenInput("action", "product")}{hiddenInput("productId", get(product, "id"))}
            <label>SKU<input name="sku" defaultValue={get(product, "sku")} required /></label><label>Název<input name="name" defaultValue={get(product, "name")} required /></label><label>Cena<input name="price" type="number" min={0} step="0.01" defaultValue={get(product, "price")} required /></label>
            <label>Kategorie<select name="categoryId" defaultValue={get(product, "categoryId")}><CategoryOptions categories={categoryOptions} /></select></label><label>Obrázek<input name="imageUrl" type="url" defaultValue={get(product, "imageUrl")} /></label><label className="check-field"><input type="checkbox" name="active" defaultChecked={isTrue(product, "active")} /> Aktivní v e-shopu</label>
            {hiddenInput("unit", get(product, "unit"))}{hiddenInput("description", get(product, "description"))}<button className="secondary" type="submit">Uložit změny</button>
          </form></details>
        </article>)}</div>
      </section>
      <section className="category-status-panel panel"><div className="section-heading"><div><span className="eyebrow">VIDITELNOST KATEGORIE</span><h2>Aktivní produkty</h2></div><span className="panel-note">Přepínač určuje, zda se produkt zobrazí v e-shopu</span></div>
        <div className="catalog-product-actions">{products.map((product) => <form method="post" className="product-status-row" key={get(product, "id")}>{hiddenInput("action", "toggleProduct")}{hiddenInput("productId", get(product, "id"))}{hiddenInput("active", isTrue(product, "active") ? "" : "on")}
          <strong>{get(product, "name")}</strong><span className={`active-state ${isTrue(product, "active") ? "is-active" : "is-inactive"}`}>{isTrue(product, "active") ? "AKTIVNÍ" : "NEAKTIVNÍ"}</span><button className="secondary" type="submit">{isTrue(product, "active") ? "Nastavit neaktivní" : "Nastavit aktivní"}</button></form>)}</div>
      </section>
      <section className="pricing-panel panel"><div className="section-heading"><div><span className="eyebrow">CENOTVORBA</span><h2>Nákupní ceny a DPH</h2></div><span className="panel-note">Globální marže: {data.eshopMarginPercent == null ? "-" : Number(text(data.eshopMarginPercent)).toFixed(2)} %</span></div>
        <div className="catalog-product-actions">{products.map((product) => { const price = Number(get(product, "price")); const vat = Number(get(product, "vatRate", "0")); return <form method="post" className="product-pricing-form" key={get(product, "id")}>{hiddenInput("action", "product")}{hiddenInput("productId", get(product, "id"))}
          {["sku", "name", "unit", "description", "categoryId", "imageUrl"].map((field) => hiddenInput(field, get(product, field)))}{hiddenInput("active", isTrue(product, "active") ? "on" : "")}
          <strong>{get(product, "name")}</strong><label>Nákupní cena bez DPH<input name="purchasePrice" type="number" min={0} step="0.01" defaultValue={get(product, "purchasePrice")} required /></label>
          <label>Marže (%)<input name="eshopMarginPercent" type="number" min={0} max="99.98" step="0.01" defaultValue={Number(get(product, "eshopMarginPercent", text(data.eshopMarginPercent))).toFixed(2)} required /></label>
          <label>DPH (%)<input name="vatRate" type="number" min={0} max={100} step="0.01" defaultValue={get(product, "vatRate", "21")} required /></label>
          <span>Prodejní cena bez DPH: <strong>{Number.isFinite(price) ? (price / (1 + vat / 100)).toFixed(2) : "-"} Kč</strong></span><span>Cena s DPH: <strong>{Number.isFinite(price) ? price.toFixed(2) : "-"} Kč</strong></span><button className="secondary" type="submit">Přepočítat cenu</button>
        </form>; })}</div>
      </section>
      <section className="catalog-actions-panel panel"><div className="section-heading"><div><span className="eyebrow">GALERIE OBRÁZKŮ</span><h2>Obrázky produktů</h2></div><span className="panel-note">Jeden obrázek je aktivní pro nové faktury</span></div>
        <div className="catalog-product-actions">{products.map((product) => <div key={get(product, "id")}><strong>{get(product, "name")}</strong><div>{rows(product, "images").map((image) => <span key={get(image, "id")}>
          <img className="product-image" src={get(image, "imageUrl")} alt={get(product, "name")} width={72} />
          <form method="post" style={{ display: "inline" }}>{hiddenInput("action", "activateImage")}{hiddenInput("productId", get(product, "id"))}{hiddenInput("imageId", get(image, "id"))}<button className="secondary" type="submit">{isTrue(image, "active") ? "Aktivní" : "Nastavit jako aktivní"}</button></form>
          <form method="post" style={{ display: "inline" }}>{hiddenInput("action", "deleteImage")}{hiddenInput("productId", get(product, "id"))}{hiddenInput("imageId", get(image, "id"))}<button className="danger-button" type="submit">Odstranit</button></form>
        </span>)}</div><form method="post">{hiddenInput("action", "addImage")}{hiddenInput("productId", get(product, "id"))}<input name="imageUrl" type="url" placeholder="https://.../produkt.jpg" required /><label><input type="checkbox" name="active" /> Aktivní</label><button className="secondary" type="submit">Přidat obrázek</button></form></div>)}</div>
      </section>
      <section className="catalog-actions-panel panel"><div className="section-heading"><div><span className="eyebrow">SPRÁVA KATALOGU</span><h2>Přejmenovat nebo smazat výrobek</h2></div></div>
        <div className="catalog-product-actions">{products.map((product) => <div key={get(product, "id")}><span>{get(product, "name")} <small>({get(product, "sku")})</small></span><form method="post" data-confirm-ecommerce data-confirm-message="Opravdu smazat produkt?">{hiddenInput("action", "deleteProduct")}{hiddenInput("productId", get(product, "id"))}<button className="danger-button" type="submit">Smazat</button></form></div>)}</div>
      </section>
      <section className="import-section"><div><span className="eyebrow">IMPORT PRODUKTŮ</span><h2>Import katalogu</h2></div><form method="post">{hiddenInput("action", "import")}<textarea name="products" rows={8} defaultValue="[]" /><button className="primary" type="submit">Importovat produkty</button></form></section>
    </>}
    <InlinePageScript source={ecommerceScript} />
  </main>;
}
