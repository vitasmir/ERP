document.getElementById('user-search')?.addEventListener('input', (event) => {
  const query = event.target.value.toLowerCase();
  document.querySelectorAll('#user-table tr').forEach((row) => row.classList.toggle('hidden', !row.textContent.toLowerCase().includes(query)));
});
document.getElementById('add-user')?.addEventListener('click', () => window.alert('Formulář nového uživatele bude napojen na /api/v1/users v další iteraci modulu base.'));

const drawer = document.getElementById('module-drawer');
const backdrop = document.createElement('div');
backdrop.className = 'drawer-backdrop';
document.body.appendChild(backdrop);
const moduleTiles = document.querySelectorAll('.module-tile');
const openModule = (tile) => {
  drawer.querySelector('.drawer-icon').className = `drawer-icon module-icon ${tile.querySelector('.module-icon').className.split(' ').slice(1).join(' ')}`;
  document.getElementById('drawer-title').textContent = tile.dataset.title;
  document.getElementById('drawer-subtitle').textContent = tile.dataset.subtitle;
  document.getElementById('drawer-description').textContent = tile.dataset.description;
  drawer.dataset.module = tile.dataset.module;
  drawer.classList.add('open');
  backdrop.classList.add('visible');
  drawer.setAttribute('aria-hidden', 'false');
};
const closeModule = () => {
  drawer?.classList.remove('open');
  backdrop.classList.remove('visible');
  drawer?.setAttribute('aria-hidden', 'true');
};
moduleTiles.forEach((tile) => tile.addEventListener('click', () => openModule(tile)));
document.getElementById('drawer-close')?.addEventListener('click', closeModule);
backdrop.addEventListener('click', closeModule);
document.addEventListener('keydown', (event) => {
  if (event.key === 'Escape') closeModule();
});
document.getElementById('drawer-action')?.addEventListener('click', () => {
  const module = drawer.dataset.module;
  if (module === 'base') {
    window.location.assign('users');
  } else if (module === 'settings') {
    window.location.assign('settings');
  } else if (module === 'crm') {
    window.location.assign('crm');
  } else if (module === 'sales') {
    window.location.assign('sales');
  } else if (module === 'purchase') {
    window.location.assign('purchase');
  } else if (module === 'inventory') {
    window.location.assign('inventory');
  } else if (module === 'manufacturing') {
    window.location.assign('manufacturing');
  } else if (module === 'accounting') {
    window.location.assign('accounting');
  } else if (module === 'promotions') {
    window.location.assign('promo');
  } else if (module === 'dashboard') {
    window.location.assign('dashboard');
  } else {
    window.alert(`Modul ${document.getElementById('drawer-title').textContent} bude napojen v další iteraci.`);
  }
});
const moduleSearch = document.getElementById('module-search');
const moduleFilters = document.querySelectorAll('.module-filter');
const filterModules = () => {
  const query = moduleSearch.value.toLowerCase().trim();
  const filter = document.querySelector('.module-filter.active').dataset.filter;
  let visible = 0;
  moduleTiles.forEach((tile) => {
    const matchesQuery = `${tile.dataset.title} ${tile.dataset.subtitle} ${tile.dataset.description}`.toLowerCase().includes(query);
    const matchesFilter = filter === 'all' || tile.dataset.category === filter;
    const isVisible = matchesQuery && matchesFilter;
    tile.hidden = !isVisible;
    if (isVisible) visible += 1;
  });
  document.getElementById('module-empty').style.display = visible ? 'none' : 'block';
};
moduleSearch?.addEventListener('input', filterModules);
moduleFilters.forEach((filter) => filter.addEventListener('click', () => {
  moduleFilters.forEach((item) => item.classList.toggle('active', item === filter));
  filterModules();
}));
