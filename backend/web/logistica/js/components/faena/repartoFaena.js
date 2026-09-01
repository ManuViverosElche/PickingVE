/**
 * faena/repartoFaena.js — Submenú "Reparto de faena".
 *
 * Réplica COMPLETA de la pestaña Reparto de Faena de /manager:
 *   - Fecha inicial resuelta desde /api/manager/fechas (la más próxima con faena).
 *   - Chips de fechas previstas.
 *   - Tarjetas por pedido con tabla de líneas: Referencia, Artículo, Litraje,
 *     Sector, Ubicación, Finca, Prioridad, Pend., Acop., Cogido Op.,
 *     Encargado (Pistoleo), Operario Faena y Acciones (Info / Chat por línea).
 *   - Selector de operario por línea (asignación por EMAIL, D-185).
 *   - Cambios sin guardar: contador, conservación al re-renderizar y al cambiar
 *     de fecha (D-228), cálculo de carga global con preasignación.
 *   - Guardar reparto (POST /api/manager/reparto).
 *   - Un solo pedido abierto a la vez (D-194).
 */
let dc = null;
let rootEl = null;
let fechasData = [];
let selectedDate = '';
let operariosList = [];
let encargadosList = [];
let cambiosFaenaPendientes = {};
let detalleOpsGlobal = {};
let repartoPedidoAbierto = null;
let filterFincaReparto = '';

function el(id) { return rootEl ? rootEl.querySelector(`#${id}`) : null; }

function escHtml(s) { return dc.escHtml(s); }
function jsAttr(s) { return dc.jsAttr(s); }

async function loadUnread() { try { await dc.fetchRecientes(); } catch (e) {} }

function esSinLeer(pedidoId, lineaHuella) { return dc.esSinLeer(pedidoId, lineaHuella); }

async function loadOperarios() {
    try { operariosList = await dc.fetchOperariosLista(); } catch (e) { operariosList = []; }
}

async function loadEncargados() {
    try { encargadosList = await dc.fetchEncargadosLista(); } catch (e) { encargadosList = []; }
}

function renderDateChipsReparto() {
    const cont = el('dateChipsReparto');
    if (!cont) return;
    const hoy = new Date();
    const hoyStr = `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, '0')}-${String(hoy.getDate()).padStart(2, '0')}`;
    const previstas = fechasData.filter(f => f.fecha >= hoyStr).slice(0, 10);
    if (!previstas.length) {
        cont.innerHTML = '<span style="color:var(--mut); font-size:0.85rem;">No hay fechas con faena pendiente.</span>';
        return;
    }
    cont.innerHTML = previstas.map(f => {
        const activo = f.fecha === selectedDate ? ' chip-activo' : '';
        return `<button class="chip chip-cargado${activo}" data-fecha="${f.fecha}">📅 ${dc.formatearFechaDDMMYYYY(f.fecha)} (${f.pedidos} ped)</button>`;
    }).join('');
    cont.querySelectorAll('[data-fecha]').forEach(btn => btn.addEventListener('click', () => selectFechaReparto(btn.dataset.fecha)));
}

function selectFechaReparto(fecha) {
    // D-228: los cambios sin guardar se conservan al cambiar de fecha.
    repartoPedidoAbierto = null;
    selectedDate = fecha;
    renderDateChipsReparto();
    renderReparto();
}

function renderFincaButtons(orders) {
    const cont = el('fincaButtonsReparto');
    if (!cont) return;
    const fincas = [...new Set(orders.map(o => o.finca).filter(Boolean))].sort();
    if (!fincas.length) {
        cont.innerHTML = '';
        return;
    }
    let html = `<button class="finca-btn${filterFincaReparto === '' ? ' active' : ''}" data-finca="">Todas</button>`;
    fincas.forEach(f => {
        html += `<button class="finca-btn${filterFincaReparto === f ? ' active' : ''}" data-finca="${escHtml(f)}">${escHtml(f)}</button>`;
    });
    cont.innerHTML = html;
    cont.querySelectorAll('.finca-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            filterFincaReparto = btn.dataset.finca;
            renderFincaButtons(orders);
            renderReparto();
        });
    });
}

function totalesPlanta(o) { return dc.totalesPlanta(o); }

