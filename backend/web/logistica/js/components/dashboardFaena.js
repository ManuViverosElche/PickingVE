/**
 * dashboardFaena.js — Componente reutilizable "Dashboard & Faena".
 *
 * Replica el diseño y funcionalidad de la vista /manager (tarjetas de pedido,
 * badges de estado, pills de cantidades, barra de progreso, filtros y botones
 * "Informe Punteo / Detalle / Mensajes / Observaciones") SIN copiar lógica a lo
 * bruto: TODA la obtención de datos pasa por dataConnector.js.
 *
 * Orquesta las subpestañas de Faena:
 *   - Pedidos del Día  -> este fichero (tarjetas + filtros + modales)
 *   - Etiquetas a Sacar -> components/etiquetasDia.js
 *   - Histórico         -> components/historicoFaena.js
 *   - Reparto de Faena  -> components/repartoFaena.js
 *   - Carga por Operario-> components/cargaOperarios.js
 *
 * Uso:
 *   import { renderDashboardFaena } from '/logistica/js/components/dashboardFaena.js?k=logistica-2026';
 *   const dataConnector = await import('/logistica/js/services/dataConnector.js?k=logistica-2026');
 *   renderDashboardFaena(document.getElementById('sec-faena'), dataConnector);
 */
import { renderFaenaSubnav } from '/logistica/js/components/subnavFaena.js?k=logistica-2026';
import { renderEtiquetasDia } from '/logistica/js/components/etiquetasDia.js?k=logistica-2026';
import { renderHistoricoFaena } from '/logistica/js/components/historicoFaena.js?k=logistica-2026';
import { renderRepartoFaena } from '/logistica/js/components/repartoFaena.js?k=logistica-2026';
import { renderCargaOperarios } from '/logistica/js/components/cargaOperarios.js?k=logistica-2026';

const ESTADOS = ['todos', 'activos', 'pendientes', 'acopiados', 'cargados', 'enviados'];
const SUBTABS = {
    'Pedidos del Día': 'pedidos',
    'Etiquetas a Sacar': 'etiquetas',
    'Histórico': 'historico',
    'Reparto de Faena': 'reparto',
    'Carga por Operario': 'carga',
};

let dc = null;          // dataConnector (inyectado)
let fechasData = [];    // lista de fechas del conector
let selectedDate = '';  // fecha activa (dinámica: hoy+)
let filterEstado = 'todos';
let allOrdersData = [];
let rootEl = null;

function el(id) { return document.getElementById(id); }

async function loadUnread() {
    await dc.fetchRecientes();
}

function renderDateChips() {
    const cont = el('dateChips');
    if (!cont) return;
    const previstas = fechasData.filter(f => f.fecha >= dc.hoyStr());
    const list = previstas.length ? previstas : fechasData.slice(-5);
    if (!list.length) {
        cont.innerHTML = '<span style="color:var(--mut); font-size:0.85rem;">No hay fechas previstas.</span>';
        return;
    }
    cont.innerHTML = list.map(f => {
        const activo = f.fecha === selectedDate ? ' chip-activo' : '';
        const pends = f.pendientes ?? Math.max(0, f.pedidos - f.cargados);
        const cls = pends === 0 ? 'chip-cargado' : 'chip-pendiente';
        const txt = pends === 0 ? `✓ ${dc.formatearFechaDDMMYYYY(f.fecha)}` : `${dc.formatearFechaDDMMYYYY(f.fecha)} · ${pends} pend`;
        return `<button class="chip ${cls}${activo}" data-fecha="${f.fecha}">${txt}</button>`;
    }).join('');
    cont.querySelectorAll('[data-fecha]').forEach(btn => btn.addEventListener('click', () => {
        selectedDate = btn.dataset.fecha;
        el('filterDate').value = selectedDate;
        loadOrders();
    }));
}

function renderStateChips() {
    const cont = el('faena-state-filters');
    if (!cont) return;
    cont.innerHTML = ESTADOS.map(st => {
        const cls = st === filterEstado ? 'chip chip-activo' : 'chip';
        return `<button class="${cls}" data-estado="${st}">${st[0].toUpperCase() + st.slice(1)}</button>`;
    }).join('');
    cont.querySelectorAll('[data-estado]').forEach(btn => btn.addEventListener('click', () => {
        filterEstado = btn.dataset.estado;
        renderStateChips();
        loadOrders();
    }));
}

