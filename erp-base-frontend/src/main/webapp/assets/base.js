document.getElementById('user-search')?.addEventListener('input', (event) => {
  const query = event.target.value.toLowerCase();
  document.querySelectorAll('#user-table tr').forEach((row) => row.classList.toggle('hidden', !row.textContent.toLowerCase().includes(query)));
});
const companyModal = document.getElementById('company-modal');
const closeCompanyDialog = () => {
  companyModal?.classList.remove('open');
  companyModal?.setAttribute('aria-hidden', 'true');
  document.getElementById('company-form')?.reset();
};
document.getElementById('add-company')?.addEventListener('click', () => {
  companyModal?.classList.add('open');
  companyModal?.setAttribute('aria-hidden', 'false');
  companyModal?.querySelector('input')?.focus();
});
document.getElementById('close-company-dialog')?.addEventListener('click', closeCompanyDialog);
document.getElementById('cancel-company-dialog')?.addEventListener('click', closeCompanyDialog);
companyModal?.querySelector('.company-modal-backdrop')?.addEventListener('click', closeCompanyDialog);
const userModal = document.getElementById('user-modal');
const userForm = document.getElementById('user-form');
const openUserDialog = (user) => {
  document.getElementById('user-action').value = user ? 'update' : 'create';
  document.getElementById('user-id').value = user?.dataset.userId || '';
  const employeeSelect = document.getElementById('user-employee-id');
  if (employeeSelect) {
    employeeSelect.value = user?.dataset.employeeId || '';
    employeeSelect.querySelectorAll('option[data-has-account="true"]').forEach((option) => {
      option.disabled = option.value !== user?.dataset.employeeId;
    });
  }
  document.getElementById('user-full-name').value = user?.dataset.fullName || '';
  const usernameInput = document.getElementById('user-username');
  usernameInput.value = user?.dataset.username || '';
  usernameInput.readOnly = Boolean(user);
  const passwordInput = document.getElementById('user-password');
  passwordInput.value = '';
  passwordInput.required = !user;
  document.getElementById('user-role-name').value = user?.dataset.roleName || '';
  document.getElementById('user-company-name').value = user?.dataset.companyName || '';
  document.getElementById('user-status').value = user?.dataset.status || 'ACTIVE';
  document.getElementById('user-dialog-title').textContent = user ? 'Upravit uživatele' : 'Nový uživatel';
  document.getElementById('save-user').textContent = user ? 'Uložit změny' : 'Přidat uživatele';
  userModal?.classList.add('open');
  userModal?.setAttribute('aria-hidden', 'false');
  document.getElementById('user-full-name')?.focus();
};
const closeUserDialog = () => {
  userModal?.classList.remove('open');
  userModal?.setAttribute('aria-hidden', 'true');
  userForm?.reset();
};
document.getElementById('add-user')?.addEventListener('click', () => openUserDialog());
document.getElementById('close-user-dialog')?.addEventListener('click', closeUserDialog);
document.getElementById('cancel-user-dialog')?.addEventListener('click', closeUserDialog);
userModal?.querySelector('.user-modal-backdrop')?.addEventListener('click', closeUserDialog);
document.querySelectorAll('.edit-user').forEach((button) => button.addEventListener('click', () => openUserDialog(button.closest('tr'))));
const requestedEmployeeId = userModal?.dataset.requestedEmployeeId;
if (requestedEmployeeId) {
  openUserDialog();
  const employeeSelect = document.getElementById('user-employee-id');
  employeeSelect.value = requestedEmployeeId;
  const employeeOption = employeeSelect.options[employeeSelect.selectedIndex];
  if (employeeOption) document.getElementById('user-full-name').value = employeeOption.textContent.split(' · ')[0];
}
document.querySelectorAll('.delete-user-form').forEach((form) => form.addEventListener('submit', (event) => {
  if (!window.confirm('Opravdu chcete tohoto uživatele smazat?')) event.preventDefault();
}));

