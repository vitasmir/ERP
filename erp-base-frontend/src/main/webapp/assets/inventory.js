(() => {
  const buttons = document.querySelectorAll('[data-inventory-view]');
  const panels = document.querySelectorAll('.inventory-view-panel');

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
})();