function populateFincas(orders) {
    const select = el('filterFinca');
    if (!select) return;
    const current = select.value;
    const fincas = [...new Set(orders.map(o => o.finca).filter(Boolean))].sort();
    select.innerHTML = '<option value="">Todas las Fincas</option>' + fincas.map(f => `<option value="${f}">${f}</option>`).join('');
    select.value = current;
}

async function loadOrders() {
    try {
        await loadUnread();
        const dateVal = el('filterDate').value;
        allOrdersData = await dc.fetchPedidosDia(dateVal, filterEstado);
        populateFincas(allOrdersData);
        renderOrders();
    } catch (e) {
        console.error(e);
        el('ordersContainer').innerHTML = '<div style="color:var(--bad); text-align:center; padding:20px;">Error al cargar los pedidos.</div>';
    }
}

async function loadFechas() {
    try {
        fechasData = await dc.fetchFechas();
        if (!selectedDate) {
            selectedDate = dc.resolverFechaInicial(fechasData);
        }
        el('filterDate').value = selectedDate;
        renderDateChips();
        await loadOrders();
    } catch (e) {
        console.error(e);
    }
}

function renderOrders() {
    const finca = el('filterFinca')?.value || '';
    const query = el('searchClient')?.value || '';
    const filtered = dc.filtrarPedidos(allOrdersData, { finca, query });
    const container = el('ordersContainer');
    if (!container) return;

    if (!filtered.length) {
        container.innerHTML = '<div style="text-align:center; color:var(--mut); padding:40px;">No hay pedidos para los filtros seleccionados.</div>';
        return;
    }

    const byFinca = dc.agruparPorFinca(filtered);
    let html = '';
    for (const [fincaName, orders] of Object.entries(byFinca)) {
        html += `<div class="finca-group"><div class="finca-title">📍 ${dc.escHtml(fincaName)} (${orders.length} pedidos)</div><div class="grid">`;
        orders.forEach(o => { html += renderOrderCard(o); });
        html += `</div></div>`;
    }
    container.innerHTML = html;
    container.querySelectorAll('[data-order-punteo]').forEach(btn => btn.addEventListener('click', () => verInformePunteo(btn.dataset.orderPunteo)));
    container.querySelectorAll('[data-order-detail]').forEach(btn => btn.addEventListener('click', () => openOrderDetails(btn.dataset.orderSerie, btn.dataset.orderDetail)));
    container.querySelectorAll('[data-order-chat]').forEach(btn => btn.addEventListener('click', () => openChatModal(btn.dataset.orderChat, btn.dataset.orderChatLinea || '')));
    container.querySelectorAll('[data-order-obs]').forEach(btn => btn.addEventListener('click', () => openObsModal(btn.dataset.orderObsTitulo, btn.dataset.orderObsTexto)));
}

