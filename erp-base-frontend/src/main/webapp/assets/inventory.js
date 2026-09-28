(() => {
  const buttons = document.querySelectorAll("[data-inventory-view]");
  const panels = document.querySelectorAll(".inventory-view-panel");
  const warehouseSelect = document.getElementById("warehouse-select");
  const warehouseRows = document.querySelectorAll("[data-warehouse]");
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

  buttons.forEach((button) =>
    button.addEventListener("click", () => {
      const selectedView = button.dataset.inventoryView;
      buttons.forEach((item) => {
        const active = item === button;
        item.classList.toggle("active", active);
        item.setAttribute("aria-selected", active ? "true" : "false");
      });
      panels.forEach((panel) => {
        panel.hidden = panel.id !== selectedView;
      });
    }),
  );

  if (warehouseSelect) {
    warehouseSelect.addEventListener("change", updateWarehouse);
    updateWarehouse();
  }
})();
