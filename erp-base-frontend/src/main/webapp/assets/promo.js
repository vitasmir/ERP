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
  const setupImagePopup = (thumbnail) => {
    let popup;
    const removePopup = () => {
      popup?.remove();
      popup = undefined;
    };
    thumbnail.addEventListener('mouseenter', () => {
      if (!thumbnail.dataset.image) return;
      removePopup();
      popup = document.createElement('div');
      popup.className = 'product-image-popup';
      popup.setAttribute('role', 'img');
      popup.setAttribute('aria-label', thumbnail.alt);
      const fullImage = document.createElement('img');
      fullImage.src = thumbnail.dataset.image;
      fullImage.alt = thumbnail.alt;
      popup.appendChild(fullImage);
      document.body.appendChild(popup);
      const bounds = thumbnail.getBoundingClientRect();
      const popupBounds = popup.getBoundingClientRect();
      const left = Math.max(16, Math.min(
        bounds.left + (bounds.width - popupBounds.width) / 2,
        window.innerWidth - popupBounds.width - 16));
      const aboveTop = bounds.top - popupBounds.height - 14;
      popup.style.left = `${left}px`;
      popup.style.top = `${Math.max(16, aboveTop)}px`;
    });
    thumbnail.addEventListener('mouseleave', removePopup);
  };
  productSelect?.addEventListener('change', () => {
    const imageUrl = productSelect.selectedOptions[0]?.dataset.image;
    if (imageUrl) {
      previewImage.src = imageUrl;
      previewImage.dataset.image = imageUrl;
      previewImage.alt = productSelect.selectedOptions[0].textContent.trim();
      previewImage.hidden = false;
      previewText.hidden = true;
    } else {
      previewImage.removeAttribute('src');
      delete previewImage.dataset.image;
      previewImage.hidden = true;
      previewText.hidden = false;
    }
  });
  document.querySelectorAll('.campaign-product-photo img[data-image]').forEach(setupImagePopup);
  if (previewImage) setupImagePopup(previewImage);
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') close();
  });
})();