function renderOrderCard(o) {
    const tot = dc.totalesPlanta(o);
    const totalRequested = tot.solicitada;
    const totalPicked = tot.acopiada;
    const pct = totalRequested > 0 ? Math.min(100, Math.round((totalPicked / totalRequested) * 100)) : (totalPicked > 0 ? 100 : 0);
    const badgeHtml = dc.estadoBadgeHtml(o);
    const cardClass = dc.cardClassEstado(o);
    const brandBadge = o.marcaPedido ? `<span class="badge blink-prio b-yellow" style="margin-left:6px;">🏷️ ${dc.escHtml(o.marcaPedido)}</span>` : '';
    const qtyBox = `<span class="pill pill-ok" title="A acopiar · Acopiada · Asignada a faena" style="margin-left:6px;">🌱 ${tot.solicitada} · ✅ ${tot.acopiada} · 👷 ${tot.asignada}</span>`;
    const obsBtn = o.notasPedido
        ? `<button class="icon-btn" data-order-obs data-order-obs-titulo="Observaciones — Pedido #${o.numero}" data-order-obs-texto="${dc.jsAttr(o.notasPedido)}" title="Observaciones del pedido">📝</button>`
        : '';
    const msgBlink = dc.esSinLeer(o.numero, '') ? ' btn-msg-unread' : '';

    return `
    <div class="card ${cardClass}">
        <div class="card-header">
            <div>
                <div class="order-id">Pedido #${o.numero} ${brandBadge} ${qtyBox}</div>
                <div class="client-name">${dc.escHtml(o.cliente)}</div>
            </div>
            <div>${badgeHtml}</div>
        </div>
        <div style="font-size:0.8rem; color:var(--mut); margin-bottom:6px;">
            📅 Carga: <b>${dc.formatearFechaDDMMYYYY(o.fechaCarga) || 'N/D'}</b> | Zona: <b>${dc.escHtml(o.sector || 'N/D')}</b>
        </div>
        <div class="progress-box">
            <div class="progress-text">
                <span>Acopio Planta: ${totalPicked} / ${totalRequested} uds</span>
                <span><b>${pct}%</b></span>
            </div>
            <div class="progress-bar-bg">
                <div class="progress-bar-fill" style="width: ${pct}%"></div>
            </div>
        </div>
        <div class="card-actions">
            <button data-order-punteo="${o.numero}" style="background:var(--primary); color:var(--on-primary);">👁️ Informe Punteo</button>
            <button class="btn-sec" data-order-detail="${o.numero}" data-order-serie="${o.serie || ''}">🔍 Detalle</button>
            ${obsBtn}
            <button class="btn-sec${msgBlink}" data-unread-key="${o.numero}" data-order-chat="${o.numero}">💬 Mensajes</button>
        </div>
    </div>`;
}

// ---------- MODALES (detalle, punteo, chat, observaciones) ----------

function openPreviewModal(title, iframeSrc, pdfDownloadUrl) {
    let modal = document.getElementById('faena-preview-modal');
    if (!modal) {
        modal = document.createElement('div');
        modal.id = 'faena-preview-modal';
        modal.className = 'modal';
        modal.innerHTML = `<div class="modal-content" style="display:flex; flex-direction:column; gap:10px;">
            <div class="modal-header"><h2 id="faena-preview-title"></h2><button class="close-btn" data-close>✕</button></div>
            <div style="display:flex; gap:8px;">
                <button class="btn-sec" data-print style="padding:6px 12px;">🖨️ Imprimir</button>
                <a data-pdf class="btn-sec" href="#" target="_blank" style="padding:6px 12px; text-decoration:none;">⬇️ Descargar PDF</a>
            </div>
            <iframe id="faena-preview-frame" class="preview-frame" style="flex:1; min-height:480px;"></iframe>
        </div>`;
        modal.addEventListener('click', e => {
            if (e.target.closest('[data-close]')) modal.classList.remove('open');
        });
        document.body.appendChild(modal);
    }
    modal.querySelector('#faena-preview-title').innerText = title;
    const frame = modal.querySelector('#faena-preview-frame');
    frame.src = iframeSrc;
    const pdfBtn = modal.querySelector('[data-pdf]');
    if (pdfDownloadUrl) {
        pdfBtn.href = pdfDownloadUrl;
        pdfBtn.style.display = 'inline-block';
    } else {
        pdfBtn.style.display = 'none';
    }
    modal.querySelector('[data-print]').onclick = () => {
        if (frame.contentWindow) frame.contentWindow.focus();
        if (frame.contentWindow) frame.contentWindow.print();
    };
    modal.classList.add('open');
}

function verInformePunteo(numero) {
    const urls = dc.buildInformePunteoUrl(numero);
    openPreviewModal(`Informe Punteo — Pedido #${numero}`, urls.html, urls.pdf);
}

function verInformeDetalle(numero) {
    openPreviewModal(`Detalle del Pistoleo — Pedido #${numero}`, dc.buildInformeDetalleUrl(numero), null);
}

function verInformeControl(numero) {
    openPreviewModal(`Control de Acopio — Pedido #${numero}`, dc.buildInformeControlUrl(numero), null);
}