async function renderReparto() {
    await loadUnread();
    if (!operariosList.length) await loadOperarios();
    if (!encargadosList.length) await loadEncargados();
    const dateVal = selectedDate || '';
    const dateInput = el('filterDateReparto');
    if (dateInput) dateInput.value = dateVal;

    try {
        const data = await dc.fetchPedidosDia(dateVal, 'todos');
        const orders = data;

        renderFincaButtons(orders);

        const searchVal = (el('searchReparto')?.value || '').toLowerCase();

        const filtered = orders.filter(o => {
            const matchFinca = !filterFincaReparto || (o.finca || '').toUpperCase() === filterFincaReparto.toUpperCase();
            const matchSearch = !searchVal ||
                (o.numero || '').toLowerCase().includes(searchVal) ||
                (o.cliente || '').toLowerCase().includes(searchVal) ||
                (o.clienteFiscal || '').toLowerCase().includes(searchVal);
            return matchFinca && matchSearch;
        });

        const container = el('repartoContainer');
        if (!filtered.length) {
            container.innerHTML = '<div style="text-align:center; color:var(--mut); padding:40px;">No hay pedidos para esta fecha y filtros de reparto.</div>';
            return;
        }

        container.innerHTML = filtered.map(renderPedidoRepartoCard).join('');
        container.querySelectorAll('select[data-pedido]').forEach(sel => sel.addEventListener('change', () => cambiarOperarioFaena(sel)));
        container.querySelectorAll('[data-toggle-reparto]').forEach(hdr => hdr.addEventListener('click', () => toggleRepartoOrder(hdr.dataset.toggleReparto)));
        container.querySelectorAll('[data-chat-linea]').forEach(btn => btn.addEventListener('click', () => openChatModal(btn.dataset.chatPedido, btn.dataset.chatLinea)));
        container.querySelectorAll('[data-obs]').forEach(btn => btn.addEventListener('click', () => openObsModal(btn.dataset.obsTitulo, btn.dataset.obsTexto)));
        actualizarContadorPendientes();
    } catch (e) {
        console.error(e);
        el('repartoContainer').innerHTML = '<div style="color:var(--bad); text-align:center; padding:20px;">Error al cargar el reparto de faena.</div>';
    }
}