const roleModal = document.getElementById('role-modal');
const roleForm = document.getElementById('role-form');
let roleBeingEdited;
const roleCards = () => [...document.querySelectorAll('.role-card')];
const updateRoleForm = (card) => {
  roleBeingEdited = card;
  document.getElementById('role-name').value = card?.querySelector('h3')?.textContent || '';
  document.getElementById('role-initial').value = card?.querySelector('.role-mark')?.textContent.trim() || '';
  document.getElementById('role-description').value = card?.querySelector('p')?.textContent || '';
};
const ensureRoleSelector = () => {
  let selector = document.getElementById('role-edit-select');
  if (selector) return selector;
  const label = document.createElement('label');
  label.id = 'role-edit-field';
  label.textContent = 'Role k úpravě';
  selector = document.createElement('select');
  selector.id = 'role-edit-select';
  selector.required = true;
  label.appendChild(selector);
  roleForm?.prepend(label);
  selector.addEventListener('change', () => updateRoleForm(roleCards()[Number(selector.value)]));
  return selector;
};
const openRoleDialog = (edit = false) => {
  const selector = document.getElementById('role-edit-field');
  selector?.remove();
  roleBeingEdited = undefined;
  if (edit) {
    const editSelector = ensureRoleSelector();
    roleCards().forEach((card, index) => {
      const option = document.createElement('option');
      option.value = index;
      option.textContent = card.querySelector('h3')?.textContent || 'Role';
      editSelector.appendChild(option);
    });
    updateRoleForm(roleCards()[0]);
    document.getElementById('role-dialog-title').textContent = 'Upravit roli';
    roleForm.querySelector('button[type="submit"]').textContent = 'Uložit změny';
  } else {
    document.getElementById('role-dialog-title').textContent = 'Nová role';
    roleForm.querySelector('button[type="submit"]').textContent = 'Vytvořit roli';
  }
  roleModal?.classList.add('open');
  roleModal?.setAttribute('aria-hidden', 'false');
  document.getElementById('role-name')?.focus();
};
const closeRoleDialog = () => {
  roleModal?.classList.remove('open');
  roleModal?.setAttribute('aria-hidden', 'true');
  roleForm?.reset();
  roleBeingEdited = undefined;
};
document.getElementById('add-role')?.addEventListener('click', () => openRoleDialog());
document.getElementById('edit-role')?.addEventListener('click', () => openRoleDialog(true));
document.getElementById('close-role-dialog')?.addEventListener('click', closeRoleDialog);
document.getElementById('cancel-role-dialog')?.addEventListener('click', closeRoleDialog);
roleModal?.querySelector('.role-modal-backdrop')?.addEventListener('click', closeRoleDialog);
roleForm?.addEventListener('submit', (event) => {
  event.preventDefault();
  const name = document.getElementById('role-name').value.trim();
  const initial = document.getElementById('role-initial').value.trim().toUpperCase();
  const description = document.getElementById('role-description').value.trim() || 'Nová pracovní role v ERP systému.';
  if (roleBeingEdited) {
    roleBeingEdited.querySelector('.role-mark').textContent = initial;
    roleBeingEdited.querySelector('h3').textContent = name;
    roleBeingEdited.querySelector('p').textContent = description;
    const selector = document.getElementById('role-edit-select');
    if (selector) selector.options[selector.selectedIndex].textContent = name;
  } else {
    const card = document.createElement('article');
    card.className = 'role-card';
    card.innerHTML = `<span class="role-mark custom"></span><h3></h3><p></p><b>0 uživatelů</b>`;
    card.querySelector('.role-mark').textContent = initial;
    card.querySelector('h3').textContent = name;
    card.querySelector('p').textContent = description;
    document.querySelector('.role-grid')?.appendChild(card);
  }
  closeRoleDialog();
});

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
  } else if (module === 'pos') {
    window.location.assign('pos');
  } else if (module === 'hr') {
    window.location.assign('hr');
  } else if (module === 'documents') {
    window.location.assign('documents');
  } else if (module === 'project') {
    window.location.assign('projects');
  } else if (module === 'helpdesk') {
    window.location.assign('helpdesk');
  } else if (module === 'website') {
    window.location.assign('website');
  } else if (module === 'ecommerce') {
    window.location.assign('ecommerce');
  } else if (module === 'marketing') {
    window.location.assign('marketing');
  } else if (module === 'planning') {
    window.location.assign('planning');
  } else if (module === 'settings') {
    window.location.assign('settings');
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