async function openOrderDetails(serie, numero) {
    try {
        const data = await dc.fetchDetallePedido(numero);
        let modal = document.getElementById('faena-order-modal');
        if (!modal) {
            modal = document.createElement('div');
            modal.id = 'faena-order-modal';
            modal.className = 'modal';
            modal.innerHTML = `<div class="modal-content">
                <div class="modal-header"><h2 id="faena-order-title"></h2><button class="close-btn" data-close>✕</button></div>
                <div id="faena-order-alerts"></div>
                <div id="faena-order-body"></div>
                <div style="display:flex; gap:8px; justify-content:flex-end; margin-top:12px;">
                    <button class="btn-sec" data-close>Cerrar</button>
                    <button id="faena-btn-punteo">📄 Punteo</button>
                    <button id="faena-btn-detalle" class="btn-sec">📋 Detalle Pistoleo</button>
                </div>
            </div>`;
            modal.addEventListener('click', e => { if (e.target.closest('[data-close]')) modal.classList.remove('open'); });
            document.body.appendChild(modal);
        }
        modal.querySelector('#faena-order-title').innerText = `Detalle Pedido #${data.numero} — ${data.cliente}`;
        const alertContainer = modal.querySelector('#faena-order-alerts');
        if (data.alertasCitricos && data.alertasCitricos.length > 0) {
            alertContainer.innerHTML = `
                <div class="alert-box">
                    <b>⚠️ ALERTA CRÍTICA: Cítrico Inmovilizado Detectado</b><br>
                    Referencias con restricciones fitosanitarias (Autorización NO):<br>
                    <ul>${data.alertasCitricos.map(a => `<li><b>${dc.escHtml(a.referencia)}</b> - ${dc.escHtml(a.descripcion)}</li>`).join('')}</ul>
                </div>`;
        } else {
            alertContainer.innerHTML = '';
        }
        let bodyHtml = `
            <div style="display:grid; grid-template-columns: repeat(2, 1fr); gap: 10px; margin-bottom: 14px; font-size: 0.85rem; background:var(--card2); padding:10px; border-radius:8px;">
                <div>📍 <b>Finca:</b> ${dc.escHtml(data.finca)}</div>
                <div>🚛 <b>Matrícula Camión:</b> ${dc.escHtml(data.matriculaCamion || 'Pendiente')}</div>
                <div>👤 <b>Operarios:</b> ${dc.escHtml(data.operarios || 'N/D')}</div>
                <div>📦 <b>Estado Carga:</b> ${data.cargado ? 'CARGADO ✓' : 'EN PROCESO'}</div>
            </div>
            <h3 style="font-size:0.95rem; margin-bottom:6px; color:var(--blue);">Líneas del Pedido</h3>
            <table>
                <thead><tr><th>Pos</th><th>Referencia</th><th>Descripción</th><th>Pedido</th><th>Acopiado</th><th>Sustituido</th></tr></thead>
                <tbody>`;
        (data.lineas || []).forEach(l => {
            bodyHtml += `<tr>
                <td>${l.posicion}</td>
                <td style="color:var(--blue);">${dc.escHtml(l.referencia)}</td>
                <td>${dc.escHtml(l.descripcion)}</td>
                <td>${l.pedido}</td>
                <td style="font-weight:700; color:var(--lime);">${l.acopiado}</td>
                <td>${l.sustituido ? '🔄 Sí' : '—'}</td>
            </tr>`;
        });
        bodyHtml += `</tbody></table>`;
        modal.querySelector('#faena-order-body').innerHTML = bodyHtml;
        modal.querySelector('#faena-btn-punteo').onclick = () => { modal.classList.remove('open'); verInformePunteo(data.numero); };
        modal.querySelector('#faena-btn-detalle').onclick = () => { modal.classList.remove('open'); verInformeDetalle(data.numero); };
        modal.classList.add('open');
    } catch (e) {
        console.error(e);
    }
}