function renderPedidoRepartoCard(o) {
    const fiscalName = o.clienteFiscal || o.cliente || 'N/D';
    const commercialDiff = o.cliente && o.cliente !== o.clienteFiscal ? `<span style="font-style:italic; color:var(--blue); margin-left:6px;">(${escHtml(o.cliente)})</span>` : '';
    const orderRef = o.referenciaPedido ? `<span style="color:var(--warn); margin-left:10px;">Ref.: <b>${escHtml(o.referenciaPedido)}</b></span>` : '';
    const tot = totalesPlanta(o);
    const qtyBox = `<span class="pill pill-ok" title="Cantidad de planta a acopiar / acopiada / con operario asignado (sin refs 99990-99999)" style="margin-left:8px;">🌱 ${tot.solicitada} · ✅ ${tot.acopiada} · 👷 ${tot.asignada}</span>`;
    const brandHeader = o.marcaPedido ? `<span class="badge blink-prio b-yellow" style="margin-left:8px;">🏷️ ${escHtml(o.marcaPedido)}</span>` : '';
    const comercial = o.agente ? `Comercial: <b>${escHtml(o.agente)}</b>` : 'Comercial: N/D';
    const zone = o.sector || 'N/D';
    const obsBtn = o.notasPedido
        ? `<button class="icon-btn" data-obs data-obs-titulo="Observaciones — Pedido #${o.numero}" data-obs-texto="${jsAttr(o.notasPedido)}" title="Observaciones del pedido">📝 Observaciones</button>`
        : '';
    const msgBlink = esSinLeer(o.numero, '') ? ' btn-msg-unread' : '';

    // D-227: conservar el pedido desplegado al re-renderizar.
    const cardKey = `${o.serie}_${o.numero}`;
    const cardAbierta = repartoPedidoAbierto === cardKey;

    const encEmailSet = new Set((encargadosList || []).filter(e => e.activo !== false).map(e => String(e.email || '').trim().toLowerCase()));
    const nombrePorEmail = {};
    (encargadosList || []).forEach(e => { const k = String(e.email || '').trim().toLowerCase(); if (k) nombrePorEmail[k] = e.nombre || k; });
    (operariosList || []).forEach(op => { const k = String(op.email || '').trim().toLowerCase(); if (k && !nombrePorEmail[k]) nombrePorEmail[k] = op.nombre || k; });

    let lineasHtml = (o.lineas || []).map((l, idx) => {
        const lineNum = l.posicion || (idx + 1);
        // D-193: SOLO dos etiquetas posibles.
        const prioTxt = (l.prioridadTexto || '').toUpperCase().trim();
        let prioBadge = '';
        if (prioTxt === 'PRIORITARIO') {
            prioBadge = '<span class="badge b-yellow blink-prio">⭐ Prioritario</span>';
        } else if (prioTxt === 'NO PRIORITARIO') {
            prioBadge = '<span class="badge badge-prio-roja blink-prio">NO PRIORITARIO</span>';
        }

        let marcaInfo = '';
        if (l.marca && l.marca !== o.marcaPedido) {
            marcaInfo = `<br><small class="marca-linea-aviso">⚠ Marca distinta: ${escHtml(l.marca)}</small>`;
        } else if (l.marca) {
            marcaInfo = `<br><small style="color:var(--mut);">Marca: ${escHtml(l.marca)}</small>`;
        }

        let rowStyle = '';
        const pedidoKey = `${o.serie}_${o.numero}`;
        const cambioPendiente = (cambiosFaenaPendientes[pedidoKey] || {})[String(lineNum)];
        if (cambioPendiente !== undefined) {
            rowStyle = 'border-left: 3px solid var(--info) !important; background: var(--info-bg);';
        } else if (l.marcado) {
            rowStyle = 'border-left: 3px solid var(--warn) !important; background: var(--warn-bg);';
        } else if (prioTxt === 'PRIORITARIO') {
            rowStyle = 'background: #fff8e1;';
        } else if (l.pendientes === 0 && l.acopiado > 0) {
            rowStyle = 'background: var(--ok-bg);';
        }

        const detalleOps = {};
        (l.detalleOperarios || '').split(',').forEach(par => {
            const idxColon = par.indexOf(':');
            if (idxColon > 0) {
                const em = par.slice(0, idxColon).trim().toLowerCase();
                const cant = parseInt(par.slice(idxColon + 1)) || 0;
                if (em) detalleOps[em] = cant;
            }
        });
        detalleOpsGlobal[`${o.serie}_${o.numero}_${lineNum}`] = detalleOps;

        const encargadosDeLinea = Object.keys(detalleOps).filter(em => encEmailSet.has(em));
        const encargadoPick = encargadosDeLinea.length
            ? escHtml(encargadosDeLinea.map(em => nombrePorEmail[em] || em).join(', '))
            : '—';

        // D-227: el cambio sin guardar manda sobre lo guardado al re-renderizar.
        const operarioEfectivo = (cambioPendiente !== undefined)
            ? (cambioPendiente.email || '')
            : (l.operarioAsignadoEmail || '');

        const operarioEfectivoNorm = (operarioEfectivo || '').trim().toLowerCase();
        const cogidoAsignado = operarioEfectivoNorm
            ? (detalleOps[operarioEfectivoNorm] ?? 0)
            : '—';

        let opOptions = '<option value="">-- Sin asignar --</option>';
        operariosList.forEach(op => {
            if (!op.activo) return;
            const sel = ((operarioEfectivo || '') === op.email) ? 'selected' : '';
            opOptions += `<option value="${escHtml(op.email)}" ${sel}>${escHtml(op.nombre)}</option>`;
        });

        const huella = l.huellaDigital || String(lineNum);
        const infoBtn = (l.observaciones || '').trim()
            ? `<button class="icon-btn" data-obs data-obs-titulo="Observaciones línea ${lineNum} — Ref ${jsAttr(l.referencia)}" data-obs-texto="${jsAttr(l.observaciones)}" title="${escHtml(l.observaciones)}">📝 Info</button>`
            : '';
        const lineMsgBlink = esSinLeer(o.numero, l.huellaDigital) ? ' btn-msg-unread' : '';

        return `
            <tr style="${rowStyle}">
              <td><b>${lineNum}</b></td>
              <td style="color:var(--blue); font-weight:700; white-space:nowrap;">${escHtml(l.referencia)}</td>
              <td>${escHtml(l.descripcion)} ${marcaInfo}</td>
              <td style="white-space:nowrap;">${escHtml(l.litraje || '—')}</td>
              <td style="white-space:nowrap;">${escHtml(l.sector || '—')}</td>
              <td style="color:var(--mut); white-space:nowrap;">${escHtml(l.ubicacionExtra || '—')}</td>
              <td style="white-space:nowrap;">${escHtml(l.fincaLinea || o.finca || '—')}</td>
              <td style="white-space:nowrap;">${prioBadge}</td>
              <td><b>${l.pendientes}</b></td>
              <td style="color:var(--warn); font-weight:700;" title="D-274: Acopio físico del operario de campo">${l.acopiadoOperario || 0}</td>
              <td style="color:var(--lime); font-weight:700;" title="D-274: Verificación del encargado de picking">${l.acopiado}</td>
              <td id="cogido_${o.serie}_${o.numero}_${lineNum}" title="Plantas cogidas por el operario de faena asignado" style="font-weight:700; color:var(--warn);">${cogidoAsignado}</td>
              <td title="Encargados que realmente pistolearon la línea" style="font-size:0.78rem; font-weight:700; color:var(--blue);">${encargadoPick}</td>
              <td>
                <select style="width:120px; font-size:0.78rem;" data-pedido="${o.serie}_${o.numero}" data-linea="${lineNum}" data-huella="${escHtml(huella)}" data-pendientes="${l.pendientes}" data-acopiado="${l.acopiado}" data-origen="${escHtml(l.operarioAsignadoEmail || '')}">
                  ${opOptions}
                </select>
              </td>
              <td>
                <div style="display:flex; gap:4px; align-items:center;">
                  ${infoBtn}
                  <button class="btn-sec${lineMsgBlink}" data-chat-pedido="${o.numero}" data-chat-linea="${huella}" style="padding:2px 6px; font-size:0.7rem;" title="Chat / Mensajes">💬</button>
                </div>
              </td>
            </tr>`;
    }).join('');

    return `
        <div class="card reparto-bloque${cardAbierta ? ' abierto' : ''}" style="margin-bottom:16px; border-color:var(--line);">
            <div class="card-header reparto-hdr" style="cursor:pointer;" data-toggle-reparto="${cardKey}">
              <div>
                <div class="order-id">📦 Pedido #${o.numero} ${orderRef} ${brandHeader} ${qtyBox}</div>
                <div class="client-name" style="font-size:0.95rem; margin-top:2px;"><b>${escHtml(fiscalName)}</b> ${commercialDiff}</div>
                <div style="font-size:0.8rem; color:var(--mut); margin-top:4px;">📍 Finca: <b>${escHtml(o.finca || 'S/F')}</b> | Zona carga: <b>${escHtml(zone)}</b> | 👤 ${comercial}</div>
              </div>
              <div style="display:flex; gap:8px; align-items:center;">
                ${obsBtn}
                <button class="btn-sec${msgBlink}" data-chat-pedido="${o.numero}" data-chat-linea="" style="padding:4px 8px; font-size:0.75rem;" onclick="event.stopPropagation()">💬 Mensajes</button>
                <span id="arrow_${cardKey}" style="font-size:0.85rem; color:var(--lime); font-weight:700;">${cardAbierta ? '▲ Líneas' : '▼ Líneas'}</span>
              </div>
            </div>
            <div id="reparto_lines_${cardKey}" style="display:${cardAbierta ? 'block' : 'none'}; margin-top:12px; border-top:1px solid var(--line); padding-top:0; overflow-x:auto; overflow-y:auto; max-height:62vh;">
              <table>
                <thead>
                  <tr>
                    <th>Línea</th><th>Referencia</th><th>Artículo</th><th>Litraje</th><th>Sector</th><th>Ubicación</th><th>Finca</th><th>Prioridad</th><th>Pend.</th>
                    <th title="D-274: Acopio físico del operario de campo (picking_tipo='I')" style="color:var(--warn);">Acop. Op.</th>
                    <th title="D-274: Verificación del encargado de picking (picking_tipo='F')" style="color:var(--lime);">Verificado</th>
                    <th title="Plantas cogidas por el operario de faena asignado (0 si no ha cogido)">Cogido Op.</th>
                    <th title="Encargados que realmente pistolearon la línea en el acopio">Encargado (Pistoleo)</th>
                    <th title="Operario de faena asignado (reparto)">Operario Faena</th>
                    <th>Acciones</th>
                  </tr>
                </thead>
                <tbody>${lineasHtml}</tbody>
              </table>
            </div>
        </div>`;
}

