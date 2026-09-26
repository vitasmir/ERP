document.getElementById('user-search')?.addEventListener('input', (event) => {
  const query = event.target.value.toLowerCase();
  document.querySelectorAll('#user-table tr').forEach((row) => row.classList.toggle('hidden', !row.textContent.toLowerCase().includes(query)));
});
const ensureColorPicker = (form, id, defaultColor) => {
  if (!form || document.getElementById(id)) return document.getElementById(id);
  const label = document.createElement('label');
  label.className = 'color-picker-field';
  label.textContent = 'Barva';
  const input = document.createElement('input');
  input.type = 'color';
  input.name = 'color';
  input.id = id;
  input.value = defaultColor;
  label.appendChild(input);
  form.insertBefore(label, form.querySelector('.dialog-actions'));
  return input;
};
ensureColorPicker(document.getElementById('role-form'), 'role-color', '#D9ED62');
ensureColorPicker(document.getElementById('company-form'), 'company-color', '#D9ED62');
ensureColorPicker(document.getElementById('user-form'), 'user-color', '#DCE9D7');
const companyModal = document.getElementById('company-modal');
const companyForm = document.getElementById('company-form');
const companyCards = () => [...document.querySelectorAll('.company-grid article')];
const ensureCompanySelector = () => {
  let selector = document.getElementById('company-edit-field');
  if (selector) return selector.querySelector('select');
  const label = document.createElement('label');
  label.id = 'company-edit-field';
  label.textContent = 'Společnost k úpravě';
  const select = document.createElement('select');
  select.id = 'company-edit-select';
  select.required = true;
  label.appendChild(select);
  companyForm?.prepend(label);
  return select;
};
const openCompanyDialog = (edit = false) => {
  document.getElementById('company-edit-field')?.remove();
  companyForm?.reset();
  document.getElementById('company-action').value = edit ? 'update' : 'create';
  document.getElementById('company-id').value = '';
  if (edit) {
    const selector = ensureCompanySelector();
    companyCards().forEach((card, index) => {
      const option = document.createElement('option');
      option.value = index;
      option.textContent = card.dataset.name;
      selector.appendChild(option);
    });
    const fillCompany = (card) => {
      document.getElementById('company-id').value = card.dataset.companyId;
      document.getElementById('company-name').value = card.dataset.name;
      document.getElementById('company-type').value = card.dataset.type;
      document.getElementById('company-currency').value = card.dataset.currency;
      document.getElementById('company-status').value = card.dataset.status;
      document.getElementById('company-color').value = card.dataset.color || '#D9ED62';
    };
    selector.addEventListener('change', () => fillCompany(companyCards()[Number(selector.value)]));
    fillCompany(companyCards()[0]);
    document.getElementById('company-dialog-title').textContent = 'Upravit společnost';
    document.getElementById('company-dialog-description').textContent = 'Upravte údaje organizační jednotky.';
    document.getElementById('save-company').textContent = 'Uložit změny';
  } else {
    document.getElementById('company-dialog-title').textContent = 'Nová společnost';
    document.getElementById('company-dialog-description').textContent = 'Přidejte organizační jednotku do ERP.';
    document.getElementById('company-color').value = '#D9ED62';
    document.getElementById('save-company').textContent = 'Vytvořit společnost';
  }
  companyModal?.classList.add('open');
  companyModal?.setAttribute('aria-hidden', 'false');
  document.getElementById('company-name')?.focus();
};
const closeCompanyDialog = () => {
  companyModal?.classList.remove('open');
  companyModal?.setAttribute('aria-hidden', 'true');
  companyForm?.reset();
  document.getElementById('company-edit-field')?.remove();
};
document.getElementById('add-company')?.addEventListener('click', () => openCompanyDialog());
document.getElementById('edit-company')?.addEventListener('click', () => openCompanyDialog(true));
document.getElementById('close-company-dialog')?.addEventListener('click', closeCompanyDialog);
document.getElementById('cancel-company-dialog')?.addEventListener('click', closeCompanyDialog);
companyModal?.querySelector('.company-modal-backdrop')?.addEventListener('click', closeCompanyDialog);
const userModal = document.getElementById('user-modal');
const userForm = document.getElementById('user-form');
const companyNameField = document.getElementById('user-company-name');
const companyOptionsSource = document.getElementById('company-options-source');
if (companyNameField?.tagName === 'INPUT' && companyOptionsSource) {
  const companySelect = document.createElement('select');
  companySelect.id = companyNameField.id;
  companySelect.name = companyNameField.name;
  companySelect.required = companyNameField.required;
  companySelect.innerHTML = companyOptionsSource.innerHTML;
  companyNameField.replaceWith(companySelect);
}
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
  usernameInput.required = !user;
  const passwordInput = document.getElementById('user-password');
  passwordInput.value = '';
  passwordInput.required = !user;
  document.getElementById('user-role-name').value = user?.dataset.roleName || '';
  document.getElementById('user-company-name').value = user?.dataset.companyName || '';
  document.getElementById('user-color').value = user?.dataset.color || '#DCE9D7';
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
  document.getElementById('role-id').value = card?.dataset.roleId || '';
  document.getElementById('role-name').value = card?.querySelector('h3')?.textContent || '';
  document.getElementById('role-initial').value = card?.querySelector('.role-mark')?.textContent.trim() || '';
  document.getElementById('role-description').value = card?.querySelector('p')?.textContent || '';
  document.getElementById('role-color').value = card?.dataset.color || '#D9ED62';
  document.getElementById('permission-read').checked = card?.dataset.canRead === 'true';
  document.getElementById('permission-insert').checked = card?.dataset.canInsert === 'true';
  document.getElementById('permission-edit').checked = card?.dataset.canEdit === 'true';
  document.getElementById('permission-manage').checked = card?.dataset.canManage === 'true';
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
    document.getElementById('role-action').value = 'update';
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
    document.getElementById('role-action').value = 'create';
    document.getElementById('role-id').value = '';
    document.getElementById('role-color').value = '#D9ED62';
    document.getElementById('permission-read').checked = true;
    document.getElementById('permission-insert').checked = false;
    document.getElementById('permission-edit').checked = false;
    document.getElementById('permission-manage').checked = false;
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
  const submit = roleForm.querySelector('button[type="submit"]');
  submit.disabled = true;
  fetch('roles', { method: 'POST', body: new URLSearchParams(new FormData(roleForm)) })
    .then((response) => {
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      window.location.reload();
    })
    .catch(() => {
      submit.disabled = false;
      window.alert('Role se nepodařilo uložit. Zkontrolujte dostupnost backendu.');
    });
});