async function openChatModal(pedidoId, lineaHuella) {
    dc.marcarLeido(lineaHuella ? `${pedidoId}|${lineaHuella}` : pedidoId);
    let modal = document.getElementById('faena-chat-modal');
    if (!modal) {
        modal = document.createElement('div');
        modal.id = 'faena-chat-modal';
        modal.className = 'modal';
        modal.innerHTML = `<div class="modal-content">
            <div class="modal-header"><h2 id="faena-chat-title"></h2><button class="close-btn" data-close>✕</button></div>
            <div id="faena-chat-messages" style="max-height:350px; overflow-y:auto; background:var(--card2); border:1px solid var(--line); border-radius:8px; padding:12px; margin-bottom:12px; display:flex; flex-direction:column; gap:8px;"></div>
            <div style="display:flex; gap:8px;">
                <input id="faena-chat-input" placeholder="Escribe un mensaje para los operarios..." style="flex:1;">
                <button id="faena-chat-send">Enviar</button>
            </div>
        </div>`;
        modal.addEventListener('click', e => { if (e.target.closest('[data-close]')) modal.classList.remove('open'); });
        document.body.appendChild(modal);
    }
    modal.querySelector('#faena-chat-title').innerText = `💬 Chat Pedido #${pedidoId}${lineaHuella ? ` (Línea ${lineaHuella})` : ''}`;
    const cont = modal.querySelector('#faena-chat-messages');
    cont.innerHTML = '<div style="color:var(--mut); text-align:center; padding:10px;">Cargando mensajes...</div>';
    modal.classList.add('open');
    try {
        const data = await dc.fetchComentarios(pedidoId, lineaHuella);
        const msgs = data.comentarios || [];
        if (!msgs.length) {
            cont.innerHTML = '<div style="color:var(--mut); text-align:center; padding:10px;">No hay mensajes en este chat.</div>';
            return;
        }
        cont.innerHTML = msgs.map(m => {
            const esOficina = m.rol === 'OFICINA' || m.rol === 'ADMIN';
            const bg = esOficina ? 'var(--ok-bg)' : 'var(--info-bg)';
            const align = esOficina ? 'margin-left:auto;' : 'margin-right:auto;';
            return `<div style="background:${bg}; border-radius:8px; padding:8px 12px; max-width:85%; ${align} font-size:0.85rem;">
                <div style="font-weight:700; color:var(--lime); font-size:0.75rem; margin-bottom:2px;">${dc.escHtml(m.autor_nombre || m.autor_email)} (${dc.escHtml(m.rol)})</div>
                <div>${dc.escHtml(m.texto)}</div>
                <div style="font-size:0.68rem; color:var(--mut); text-align:right; margin-top:2px;">${dc.escHtml(m.creado_en || '')}</div>
            </div>`;
        }).join('');
        cont.scrollTop = cont.scrollHeight;
        const sendBtn = modal.querySelector('#faena-chat-send');
        const input = modal.querySelector('#faena-chat-input');
        sendBtn.onclick = () => sendChatMessage(pedidoId, lineaHuella, input, cont);
        input.onkeypress = (e) => { if (e.key === 'Enter') sendChatMessage(pedidoId, lineaHuella, input, cont); };
    } catch (e) {
        // El chat GET/POST exige X-API-Key de la app (401 desde navegador, igual que /manager).
        console.error(e);
        cont.innerHTML = '<div style="color:var(--mut); text-align:center; padding:10px;">Chat disponible desde la app del operario (el panel no tiene credenciales de la API).</div>';
    }
}

async function sendChatMessage(pedidoId, lineaHuella, input, cont) {
    const texto = input.value.trim();
    if (!texto) return;
    try {
        const body = {
            pedido_id: pedidoId,
            linea_huella: lineaHuella || null,
            autor_email: 'gerencia@viveros.com',
            autor_nombre: 'Gerencia / Oficina',
            rol: 'OFICINA',
            canal: 'panel',
            texto: texto,
        };
        await dc.postComentario(body);
        input.value = '';
        const data = await dc.fetchComentarios(pedidoId, lineaHuella);
        const msgs = data.comentarios || [];
        cont.innerHTML = msgs.map(m => {
            const esOficina = m.rol === 'OFICINA' || m.rol === 'ADMIN';
            const bg = esOficina ? 'var(--ok-bg)' : 'var(--info-bg)';
            const align = esOficina ? 'margin-left:auto;' : 'margin-right:auto;';
            return `<div style="background:${bg}; border-radius:8px; padding:8px 12px; max-width:85%; ${align} font-size:0.85rem;">
                <div style="font-weight:700; color:var(--lime); font-size:0.75rem; margin-bottom:2px;">${dc.escHtml(m.autor_nombre || m.autor_email)} (${dc.escHtml(m.rol)})</div>
                <div>${dc.escHtml(m.texto)}</div>
                <div style="font-size:0.68rem; color:var(--mut); text-align:right; margin-top:2px;">${dc.escHtml(m.creado_en || '')}</div>
            </div>`;
        }).join('');
        cont.scrollTop = cont.scrollHeight;
    } catch (e) {
        console.error(e);
        alert('Chat disponible desde la app del operario (el panel no tiene credenciales de la API).');
    }
}

