(() => {
  const buttons = document.querySelectorAll("[data-inventory-view]");
  const panels = document.querySelectorAll(".inventory-view-panel");
  const warehouseSelect = document.getElementById("warehouse-select");
  const warehouseRows = document.querySelectorAll("[data-warehouse]");
  const search = document.getElementById("inventory-search");
  const lowStock = document.getElementById("inventory-low-stock");
  const filterToolbar = document.querySelector(".inventory-filters");
  const filterCount = document.getElementById("inventory-filter-count");
  const stockRows = document.querySelectorAll("[data-stock-row]");
  const productCards = document.querySelectorAll("[data-product-card]");
  const productImages = document.querySelectorAll(
    ".stock-item-image img, .warehouse-product-media img",
  );
  const setupImagePopup = (thumbnail) => {
    thumbnail.dataset.image = thumbnail.currentSrc || thumbnail.src;
    thumbnail.style.cursor = "zoom-in";
    let popup;
    const removePopup = () => {
      popup?.remove();
      popup = undefined;
    };
    thumbnail.addEventListener("mouseenter", () => {
      if (!thumbnail.dataset.image) return;
      removePopup();
      popup = document.createElement("div");
      popup.className = "product-image-popup";
      popup.setAttribute("role", "img");
      popup.setAttribute("aria-label", thumbnail.alt);
      const fullImage = document.createElement("img");
      fullImage.src = thumbnail.dataset.image;
      fullImage.alt = thumbnail.alt;
      popup.appendChild(fullImage);
      document.body.appendChild(popup);
      const bounds = thumbnail.getBoundingClientRect();
      const popupBounds = popup.getBoundingClientRect();
      const left = Math.max(
        16,
        Math.min(
          bounds.left + (bounds.width - popupBounds.width) / 2,
          window.innerWidth - popupBounds.width - 16,
        ),
      );
      const aboveTop = bounds.top - popupBounds.height - 14;
      popup.style.left = `${left}px`;
      popup.style.top = `${Math.max(16, aboveTop)}px`;
    });
    thumbnail.addEventListener("mouseleave", removePopup);
  };
  productImages.forEach(setupImagePopup);

  const updateWarehouse = () => {
    const selectedWarehouse = warehouseSelect ? warehouseSelect.value : "";
    warehouseRows.forEach((row) => {
      row.hidden = row.dataset.warehouse !== selectedWarehouse;
    });
  };

  const normalize = (value) =>
    value.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLocaleLowerCase("cs");

  const updateFilters = () => {
    const query = normalize(search?.value.trim() || "");
    const onlyLowStock = lowStock?.checked || false;
    const apply = (element) => {
      const matches = normalize(element.dataset.search || "").includes(query);
      element.hidden = !matches || (onlyLowStock && element.dataset.lowStock !== "true");
    };
    stockRows.forEach(apply);
    productCards.forEach(apply);
    document.querySelectorAll(".category-branch").forEach((branch) => {
      branch.hidden = !Array.from(branch.querySelectorAll("[data-stock-row]"))
        .some((row) => !row.hidden);
    });
    const active = document.querySelector("[data-inventory-view].active");
    const visibleItems = active?.dataset.inventoryView === "products" ? productCards : stockRows;
    if (filterCount) {
      filterCount.textContent = `Zobrazeno ${Array.from(visibleItems).filter((item) => !item.hidden).length} položek`;
    }
  };

  search?.addEventListener("input", updateFilters);
  lowStock?.addEventListener("change", updateFilters);

  const selectView = (button) => {
    const selectedView = button.dataset.inventoryView;
    buttons.forEach((item) => {
      const active = item === button;
      item.classList.toggle("active", active);
      item.setAttribute("aria-selected", String(active));
      item.tabIndex = active ? 0 : -1;
    });
    panels.forEach((panel) => {
      panel.hidden = panel.id !== selectedView;
    });
    if (filterToolbar) filterToolbar.hidden = selectedView === "history";
    const url = new URL(window.location.href);
    url.searchParams.set("view", selectedView);
    window.history.replaceState(null, "", url);
    updateFilters();
  };

  buttons.forEach((button) =>
    button.addEventListener("click", () => {
      selectView(button);
    }),
  );

  buttons.forEach((button, index) => {
    button.addEventListener("keydown", (event) => {
      const keys = ["ArrowRight", "ArrowLeft", "Home", "End"];
      if (!keys.includes(event.key)) return;
      event.preventDefault();
      const next = event.key === "Home" ? 0 : event.key === "End" ? buttons.length - 1
        : (index + (event.key === "ArrowRight" ? 1 : -1) + buttons.length) % buttons.length;
      selectView(buttons[next]);
      buttons[next].focus();
    });
  });

  document.querySelectorAll(".stock-movement-form").forEach((form) => {
    const action = form.querySelector("[data-stock-action]");
    const quantity = form.querySelector('[name="quantity"]');
    const submit = form.querySelector('button[type="submit"]');
    const updateAction = () => {
      const dispatch = action.value === "dispatch";
      quantity.max = dispatch ? quantity.dataset.available : "2147483647";
      submit.textContent = dispatch ? "Zaúčtovat výdej" : "Zaúčtovat příjem";
      Array.from(form.elements).forEach((input) => {
        if (input.name === "reorderLevel" || input.name === "unitCost") {
          input.disabled = dispatch;
        }
      });
    };
    action.addEventListener("change", updateAction);
    updateAction();
  });

  document.querySelectorAll("form[method='post']").forEach((form) => {
    form.addEventListener("submit", (event) => {
      if (form.dataset.submitting === "true") {
        event.preventDefault();
        return;
      }
      form.dataset.submitting = "true";
      const button = form.querySelector('button[type="submit"]');
      if (button) {
        button.disabled = true;
        button.textContent = "Odesílám…";
      }
    });
  });

  if (warehouseSelect) {
    warehouseSelect.addEventListener("change", updateWarehouse);
    updateWarehouse();
  }
  updateFilters();
})();
