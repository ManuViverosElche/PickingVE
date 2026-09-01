/**
 * repartoFaena.js — Componente "Reparto de Faena" (subpestaña de Faena).
 *
 * Muestra la faena del día por pedido/línea (operarios asignados) y permite
 * reasignar operarios y guardar (usa /api/manager/reparto vía dataConnector).
 * Replica la funcionalidad esencial de /manager sin depender de su DOM.
 */
let dc = null;
let asignaciones = [];
let selectedDate = '';
let operariosCache = [];

export async function renderRepartoFaena(root, dataConnector) {
    if (!root || !dataConnector) return;
    dc = dataConnector;
    root.innerHTML = `
        <div class="filters">
            <input type="date" id="filterDateReparto">
            <button id="reparto-load">Cargar reparto</button>
            <button class="btn-sec" id="reparto-save" style="display:none;">💾 Guardar reparto</button>
            <span id="reparto-pendientes" style="display:none; color:var(--warn); font-weight:700;"></span>
        </div>
        <div id="repartoContainer"><p class="text-muted">Selecciona fecha y pulsa "Cargar reparto".</p></div>
    `;
    const dateInput = root.querySelector('#filterDateReparto');
    dateInput.value = dataConnector.hoyStr();
    root.querySelector('#reparto-load').addEventListener('click', () => loadReparto(dateInput.value));
    root.querySelector('#reparto-save').addEventListener('click', guardarReparto);
    await loadOperarios();
    await loadReparto(dateInput.value);
}

async function loadOperarios() {
    try {
        const data = await dc.fetchCarga();
        operariosCache = data.map(op => ({ nombre: op.nombre, email: op.email }));
    } catch (e) {
        console.error(e);
        operariosCache = [];
    }
}

async function loadReparto(fecha) {
    selectedDate = fecha;
    const container = document.getElementById('repartoContainer');
    try {
        asignaciones = await dc.fetchReparto(fecha);
        const data = await dc.fetchPedidosDia(fecha, 'todos');
        const pedidos = data;

        if (!pedidos.length) {
            container.innerHTML = '<div style="text-align:center; color:var(--mut); padding:30px;">No hay pedidos para esta fecha.</div>';
            return;
        }

        let html = '';
        pedidos.forEach(o => {
            const key = `${o.serie}_${o.numero}`;
            const lineas = (o.lineas || []).map((l, idx) => {
                const lineNum = l.posicion || (idx + 1);
                const asig = asignaciones.find(a => a.pedido_id === o.numero && a.linea_huella === l.huellaDigital);
                const opActual = asig ? asig.operario_nombre : (l.operarioAsignado || '');
                const emailActual = asig ? asig.operario_email : (l.operarioAsignadoEmail || '');
                const options = `<option value="">— Sin asignar —</option>` +
                    operariosCache.map(op => `<option value="${dc.escHtml(op.email)}" ${op.email === emailActual ? 'selected' : ''}>${dc.escHtml(op.nombre)}</option>`).join('');
                return `<tr>
                    <td>${lineNum}</td>
                    <td style="color:var(--blue); font-weight:700;">${dc.escHtml(l.referencia)}</td>
                    <td>${dc.escHtml(l.descripcion)}</td>
                    <td style="font-weight:700;">${l.pendientes || 0}</td>
                    <td style="color:var(--warn); font-weight:700;" title="D-274: Acopio físico del operario de campo">${l.acopiadoOperario || 0}</td>
                    <td style="color:var(--lime); font-weight:700;" title="D-274: Verificación del encargado de picking">${l.acopiado || 0}</td>
                    <td>${opActual ? dc.escHtml(opActual) : '—'}</td>
                    <td><select data-pedido="${o.numero}" data-linea="${l.huellaDigital}" data-linea-num="${lineNum}" data-pendientes="${l.pendientes || 0}" data-acopiado="${l.acopiado || 0}" data-origen="${dc.escHtml(emailActual)}">${options}</select></td>
                </tr>`;
            }).join('');

            html += `<div class="card" style="margin-bottom:14px;">
                <div class="card-header">
                    <div>
                        <div class="order-id">Pedido #${o.numero} ${o.marcaPedido ? `<span class="badge b-yellow">🏷️ ${dc.escHtml(o.marcaPedido)}</span>` : ''}</div>
                        <div class="client-name">${dc.escHtml(o.cliente)} · 📍 ${dc.escHtml(o.finca || 'SIN FINCA')} · Zona: ${dc.escHtml(o.sector || 'N/D')}</div>
                    </div>
                    <span class="badge b-blue">${dc.escHtml(o.estado || 'activo')}</span>
                </div>
                <table>
                    <thead><tr><th>#</th><th>Referencia</th><th>Descripción</th><th>Pendientes</th><th style="color:var(--warn);">Acop. Op.</th><th style="color:var(--lime);">Verificado</th><th>Asignado</th><th>Operario</th></tr></thead>
                    <tbody>${lineas}</tbody>
                </table>
            </div>`;
        });
        container.innerHTML = html || '<div style="text-align:center; color:var(--mut); padding:30px;">No hay líneas para esta fecha.</div>';
        container.querySelectorAll('select[data-pedido]').forEach(sel => sel.addEventListener('change', actualizarPendientes));
        document.getElementById('reparto-pendientes').style.display = 'none';
        document.getElementById('reparto-save').style.display = 'none';
    } catch (e) {
        console.error(e);
        container.innerHTML = '<div style="color:var(--bad); text-align:center; padding:20px;">Error al cargar el reparto de faena.</div>';
    }
}

let cambios = {};
function actualizarPendientes() {
    const sel = this;
    const key = `${sel.dataset.pedido}|${sel.dataset.linea}`;
    if (sel.value) cambios[key] = { operario_email: sel.value, pedido_id: sel.dataset.pedido, linea_huella: sel.dataset.linea };
    else delete cambios[key];
    const n = Object.keys(cambios).length;
    const pendEl = document.getElementById('reparto-pendientes');
    const saveEl = document.getElementById('reparto-save');
    if (n > 0) {
        pendEl.textContent = `⚠ ${n} cambio${n > 1 ? 's' : ''} sin guardar`;
        pendEl.style.display = '';
        saveEl.style.display = '';
    } else {
        pendEl.style.display = 'none';
        saveEl.style.display = 'none';
    }
}

async function guardarReparto() {
    try {
        const pendientes = Object.values(cambios);
        if (!pendientes.length) return;
        await dc.guardarReparto({ fecha: selectedDate, asignaciones: pendientes });
        cambios = {};
        alert('Reparto guardado correctamente.');
        await loadReparto(selectedDate);
    } catch (e) {
        console.error(e);
        alert('Error al guardar el reparto.');
    }
}