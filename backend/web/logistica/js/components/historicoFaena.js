/**
 * historicoFaena.js — Componente "Histórico de Cargas" (subpestaña de Faena).
 *
 * Replica la funcionalidad del submenú /logistica historicoCargas.js usando
 * dataConnector, con el mismo lenguaje visual de "Pedidos del día": botones de
 * finca, chips de estado, agrupación por finca y modal de desglose tipo
 * "Detalle". Mantiene compatibilidad con el dashboard legacy.
 */
let dc = null;
let fechas = [];
let selectedDate = '';
let pedidos = [];
let filterFinca = '';
let filterEstado = 'todos';

function el(id) { return document.getElementById(id); }

export async function renderHistoricoFaena(root, dataConnector) {
    if (!root || !dataConnector) return;
    dc = dataConnector;
    selectedDate = '';
    fechas = [];
    pedidos = [];
    filterFinca = '';
    filterEstado = 'todos';

    root.innerHTML = `
        <div class="filters">
            <input id="searchHistorico" placeholder="Buscar pedido o cliente..." style="flex:1; min-width:200px;">
        </div>
        <div class="chips" id="fincaButtonsHistorico"></div>
        <div class="chips" id="dateChipsHistorico"></div>
        <div class="chips" id="historico-state-filters"></div>
        <div id="historicoContainer"><p class="text-muted">Cargando histórico...</p></div>
    `;
    root.querySelector('#searchHistorico').addEventListener('input', renderHistorico);
    renderStateChips();
    await loadFechas();
}

function renderStateChips() {
    const cont = el('historico-state-filters');
    if (!cont) return;
    const estados = ['todos', 'cargados', 'enviados'];
    cont.innerHTML = estados.map(st => {
        const cls = st === filterEstado ? 'chip chip-activo' : 'chip';
        return `<button class="${cls}" data-estado="${st}">${st[0].toUpperCase() + st.slice(1)}</button>`;
    }).join('');
    cont.querySelectorAll('[data-estado]').forEach(btn => btn.addEventListener('click', () => {
        filterEstado = btn.dataset.estado;
        renderStateChips();
        renderHistorico();
    }));
}

async function loadFechas() {
    try {
        fechas = await dc.fetchHistoricoFechas();
        if (fechas.length) selectedDate = fechas[0].fecha;
        renderChips();
        if (selectedDate) await loadPedidos(selectedDate);
        else document.getElementById('historicoContainer').innerHTML = '<div style="color:var(--mut); text-align:center; padding:30px;">No hay registros cargados o enviados en el histórico.</div>';
    } catch (e) {
        console.error(e);
        document.getElementById('historicoContainer').innerHTML = '<div style="color:var(--bad); text-align:center; padding:20px;">Error al cargar histórico.</div>';
    }
}

function renderChips() {
    const cont = document.getElementById('dateChipsHistorico');
    if (!fechas.length) {
        cont.innerHTML = '<span style="color:var(--mut); font-size:0.85rem;">Sin fechas en histórico.</span>';
        return;
    }
    cont.innerHTML = fechas.map(f => {
        const activo = f.fecha === selectedDate ? ' chip-activo' : '';
        const cls = f.total > 0 ? (f.enviados > 0 ? 'chip-enviado' : 'chip-cargado') : 'chip';
        return `<button class="chip ${cls}${activo}" data-fecha="${f.fecha}">📜 ${dc.formatearFechaDDMMYYYY(f.fecha)} (${f.total} ped)</button>`;
    }).join('');
    cont.querySelectorAll('[data-fecha]').forEach(btn => btn.addEventListener('click', () => {
        selectedDate = btn.dataset.fecha;
        renderChips();
        loadPedidos(selectedDate);
    }));
}

async function loadPedidos(fecha) {
    try {
        pedidos = await dc.fetchHistoricoPedidos(fecha);
        renderFincaButtons();
        renderHistorico();
    } catch (e) {
        console.error(e);
        document.getElementById('historicoContainer').innerHTML = '<div style="color:var(--bad); text-align:center; padding:20px;">Error al cargar pedidos del histórico.</div>';
    }
}

function renderFincaButtons() {
    const cont = document.getElementById('fincaButtonsHistorico');
    if (!cont) return;
    const fincas = [...new Set(pedidos.map(o => o.finca).filter(Boolean))].sort();
    if (!fincas.length) { cont.innerHTML = ''; return; }
    let html = `<button class="finca-btn${filterFinca === '' ? ' active' : ''}" data-finca="">Todas</button>`;
    fincas.forEach(f => {
        html += `<button class="finca-btn${filterFinca === f ? ' active' : ''}" data-finca="${dc.escHtml(f)}">${dc.escHtml(f)}</button>`;
    });
    cont.innerHTML = html;
    cont.querySelectorAll('.finca-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            filterFinca = btn.dataset.finca;
            renderFincaButtons();
            renderHistorico();
        });
    });
}