function toggleRepartoOrder(key) {
    const elm = document.getElementById(`reparto_lines_${key}`);
    const card = elm ? elm.closest('.reparto-bloque') : null;
    const arrow = document.getElementById(`arrow_${key}`);
    if (!elm || !card) return;
    const abrir = elm.style.display === 'none';
    // D-194: solo un pedido abierto a la vez
    document.querySelectorAll('.reparto-bloque.abierto').forEach(b => {
        b.classList.remove('abierto');
        const ln = b.querySelector('[id^="reparto_lines_"]');
        const ar = b.querySelector('[id^="arrow_"]');
        if (ln) ln.style.display = 'none';
        if (ar) ar.textContent = '▼ Líneas';
    });
    if (abrir) {
        elm.style.display = 'block';
        card.classList.add('abierto');
        repartoPedidoAbierto = key;
        if (arrow) arrow.textContent = '▲ Líneas';
    } else {
        repartoPedidoAbierto = null;
        if (arrow) arrow.textContent = '▼ Líneas';
    }
}

function actualizarContadorPendientes() {
    const elm = el('contadorPendientes');
    if (!elm) return;
    let n = 0;
    Object.values(cambiosFaenaPendientes).forEach(l => { n += Object.keys(l || {}).length; });
    if (n > 0) {
        elm.textContent = `⚠ ${n} cambio${n > 1 ? 's' : ''} sin guardar`;
        elm.style.display = '';
    } else {
        elm.style.display = 'none';
    }
}

