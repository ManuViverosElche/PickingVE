export function renderFaenaSubnav(root, onChange) {
    const tabs = ['Pedidos del Día', 'Etiquetas a Sacar', 'Histórico', 'Reparto de Faena', 'Carga por Operario'];
    root.innerHTML = `<nav class="chips" aria-label="Subpestañas de Faena">${tabs.map((tab, index) => `<button class="chip${index === 0 ? ' chip-activo' : ''}" data-faena-subtab="${tab}">${tab}</button>`).join('')}</nav>`;
    root.querySelectorAll('[data-faena-subtab]').forEach(button => button.addEventListener('click', () => { root.querySelectorAll('button').forEach(item => item.classList.toggle('chip-activo', item === button)); onChange(button.dataset.faenaSubtab); }));
}

/**
 * initSubnav — navegación lateral del shell SPA (/logistica).
 * Cambia entre los paneles #panel-{tab} y actualiza el título y el estado activo.
 * Sin esto las pestañas Stock/Etiquetas/Configuración no responden.
 */
export function initSubnav() {
    document.querySelectorAll('.sidebar-nav .nav-item[data-tab]').forEach(item => {
        item.addEventListener('click', () => {
            const tab = item.dataset.tab;
            document.querySelectorAll('.sidebar-nav .nav-item').forEach(n => n.classList.remove('active'));
            item.classList.add('active');
            document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('active'));
            const panel = document.getElementById(`panel-${tab}`);
            if (panel) panel.classList.add('active');
            const title = document.getElementById('current-page-title');
            if (title) title.textContent = item.textContent.trim();
        });
    });
}
