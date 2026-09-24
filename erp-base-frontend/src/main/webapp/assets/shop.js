(() => {
  const filters = document.querySelectorAll('[data-category-filter]');
  const cards = document.querySelectorAll('[data-product-card]');
  const count = document.getElementById('result-count');
  const empty = document.getElementById('empty-products');

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
})();