function cambiarOperarioFaena(sel) {
    const pedido = sel.getAttribute('data-pedido');
    const linea = sel.getAttribute('data-linea');
    const operario = sel.value;
    if (!cambiosFaenaPendientes[pedido]) cambiosFaenaPendientes[pedido] = {};
    // D-228: se guarda todo el contexto de la línea para poder preasignar faena
    // de distintas fechas de carga y calcular la carga global al guardar.
    cambiosFaenaPendientes[pedido][linea] = {
        email: operario,
        huella: sel.getAttribute('data-huella') || '',
        pendientes: parseInt(sel.getAttribute('data-pendientes') || '0', 10) || 0,
        acopiado: parseInt(sel.getAttribute('data-acopiado') || '0', 10) || 0,
        origen: sel.getAttribute('data-origen') || '',
    };
    const cell = document.getElementById(`cogido_${pedido}_${linea}`);
    if (cell && operario) {
        const det = (detalleOpsGlobal[`${pedido}_${linea}`] || {})[operario.toLowerCase()];
        cell.textContent = det !== undefined ? det : '0';
    } else if (cell) {
        cell.textContent = '—';
    }
    actualizarContadorPendientes();
}

async function guardarReparto() {
    const cambios = [];
    Object.entries(cambiosFaenaPendientes).forEach(([pedidoKey, lineas]) => {
        Object.entries(lineas).forEach(([linea, c]) => {
            if (!c || c.email === undefined) return;
            cambios.push({
                pedido_id: pedidoKey.split('_').pop(),
                linea_huella: c.huella || String(linea),
                operario_email: c.email || '',
                operario_nombre: (operariosList.find(op => op.email === c.email) || {}).nombre || '',
            });
        });
    });
    if (!cambios.length) {
        alert('No hay cambios de asignación pendientes de guardar.');
        return;
    }
    if (!confirm(`¿Guardar el reparto de faena?\n\nSe enviarán ${cambios.length} asignación(es). Los operarios las verán cuando se active el módulo de faena en la app.`)) return;
    try {
        const res = await dc.guardarReparto({ asignaciones: cambios });
        if (res && res.ok) {
            alert(`✅ Reparto guardado: ${res.guardadas} asignación(es) registradas${res.borradas ? `, ${res.borradas} desasignada(s)` : ''}.`);
            cambiosFaenaPendientes = {};
            await renderReparto();
            actualizarContadorPendientes();
        } else {
            alert('Error al guardar el reparto: ' + (res?.detail || 'desconocido'));
        }
    } catch (e) {
        console.error(e);
        alert('Error de red al guardar el reparto');
    }
}

async function loadFechas() {
    try {
        fechasData = await dc.fetchFechas();
        const fechaInput = el('filterDateReparto');
        if (!selectedDate) {
            selectedDate = dc.resolverFechaInicial(fechasData);
        }
        if (fechaInput) fechaInput.value = selectedDate;
        renderDateChipsReparto();
    } catch (e) {
        console.error(e);
        if (!selectedDate) selectedDate = dc.hoyStr();
    }
}

