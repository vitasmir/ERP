(() => {
  const modal = document.getElementById('campaign-modal');
  const open = document.getElementById('add-campaign');
  const close = () => {
    modal.classList.remove('is-open');
    modal.setAttribute('aria-hidden', 'true');
  };
  open?.addEventListener('click', () => {
    modal.classList.add('is-open');
    modal.setAttribute('aria-hidden', 'false');
    modal.querySelector('input[name="name"]')?.focus();
  });
  document.getElementById('close-campaign')?.addEventListener('click', close);
  document.getElementById('cancel-campaign')?.addEventListener('click', close);
  modal?.querySelector('.campaign-modal-backdrop')?.addEventListener('click', close);
  const productSelect = modal?.querySelector('select[name="productId"]');
  const previewText = document.getElementById('product-preview')?.querySelector('span');
  const previewImage = document.getElementById('product-preview-image');
  productSelect?.addEventListener('change', () => {
    const imageUrl = productSelect.selectedOptions[0]?.dataset.image;
    if (imageUrl) {
      previewImage.src = imageUrl;
      previewImage.hidden = false;
      previewText.hidden = true;
    } else {
      previewImage.removeAttribute('src');
      previewImage.hidden = true;
      previewText.hidden = false;
    }
  });
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') close();
  });
})();