const modulePermissionsForm = document.getElementById('module-permissions-form');
modulePermissionsForm?.addEventListener('submit', (event) => {
  event.preventDefault();
  const submit = document.getElementById('save-module-permissions');
  submit.disabled = true;
  fetch('roles', { method: 'POST', body: new URLSearchParams(new FormData(modulePermissionsForm)) })
    .then((response) => { if (!response.ok) throw new Error(`HTTP ${response.status}`); window.location.reload(); })
    .catch(() => { submit.disabled = false; window.alert('Oprávnění se nepodařilo uložit.'); });
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
const browserBackend = `${window.location.protocol}//${window.location.hostname}:8080/api/v1`;
fetch(`${browserBackend}/companies`).then((response) => response.ok ? response.json() : []).then((companies) => {
  companies.forEach((company) => {
    document.querySelectorAll('.company-grid article').forEach((card) => {
      if (card.querySelector('h3')?.textContent.trim() === company.name) card.style.setProperty('--card-color', company.color);
    });
  });
}).catch(() => {});
fetch(`${browserBackend}/users`).then((response) => response.ok ? response.json() : []).then((users) => {
  users.forEach((user) => {
    const row = document.querySelector(`tr[data-user-id="${user.id}"]`);
    row?.style.setProperty('--card-color', user.color);
    if (row) row.dataset.color = user.color;
  });
}).catch(() => {});
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
