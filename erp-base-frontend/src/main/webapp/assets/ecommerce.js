(() => {
  const stage = document.getElementById("design-stage");
  const copy = document.getElementById("stage-copy");
  const xField = document.getElementById("text-x");
  const yField = document.getElementById("text-y");
  const design = document.getElementById("design-select");
  document.querySelectorAll("[data-image-input]").forEach((input) => {
    const preview = input.closest("form").querySelector("[data-image-preview]");
    input.addEventListener("input", () => {
      preview.textContent = "";
      if (!input.value) {
        preview.textContent = "Sem zadejte URL obrázku produktu";
        return;
      }
      const image = document.createElement("img");
      image.src = input.value;
      image.alt = "Náhled produktu";
      image.onerror = () => {
        preview.textContent = "Obrázek se nepodařilo načíst";
      };
      preview.appendChild(image);
    });
  });
  const defaultVat = document.body.dataset.defaultVat;
  const newProductVat = document.querySelector('form input[name="vatRate"][placeholder="21"]');
  if (newProductVat && defaultVat) newProductVat.value = defaultVat;
  document.querySelectorAll(".product-image").forEach((thumbnail) => {
    let popup;
    const removePopup = () => {
      popup?.remove();
      popup = undefined;
    };
    const showPopup = () => {
      removePopup();
      popup = document.createElement("div");
      popup.className = "product-image-popup";
      popup.setAttribute("role", "img");
      popup.setAttribute("aria-label", thumbnail.alt);
      const fullImage = document.createElement("img");
      fullImage.src = thumbnail.currentSrc || thumbnail.src;
      fullImage.alt = thumbnail.alt;
      popup.appendChild(fullImage);
      document.body.appendChild(popup);
      const positionPopup = () => {
        if (!popup) return;
        const bounds = thumbnail.getBoundingClientRect();
        const popupBounds = popup.getBoundingClientRect();
        const left = Math.max(
          16,
          Math.min(
            bounds.left + (bounds.width - popupBounds.width) / 2,
            window.innerWidth - popupBounds.width - 16,
          ),
        );
        popup.style.left = `${left}px`;
        popup.style.top = `${Math.max(16, bounds.top - popupBounds.height - 14)}px`;
      };
      fullImage.addEventListener("load", positionPopup, { once: true });
      if (fullImage.complete) positionPopup();
    };
    thumbnail.style.cursor = "zoom-in";
    thumbnail.addEventListener("mouseenter", showPopup);
    thumbnail.addEventListener("mouseleave", removePopup);
  });
  document.querySelectorAll(".danger-button").forEach((button) => {
    button.style.backgroundColor = "#a8463d";
    button.style.borderColor = "#a8463d";
    button.style.color = "#fff";
  });
  document.querySelectorAll(".product-card .active-state").forEach((state) => {
    const card = state.closest(".product-card");
    const productId = card?.id.replace("product-", "");
    if (!productId) return;
    const active = state.classList.contains("is-active");
    const form = document.createElement("form");
    form.method = "post";
    form.className = "product-visibility-form";
    form.innerHTML = `<input type="hidden" name="action" value="toggleProduct"><input type="hidden" name="productId" value="${productId}"><input type="hidden" name="active" value="${active ? "" : "on"}"><button class="secondary" type="submit">${active ? "Skrýt" : "Zobrazit"}</button>`;
    state.replaceWith(form);
  });
  document.querySelectorAll(".product-card").forEach((card) => {
    const categorySelect = card.querySelector('.product-edit-form select[name="categoryId"]');
    const sku = card.querySelector(".sku");
    if (!categorySelect || !sku) return;
    const category = document.createElement("span");
    category.className = "product-category";
    category.textContent = `Kategorie: ${categorySelect.selectedOptions[0]?.textContent.trim() || "Bez kategorie"}`;
    sku.insertAdjacentElement("afterend", category);
    const editSummary = card.querySelector(".product-edit summary");
    if (editSummary) editSummary.textContent = "Kategorie a úprava";
  });
  const categoryCounts = new Map();
  document.querySelectorAll('.product-card .product-edit-form select[name="categoryId"]').forEach((select) => {
    if (!select.value) return;
    categoryCounts.set(select.value, (categoryCounts.get(select.value) || 0) + 1);
  });
  document.querySelectorAll(".category-filter-link").forEach((link) => {
    const categoryId = new URL(link.href, window.location.href).searchParams.get("categoryId");
    const count = document.createElement("span");
    count.className = "category-count";
    count.textContent = ` (${categoryCounts.get(categoryId) || 0})`;
    link.appendChild(count);
  });
  const catalogPanels = [...document.querySelectorAll(".catalog-actions-panel")];
  const galleryPanel = catalogPanels[0];
  const galleryRows = [...(galleryPanel?.querySelectorAll(".catalog-product-actions > div") ?? [])];
  const productCards = [...document.querySelectorAll(".product-card")];
  galleryRows.forEach((row, index) => {
    const card = productCards[index];
    if (!card) return;
    row.classList.add("product-gallery-content");
    card.appendChild(row);
  });
  galleryPanel?.remove();
  const catalogManagement = catalogPanels[1];
  const deleteRows = [...(catalogManagement?.querySelectorAll(".catalog-product-actions > div") ?? [])];
  deleteRows.forEach((row, index) => {
    const card = productCards[index];
    const form = row.querySelector("form");
    const priceColumn = card?.querySelector(".product-top > div:last-child");
    if (!card || !form || !priceColumn) return;
    priceColumn.classList.add("product-card-actions");
    priceColumn.style.display = "flex";
    priceColumn.style.flexDirection = "row";
    priceColumn.style.alignItems = "center";
    priceColumn.style.flexWrap = "wrap";
    priceColumn.style.gap = "7px";
    form.classList.add("product-delete-form");
    const productId = form.querySelector('input[name="productId"]')?.value;
    const categorySelect = card.querySelector('.product-edit-form select[name="categoryId"]');
    if (productId && categorySelect?.value) {
      const removeForm = document.createElement("form");
      removeForm.method = "post";
      removeForm.className = "product-remove-category-form";
      const action = document.createElement("input");
      action.type = "hidden";
      action.name = "action";
      action.value = "removeFromCategory";
      const id = document.createElement("input");
      id.type = "hidden";
      id.name = "productId";
      id.value = productId;
      const removeButton = document.createElement("button");
      removeButton.type = "submit";
      removeButton.className = "secondary";
      removeButton.textContent = "Odebrat z kategorie";
      removeForm.append(action, id, removeButton);
      priceColumn.appendChild(removeForm);
    }
    priceColumn.appendChild(form);
  });
  catalogManagement?.remove();
  if (!stage || !copy) return;
  const setPosition = (x, y) => {
    x = Math.max(0, Math.min(100, x));
    y = Math.max(0, Math.min(100, y));
    copy.style.left = `${x}%`;
    copy.style.top = `${y}%`;
    xField.value = x.toFixed(2);
    yField.value = y.toFixed(2);
  };
  setPosition(Number(copy.dataset.x), Number(copy.dataset.y));
  stage.addEventListener("click", (event) => {
    const box = stage.getBoundingClientRect();
    setPosition(
      ((event.clientX - box.left) / box.width) * 100,
      ((event.clientY - box.top) / box.height) * 100,
    );
    stage.focus();
  });
  stage.addEventListener("keydown", (event) => {
    const step = event.shiftKey ? 5 : 1;
    let x = Number(xField.value);
    let y = Number(yField.value);
    if (event.key === "ArrowLeft") x -= step;
    else if (event.key === "ArrowRight") x += step;
    else if (event.key === "ArrowUp") y -= step;
    else if (event.key === "ArrowDown") y += step;
    else return;
    event.preventDefault();
    setPosition(x, y);
  });
  design?.addEventListener("change", () => {
    stage.className = `design-stage design-${design.value.toLowerCase()}`;
  });
})();
