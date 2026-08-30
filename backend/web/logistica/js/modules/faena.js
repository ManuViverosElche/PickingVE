import { apiService } from '/logistica/js/services/apiService.js?k=logistica-2026';
import { renderOrderCard } from '/logistica/js/components/orderCard.js?k=logistica-2026';
import { openOrderDetail } from '/logistica/js/components/modalDetail.js?k=logistica-2026';
import { renderFaenaSubnav } from '/logistica/js/components/subnavFaena.js?k=logistica-2026';

let orders = [];
let filterState = 'todos';

export async function initFaena() {
    const root = document.getElementById('sec-faena');
    if (!root) return;
    root.innerHTML = `<div id="faena-subnav"></div><div class="filters"><input type="date" id="filterDate"><select id="filterFinca"><option value="">Todas las Fincas</option></select><input id="searchClient" placeholder="Buscar pedido, cliente, referencia..."></div><div class="chips" id="dateChips"></div><div class="chips" id="faena-state-filters"></div><div id="ordersContainer"><p class="text-muted">Cargando pedidos...</p></div><div id="faena-detail"></div>`;
    renderFaenaSubnav(document.getElementById('faena-subnav'), () => {});
    document.getElementById('filterDate').addEventListener('change', loadOrders);
    document.getElementById('filterFinca').addEventListener('change', renderOrders);
    document.getElementById('searchClient').addEventListener('input', renderOrders);
    const stateRoot = document.getElementById('faena-state-filters');
    ['todos', 'activos', 'pendientes', 'acopiados', 'cargados', 'enviados'].forEach(value => {
        const button = document.createElement('button');
        button.className = `chip${value === 'todos' ? ' chip-activo' : ''}`;
        button.textContent = value[0].toUpperCase() + value.slice(1);
        button.addEventListener('click', () => { filterState = value; stateRoot.querySelectorAll('button').forEach(item => item.classList.toggle('chip-activo', item === button)); loadOrders(); });
        stateRoot.appendChild(button);
    });
    await loadDates();
}

async function loadDates() {
    const data = await apiService.getOrderDates();
    const dates = data.fechas || [];
    const today = new Date().toISOString().slice(0, 10);
    const selected = dates.find(item => item.fecha >= today)?.fecha || dates.at(-1)?.fecha || today;
    document.getElementById('filterDate').value = selected;
    document.getElementById('dateChips').innerHTML = dates.slice(-10).map(item => `<button class="chip${item.fecha === selected ? ' chip-activo' : ''}" data-date="${item.fecha}">${item.fecha} · ${item.pedidos} ped</button>`).join('');
    document.querySelectorAll('#dateChips [data-date]').forEach(button => button.addEventListener('click', () => { document.getElementById('filterDate').value = button.dataset.date; loadOrders(); }));
    await loadOrders();
}

async function loadOrders() {
    const date = document.getElementById('filterDate').value;
    const data = await apiService.getOrders(date, filterState);
    orders = data.pedidos || [];
    const select = document.getElementById('filterFinca');
    const current = select.value;
    select.innerHTML = '<option value="">Todas las Fincas</option>' + [...new Set(orders.map(item => item.finca).filter(Boolean))].sort().map(item => `<option>${item}</option>`).join('');
    select.value = current;
    renderOrders();
}

function renderOrders() {
    const finca = document.getElementById('filterFinca').value.toLowerCase();
    const query = document.getElementById('searchClient').value.toLowerCase();
    const filtered = orders.filter(order => (!finca || (order.finca || '').toLowerCase() === finca) && (!query || JSON.stringify(order).toLowerCase().includes(query)));
    const root = document.getElementById('ordersContainer');
    root.innerHTML = filtered.length ? `<div class="grid-2">${filtered.map(order => renderOrderCard(order)).join('')}</div>` : '<p class="text-muted">No hay pedidos para los filtros seleccionados.</p>';
    root.querySelectorAll('[data-order-detail]').forEach(button => button.addEventListener('click', () => openOrderDetail(button.dataset.orderDetail, { apiService, onPrint: window.prepareOrderLabels })));
}