function renderHistorico() {
    const searchVal = (document.getElementById('searchHistorico').value || '').toLowerCase();
    const container = document.getElementById('historicoContainer');
    const filtered = pedidos.filter(o => {
        if (filterFinca && o.finca !== filterFinca) return false;
        if (filterEstado === 'cargados' && o.estado !== 'cargado') return false;
        if (filterEstado === 'enviados' && o.estado !== 'enviado') return false;
        return !searchVal || (o.cliente || '').toLowerCase().includes(searchVal) || (o.numero || '').includes(searchVal);
    });
    if (!filtered.length) {
        container.innerHTML = '<div style="text-align:center; color:var(--mut); padding:30px;">No hay pedidos en esta fecha.</div>';
        return;
    }
    const byFinca = {};
    filtered.forEach(o => { (byFinca[o.finca || 'Sin finca'] = byFinca[o.finca || 'Sin finca'] || []).push(o); });
    let html = '';
    for (const [fincaName, orders] of Object.entries(byFinca)) {
        html += `<div class="finca-group"><div class="finca-title">📍 ${dc.escHtml(fincaName)} (${orders.length} pedidos)</div><div class="grid">`;
        orders.forEach(o => { html += renderHistoricoCard(o); });
        html += `</div></div>`;
    }
    container.innerHTML = html;
    container.querySelectorAll('[data-hist-punteo]').forEach(btn => btn.addEventListener('click', () => verInformePunteoHist(btn.dataset.histPunteo)));
    container.querySelectorAll('[data-hist-desglose]').forEach(btn => btn.addEventListener('click', () => verInformeDesgloseHist(btn.dataset.histDesglose, btn.dataset.histFinca, btn.dataset.histZona)));
}

function renderHistoricoCard(o) {
    const badge = o.estado === 'enviado'
        ? '<span class="badge b-green">✅ Enviado</span>'
        : '<span class="badge b-yellow">🚛 Cargado</span>';
    const cClass = o.estado === 'enviado' ? 'card-enviado' : 'card-camion_asignado';
    const tot = o.totalPistoleado || 0;
    const qtyBox = `<span class="pill pill-ok" title="Total pistoleado · Escaneos">⚡ ${tot} uds · ${o.totalEventos || 0} escaneos</span>`;
    return `
    <div class="card ${cClass}">
        <div class="card-header">
            <div>
                <div class="order-id">Pedido #${o.numero} ${qtyBox}</div>
                <div class="client-name">${dc.escHtml(o.cliente)}</div>
            </div>
            <div>${badge}</div>
        </div>
        <div style="font-size:0.8rem; color:var(--mut); margin-bottom:6px;">
            📍 Finca: <b>${dc.escHtml(o.finca || 'N/D')}</b> | Zona: <b>${dc.escHtml(o.sector || 'N/D')}</b>
        </div>
        <div class="card-actions">
            <button data-hist-punteo="${o.numero}" style="background:var(--primary); color:var(--on-primary);">📄 Punteo</button>
            <button class="btn-sec" data-hist-desglose="${o.numero}" data-hist-finca="${dc.jsAttr(o.finca || '')}" data-hist-zona="${dc.jsAttr(o.sector || '')}">🔍 Desglose</button>
        </div>
    </div>`;
}

