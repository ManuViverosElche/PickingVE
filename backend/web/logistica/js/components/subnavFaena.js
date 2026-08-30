export function renderFaenaSubnav(root, onChange) {
    const tabs = ['Pedidos del Día', 'Etiquetas a Sacar', 'Histórico', 'Reparto de Faena', 'Carga por Operario'];
    root.innerHTML = `<nav class="chips" aria-label="Subpestañas de Faena">${tabs.map((tab, index) => `<button class="chip${index === 0 ? ' chip-activo' : ''}" data-faena-subtab="${tab}">${tab}</button>`).join('')}</nav>`;
    root.querySelectorAll('[data-faena-subtab]').forEach(button => button.addEventListener('click', () => { root.querySelectorAll('button').forEach(item => item.classList.toggle('chip-activo', item === button)); onChange(button.dataset.faenaSubtab); }));
}

export function initSubnav() {}