// ---- Mensajes / Observaciones (mismo contrato que /manager) ----
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
            <div style="display:flex; gap:8px;"><input id="faena-chat-input" placeholder="Escribe un mensaje para los operarios..." style="flex:1;"><button id="faena-chat-send">Enviar</button></div>
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
        if (!msgs.length) { cont.innerHTML = '<div style="color:var(--mut); text-align:center; padding:10px;">No hay mensajes en este chat.</div>'; return; }
        cont.innerHTML = msgs.map(m => {
            const esOficina = m.rol === 'OFICINA' || m.rol === 'ADMIN';
            const bg = esOficina ? 'var(--ok-bg)' : 'var(--info-bg)';
            const align = esOficina ? 'margin-left:auto;' : 'margin-right:auto;';
            return `<div style="background:${bg}; border-radius:8px; padding:8px 12px; max-width:85%; ${align} font-size:0.85rem;">
                <div style="font-weight:700; color:var(--lime); font-size:0.75rem; margin-bottom:2px;">${escHtml(m.autor_nombre || m.autor_email)} (${escHtml(m.rol)})</div>
                <div>${escHtml(m.texto)}</div>
                <div style="font-size:0.68rem; color:var(--mut); text-align:right; margin-top:2px;">${escHtml(m.creado_en || '')}</div>
            </div>`;
        }).join('');
        cont.scrollTop = cont.scrollHeight;
        const sendBtn = modal.querySelector('#faena-chat-send');
        const input = modal.querySelector('#faena-chat-input');
        sendBtn.onclick = () => sendChatMessage(pedidoId, lineaHuella, input, cont);
        input.onkeypress = (e) => { if (e.key === 'Enter') sendChatMessage(pedidoId, lineaHuella, input, cont); };
    } catch (e) {
        console.error(e);
        cont.innerHTML = '<div style="color:var(--mut); text-align:center; padding:10px;">Chat disponible desde la app del operario (el panel no tiene credenciales de la API).</div>';
    }
}

async function sendChatMessage(pedidoId, lineaHuella, input, cont) {
    const texto = input.value.trim();
    if (!texto) return;
    try {
        await dc.postComentario({
            pedido_id: pedidoId, linea_huella: lineaHuella || null,
            autor_email: 'gerencia@viveros.com', autor_nombre: 'Gerencia / Oficina',
            rol: 'OFICINA', canal: 'panel', texto,
        });
        input.value = '';
        const data = await dc.fetchComentarios(pedidoId, lineaHuella);
        const msgs = data.comentarios || [];
        cont.innerHTML = msgs.map(m => {
            const esOficina = m.rol === 'OFICINA' || m.rol === 'ADMIN';
            const bg = esOficina ? 'var(--ok-bg)' : 'var(--info-bg)';
            const align = esOficina ? 'margin-left:auto;' : 'margin-right:auto;';
            return `<div style="background:${bg}; border-radius:8px; padding:8px 12px; max-width:85%; ${align} font-size:0.85rem;">
                <div style="font-weight:700; color:var(--lime); font-size:0.75rem; margin-bottom:2px;">${escHtml(m.autor_nombre || m.autor_email)} (${escHtml(m.rol)})</div>
                <div>${escHtml(m.texto)}</div>
                <div style="font-size:0.68rem; color:var(--mut); text-align:right; margin-top:2px;">${escHtml(m.creado_en || '')}</div>
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
    modal.querySelector('#faena-obs-content').innerHTML = `<b>${escHtml(title)}</b><br><br>${escHtml(text).replace(/\n/g, '<br>')}`;
    modal.classList.add('open');
}

export async function renderRepartoFaena(root, dataConnector) {
    if (!root || !dataConnector) return;
    dc = dataConnector;
    rootEl = root;
    fechasData = [];
    selectedDate = '';
    operariosList = [];
    encargadosList = [];
    cambiosFaenaPendientes = {};
    detalleOpsGlobal = {};
    repartoPedidoAbierto = null;
    filterFincaReparto = '';

    root.innerHTML = `
        <div class="filters">
            <input type="text" id="searchReparto" placeholder="Buscar pedido, cliente, ref..." style="flex:1; min-width:180px;">
            <span id="contadorPendientes" style="font-size:0.78rem; font-weight:700; color:var(--warn); display:none;"></span>
            <button id="reparto-save">💾 Guardar Reparto</button>
            <button class="btn-sec" id="reparto-refresh">🔄 Actualizar Faena</button>
        </div>
        <div class="chips" id="fincaButtonsReparto"></div>
        <div class="chips" id="dateChipsReparto"></div>
        <div id="repartoContainer"><p class="text-muted">Cargando reparto de faena...</p></div>
    `;

    el('searchReparto').addEventListener('input', renderReparto);
    el('reparto-save').addEventListener('click', guardarReparto);
    el('reparto-refresh').addEventListener('click', renderReparto);

    await loadFechas();
    await renderReparto();
}