function openObsModal(title, text) {
    let modal = document.getElementById('faena-obs-modal');
    if (!modal) {
        modal = document.createElement('div');
        modal.id = 'faena-obs-modal';
        modal.className = 'modal';
        modal.innerHTML = `<div class="modal-content">
            <div class="modal-header"><h2 id="faena-obs-title"></h2><button class="close-btn" data-close>✕</button></div>
            <div id="faena-obs-content" style="font-size:0.9rem; line-height:1.6; color:var(--txt); background:var(--card2); padding:12px; border-radius:8px; border:1px solid var(--line);"></div>
        </div>`;
        modal.addEventListener('click', e => { if (e.target.closest('[data-close]')) modal.classList.remove('open'); });
        document.body.appendChild(modal);
    }
    modal.querySelector('#faena-obs-title').innerText = title;
    modal.querySelector('#faena-obs-content').innerHTML = `<b>${dc.escHtml(title)}</b><br><br>${dc.escHtml(text).replace(/\n/g, '<br>')}`;
    modal.classList.add('open');
}

// ---------- INICIALIZACIÓN ----------

export async function renderDashboardFaena(root, dataConnector) {
    if (!root || !dataConnector) return;
    dc = dataConnector;
    rootEl = root;
    selectedDate = '';
    fechasData = [];
    allOrdersData = [];
    filterEstado = 'todos';

    root.innerHTML = `
        <div id="faena-subnav"></div>
        <div id="faena-pedidos-panel">
            <div class="filters">
                <input type="date" id="filterDate">
                <select id="filterFinca"><option value="">Todas las Fincas</option></select>
                <input id="searchClient" placeholder="Buscar pedido, cliente, referencia..." style="flex:1; min-width:200px;">
            </div>
            <div class="chips" id="dateChips"></div>
            <div class="chips" id="faena-state-filters"></div>
            <div id="ordersContainer"><p class="text-muted">Cargando pedidos...</p></div>
        </div>
        <div id="faena-etiquetas-panel" style="display:none;"></div>
        <div id="faena-historico-panel" style="display:none;"></div>
        <div id="faena-reparto-panel" style="display:none;"></div>
        <div id="faena-carga-panel" style="display:none;"></div>
    `;

    renderFaenaSubnav(el('faena-subnav'), onSubtabChange);
    renderStateChips();
    el('filterDate').addEventListener('change', () => { selectedDate = el('filterDate').value; renderDateChips(); loadOrders(); });
    el('filterFinca').addEventListener('change', renderOrders);
    el('searchClient').addEventListener('input', renderOrders);
    await loadFechas();
}

async function onSubtabChange(subtab) {
    const key = SUBTABS[subtab];
    if (!key) return;
    const panels = {
        pedidos: 'faena-pedidos-panel',
        etiquetas: 'faena-etiquetas-panel',
        historico: 'faena-historico-panel',
        reparto: 'faena-reparto-panel',
        carga: 'faena-carga-panel',
    };
    Object.entries(panels).forEach(([k, id]) => {
        el(id).style.display = k === key ? 'block' : 'none';
    });
    const container = el(panels[key]);
    if (key === 'etiquetas') await renderEtiquetasDia(container, dc);
    else if (key === 'historico') await renderHistoricoFaena(container, dc);
    else if (key === 'reparto') await renderRepartoFaena(container, dc);
    else if (key === 'carga') await renderCargaOperarios(container, dc);
}