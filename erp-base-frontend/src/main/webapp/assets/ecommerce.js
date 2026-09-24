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
