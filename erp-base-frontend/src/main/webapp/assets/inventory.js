(() => {
  const buttons = document.querySelectorAll('[data-inventory-view]');
  const panels = document.querySelectorAll('.inventory-view-panel');
  const warehouseSelect = document.getElementById('warehouse-select');
  const warehouseRows = document.querySelectorAll('[data-warehouse]');

  const updateWarehouse = () => {
    const selectedWarehouse = warehouseSelect ? warehouseSelect.value : '';
    warehouseRows.forEach((row) => {
      row.hidden = row.dataset.warehouse !== selectedWarehouse;
    });
  };

  buttons.forEach((button) => button.addEventListener('click', () => {
    const selectedView = button.dataset.inventoryView;
    buttons.forEach((item) => {
      const active = item === button;
      item.classList.toggle('active', active);
      item.setAttribute('aria-selected', active ? 'true' : 'false');
    });
    panels.forEach((panel) => {
      panel.hidden = panel.id !== selectedView;
    });
  }));

  if (warehouseSelect) {
    warehouseSelect.addEventListener('change', updateWarehouse);
    updateWarehouse();
  }
})();