async function verInformeDesgloseHist(numero, finca, zona) {
    try {
        const data = await dc.fetchHistoricoDetalle(numero);
        let modal = document.getElementById('faena-hist-modal');
        if (!modal) {
            modal = document.createElement('div');
            modal.id = 'faena-hist-modal';
            modal.className = 'modal';
            modal.innerHTML = `<div class="modal-content">
                <div class="modal-header"><h2 id="faena-hist-title"></h2><button class="close-btn" data-close>✕</button></div>
                <div id="faena-hist-body"></div>
                <div style="display:flex; gap:8px; justify-content:flex-end; margin-top:12px;">
                    <button class="btn-sec" data-close>Cerrar</button>
                    <button id="faena-hist-punteo">📄 Punteo</button>
                </div>
            </div>`;
            modal.addEventListener('click', e => { if (e.target.closest('[data-close]')) modal.classList.remove('open'); });
            document.body.appendChild(modal);
        }
        modal.querySelector('#faena-hist-title').innerText = `Desglose Pedido #${numero}`;

        const formatearFechaHora = (iso) => {
            if (!iso) return '';
            const d = new Date(iso);
            if (isNaN(d.getTime())) return String(iso);
            const dd = String(d.getDate()).padStart(2, '0');
            const mm = String(d.getMonth() + 1).padStart(2, '0');
            const yyyy = d.getFullYear();
            const hh = String(d.getHours()).padStart(2, '0');
            const min = String(d.getMinutes()).padStart(2, '0');
            return `${dd}/${mm}/${yyyy} ${hh}:${min}`;
        };
        const nombreMatricula = (tipo) => {
            const t = String(tipo || '').toUpperCase();
            if (t.startsWith('CAMION')) return 'Camión';
            if (t.startsWith('REMOLQUE')) return 'Remolque';
            return tipo || 'Matrícula';
        };
        let matHtml = (data.matriculas || []).map(m => {
            const icono = String(m.tipo || '').toUpperCase().startsWith('CAMION') ? '🚛' : '🛻';
            const muelle = m.muelle ? ` · Muelle: <b>${dc.escHtml(m.muelle)}</b>` : '';
            return `<div>${icono} <b>${dc.escHtml(nombreMatricula(m.tipo))}:</b> ${dc.escHtml(m.matricula || 'N/D')}${muelle} <small style="color:var(--mut);">${dc.escHtml(formatearFechaHora(m.creadoEn))}</small></div>`;
        }).join('') || '<div style="color:var(--mut)">Sin matrículas registradas</div>';

        let regHtml = '<table><thead><tr><th>Parte</th><th>Hora</th><th>Operario</th><th>Ref. Servida</th><th>Cant</th><th>Sust</th><th>OCR / EAN</th></tr></thead><tbody>';
        (data.registros || []).forEach(r => {
            regHtml += `<tr>
                <td>${dc.escHtml(r.parte)}</td>
                <td style="font-size:0.7rem;">${dc.escHtml(r.fechaHora)}</td>
                <td>${dc.escHtml(r.empleado)}</td>
                <td style="font-weight:700; color:var(--blue);">${dc.escHtml(r.refServida)}</td>
                <td>${r.cantidad}</td>
                <td>${r.sustituido ? '🔄' : ''}</td>
                <td style="font-size:0.7rem;">${dc.escHtml(r.ocr || r.ean)}</td>
            </tr>`;
        });
        regHtml += '</tbody></table>';

        let etHtml = '<table><thead><tr><th>Referencia</th><th>Litraje</th><th>Sector</th><th>Cant</th><th>Estado</th></tr></thead><tbody>';
        (data.etiquetas || []).forEach(e => {
            etHtml += `<tr>
                <td style="color:var(--blue); font-weight:700;">${dc.escHtml(e.referencia)}</td>
                <td>${dc.escHtml(e.litraje || '—')}</td>
                <td>${dc.escHtml(e.sector || '—')}</td>
                <td>${e.cantidad}</td>
                <td><b>${dc.escHtml(e.estado)}</b></td>
            </tr>`;
        });
        etHtml += '</tbody></table>';

        const muelleDb = (data.matriculas || []).find(m => m.muelle)?.muelle || '';
        modal.querySelector('#faena-hist-body').innerHTML = `
            <div style="display:grid; grid-template-columns: repeat(2, 1fr); gap: 10px; margin-bottom: 14px; font-size: 0.85rem; background:var(--card2); padding:10px; border-radius:8px;">
                <div>📍 <b>Finca:</b> ${dc.escHtml(finca || 'N/D')}${muelleDb ? ` · Zona: <b>${dc.escHtml(muelleDb)}</b>` : ''}</div>
                <div>🚛 <b>Matrículas:</b> ${data.matriculas ? data.matriculas.length : 0}</div>
                <div>⚡ <b>Escaneos:</b> ${(data.registros || []).length}</div>
                <div>🏷️ <b>Etiquetas:</b> ${(data.etiquetas || []).length}</div>
            </div>
            <h3 style="font-size:0.95rem; margin-bottom:6px; color:var(--blue);">🚛 Carga y Matrículas</h3>
            <div style="background:var(--card2); border:1px solid var(--line); border-radius:8px; padding:10px; font-size:0.85rem; margin-bottom:14px;">${matHtml}</div>
            <h3 style="font-size:0.95rem; margin-bottom:6px; color:var(--blue);">📱 Histórico de Pistoleo (${(data.registros || []).length} eventos)</h3>
            ${regHtml}
            <h3 style="font-size:0.95rem; margin:14px 0 6px; color:var(--blue);">🏷️ Etiquetas del Pedido (${(data.etiquetas || []).length})</h3>
            ${etHtml}`;
        modal.querySelector('#faena-hist-punteo').onclick = () => { modal.classList.remove('open'); verInformePunteoHist(numero); };
        modal.classList.add('open');
    } catch (e) {
        console.error(e);
        alert('Error al cargar el desglose.');
    }
}

function verInformePunteoHist(numero) {
    const urls = dc.buildInformePunteoUrl(numero);
    window.open(urls.html, '_blank');
}
