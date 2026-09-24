(() => {
  const filters = document.querySelectorAll('[data-category-filter]');
  const cards = document.querySelectorAll('[data-product-card]');
  const count = document.getElementById('result-count');
  const empty = document.getElementById('empty-products');
  const checkoutButton = document.querySelector('.checkout-button');

  filters.forEach((filter) => filter.addEventListener('click', () => {
    filters.forEach((item) => item.classList.toggle('active', item === filter));
    const selected = filter.dataset.categoryFilter;
    let visible = 0;
    cards.forEach((card) => {
      const matches = selected === 'all' || card.dataset.categoryId === selected;
      card.hidden = !matches;
      if (matches) visible += 1;
    });
    if (count) count.textContent = `${visible} produktů`;
    if (empty) empty.style.display = visible ? 'none' : 'block';
  }));

  document.querySelectorAll('.quantity-control input').forEach((input) => {
    input.addEventListener('change', () => input.form.submit());
    input.addEventListener('keydown', (event) => {
      if (event.key === 'Enter') input.form.submit();
    });
  });

  if (checkoutButton) {
    checkoutButton.addEventListener('click', () => {
      window.location.href = 'eshop?checkout=delivery#delivery-step';
    });
  }

  const paymentForm = document.querySelector('.payment-options');
  const cardFields = document.querySelector('[data-card-details]');
  const cardInputs = cardFields ? cardFields.querySelectorAll('input') : [];
  const paymentTotal = document.querySelector('[data-payment-total]');
  const deliveryFeeLabels = document.querySelectorAll('[data-delivery-fee-label]');
  const paymentFee = Number(paymentForm?.dataset.deliveryFee || 0);
  const paymentSubtotal = Number(paymentForm?.dataset.subtotal || 0);
  const updateCardFields = () => {
    const cardSelected = paymentForm?.querySelector('input[name="paymentMethod"]:checked')?.value === 'card';
    if (cardFields) cardFields.style.display = cardSelected ? 'grid' : 'none';
    cardInputs.forEach((input) => { input.required = cardSelected; });
    deliveryFeeLabels.forEach((label) => { label.hidden = cardSelected; });
    if (paymentTotal) paymentTotal.textContent = `${(paymentSubtotal + (cardSelected ? 0 : paymentFee)).toFixed(2)} Kč`;
  };
  paymentForm?.querySelectorAll('input[name="paymentMethod"]').forEach((input) => {
    input.addEventListener('change', updateCardFields);
  });
  paymentForm?.querySelectorAll('[name^="cardNumber"]').forEach((input, index, inputs) => {
    input.addEventListener('input', () => {
      input.value = input.value.replace(/\D/g, '').slice(0, 4);
      if (input.value.length === 4 && inputs[index + 1]) inputs[index + 1].focus();
    });
  });
  updateCardFields();
})();
