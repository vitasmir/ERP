(() => {
  const stage = document.getElementById('design-stage');
  const copy = document.getElementById('stage-copy');
  const xField = document.getElementById('text-x');
  const yField = document.getElementById('text-y');
  const design = document.getElementById('design-select');
  document.querySelectorAll('[data-image-input]').forEach((input) => {
    const preview = input.closest('form').querySelector('[data-image-preview]');
    input.addEventListener('input', () => {
      preview.textContent = '';
      if (!input.value) {
        preview.textContent = 'Sem zadejte URL obrázku produktu';
        return;
      }
      const image = document.createElement('img');
      image.src = input.value;
      image.alt = 'Náhled produktu';
      image.onerror = () => { preview.textContent = 'Obrázek se nepodařilo načíst'; };
      preview.appendChild(image);
    });
  });
  document.querySelectorAll('.danger-button').forEach((button) => {
    button.style.backgroundColor = '#a8463d';
    button.style.borderColor = '#a8463d';
    button.style.color = '#fff';
  });
  const deleteRows = [...document.querySelectorAll('.catalog-product-actions > div')];
  const productCards = [...document.querySelectorAll('.product-card')];
  deleteRows.forEach((row, index) => {
    const card = productCards[index];
    const form = row.querySelector('form');
    const priceColumn = card?.querySelector('.product-top > div:last-child');
    if (!card || !form || !priceColumn) return;
    priceColumn.classList.add('product-card-actions');
    priceColumn.style.display = 'flex';
    priceColumn.style.flexDirection = 'column';
    priceColumn.style.alignItems = 'flex-end';
    priceColumn.style.gap = '7px';
    form.classList.add('product-delete-form');
    const productId = form.querySelector('input[name="productId"]')?.value;
    if (productId) {
      const removeForm = document.createElement('form');
      removeForm.method = 'post';
      removeForm.className = 'product-remove-category-form';
      const action = document.createElement('input');
      action.type = 'hidden';
      action.name = 'action';
      action.value = 'removeFromCategory';
      const id = document.createElement('input');
      id.type = 'hidden';
      id.name = 'productId';
      id.value = productId;
      const removeButton = document.createElement('button');
      removeButton.type = 'submit';
      removeButton.className = 'secondary';
      removeButton.textContent = 'Odebrat z kategorie';
      removeForm.append(action, id, removeButton);
      priceColumn.appendChild(removeForm);
    }
    priceColumn.appendChild(form);
  });
  document.querySelector('.catalog-actions-panel')?.remove();
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
  stage.addEventListener('click', (event) => {
    const box = stage.getBoundingClientRect();
    setPosition(((event.clientX - box.left) / box.width) * 100, ((event.clientY - box.top) / box.height) * 100);
    stage.focus();
  });
  stage.addEventListener('keydown', (event) => {
    const step = event.shiftKey ? 5 : 1;
    let x = Number(xField.value);
    let y = Number(yField.value);
    if (event.key === 'ArrowLeft') x -= step;
    else if (event.key === 'ArrowRight') x += step;
    else if (event.key === 'ArrowUp') y -= step;
    else if (event.key === 'ArrowDown') y += step;
    else return;
    event.preventDefault();
    setPosition(x, y);
  });
  design?.addEventListener('change', () => { stage.className = `design-stage design-${design.value.toLowerCase()}`; });
})();
