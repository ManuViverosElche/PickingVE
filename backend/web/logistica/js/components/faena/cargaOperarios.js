/**
 * faena/cargaOperarios.js — Submenú "Carga por operario".
 *
 * Réplica de la pestaña Carga por Operario de /manager con columna adicional
 * "Plantas" que muestra las líneas asignadas a cada operario agrupadas
 * por finca > pedido (estilo "mi faena" de la app), imprimibles.
 */
let dc = null;
let rootEl = null;
let cargaOpsGlobal = null;

function el(id) { return rootEl ? rootEl.querySelector(`#${id}`) : null; }
function escHtml(s) { return dc.escHtml(s); }

// ── Render tabla principal ──────────────────────────────────────────

function renderTablaCargaOperarios(filas) {
    if (!filas.length) {
        return `<div class="carga-box"><h3>⚖️ Carga por Operario</h3>
            <p style="color:var(--mut); font-size:0.82rem;">No hay operarios activos dados de alta en Configuración.</p></div>`;
    }
    const maxTot = Math.max(1, ...filas.map(f => (f.asignado || 0) + (f.preasignado || 0)));
    let html = `<div class="carga-box"><h3>⚖️ Carga por Operario <span style="font-weight:400; color:var(--mut); font-size:0.78rem;">— faena prevista total (todas las fechas de carga)</span></h3>
      <table class="carga-table">
        <thead><tr>
          <th>Operario</th><th>Maquinaria</th><th style="text-align:right;">Asignado</th><th style="text-align:right;">Preasignado*</th><th style="text-align:right;">Recogido</th><th style="text-align:right;"><b>Total a acopiar</b></th><th>Equilibrio</th><th style="text-align:center;">Plantas</th>
        </tr></thead><tbody>`;
    filas.forEach(f => {
        const total = (f.asignado || 0) + (f.preasignado || 0);
        const sobrecargado = total >= maxTot * 0.75 && total > 0;
        const preaTxt = f.preasignado > 0 ? `<span style="color:var(--warn);">+${f.preasignado}</span>` : (f.preasignado < 0 ? `<span style="color:var(--bad);">${f.preasignado}</span>` : '');
        html += `<tr style="${sobrecargado ? 'background:var(--bad-bg);' : ''}">
          <td><b>${escHtml(f.nombre)}</b></td>
          <td style="font-size:0.74rem; color:var(--mut);">${escHtml(f.maquinaria || '—')}</td>
          <td class="carga-num" style="text-align:right;">${f.asignado || 0}</td>
          <td class="carga-num" style="text-align:right;">${preaTxt || ''}</td>
          <td class="carga-num" style="text-align:right; color:var(--lime);">${f.recogido || 0}</td>
          <td class="carga-num ${sobrecargado ? 'sobrecarga' : ''}" style="text-align:right; font-weight:700; font-size:0.95rem;">${total}${sobrecargado ? ' ⚠' : ''}</td>
          <td><div style="background:var(--card2); border-radius:4px; height:8px; overflow:hidden;"><div style="height:100%; width:${Math.round(Math.max(0, total) / maxTot * 100)}%; background:${sobrecargado ? 'var(--bad)' : 'var(--lime)'};"></div></div></td>
          <td style="text-align:center;"><button class="btn-ver-faena" data-email="${escHtml(f.email)}" data-nombre="${escHtml(f.nombre)}" style="background:var(--info-bg); color:var(--info); border:1px solid var(--info-bd); border-radius:6px; padding:4px 10px; font-size:0.76rem; font-weight:700; cursor:pointer; white-space:nowrap;">📋 Ver</button></td>
        </tr>`;
    });
    html += `</tbody></table>
      <p style="font-size:0.72rem; color:var(--mut); margin-top:8px;">
        <b>Asignado</b>: faena guardada en el servidor (todas las fechas). <b>Recogido</b>: unidades ya pistoleadas (todas las fechas).
        <b>Total a acopiar</b> = asignado + preasignado. Filas en rojo concentran más faena: valora reasignar.</p></div>`;
    return html;
}

// ── Modal de faena por operario ─────────────────────────────────────

function renderFaenaModal(data) {
    const { fincas = [], totalPlantas = 0, operario = '' } = data;
    if (!fincas.length) {
        return `<div class="modal open" id="faenaModal">
          <div class="modal-content" style="max-width:700px;">
            <div class="modal-header">
              <h2>📋 Faena de ${escHtml(operario)}</h2>
              <button class="close-btn" id="closeFaenaModal">&times;</button>
            </div>
            <p style="color:var(--mut); text-align:center; padding:24px 0;">No hay líneas asignadas a este operario.</p>
          </div></div>`;
    }

    let html = `<div class="modal open" id="faenaModal">
      <div class="modal-content faena-operario-modal">
        <div class="modal-header">
          <div>
            <h2>📋 Faena — ${escHtml(operario)}</h2>
            <span style="font-size:0.82rem; color:var(--mut);">Total: <b style="color:var(--primary);">${totalPlantas} plantas</b> · ${fincas.length} finca${fincas.length > 1 ? 's' : ''}</span>
          </div>
          <div style="display:flex; gap:8px; align-items:center;">
            <button class="btn-sec" id="printFaena" style="font-size:0.82rem;">🖨️ Imprimir</button>
            <button class="close-btn" id="closeFaenaModal">&times;</button>
          </div>
        </div>
        <div class="faena-print-area">`;

    fincas.forEach(finca => {
        html += `<div class="faena-finca-group">
          <div class="faena-finca-header">
            <span class="faena-finca-name">🌱 ${escHtml(finca.finca)}</span>
            <span class="faena-finca-count">${finca.plantasPendientes} plantas</span>
          </div>`;

        finca.pedidos.forEach(ped => {
            html += `<div class="faena-pedido-card">
              <div class="faena-pedido-header">
                <div>
                  <span class="faena-pedido-id">#${escHtml(ped.orderId)}</span>
                  <span class="faena-pedido-cliente">${escHtml(ped.clienteDisplay)}</span>
                </div>
                <div style="text-align:right;">
                  <span class="faena-pedido-plantas">${ped.plantasPendientes} plantas</span>
                  ${ped.marcaPedido ? `<span class="badge b-blue" style="margin-left:6px;">${escHtml(ped.marcaPedido)}</span>` : ''}
                </div>
              </div>
              <div class="faena-pedido-meta">
                <span>📅 ${dc.formatearFechaDDMMYYYY(ped.fechaCarga) || 'N/D'}</span>
                ${ped.fincaCarga ? `<span>📦 Carga: ${escHtml(ped.fincaCarga)}</span>` : ''}
                ${ped.sectorCarga ? `<span>S: ${escHtml(ped.sectorCarga)}</span>` : ''}
              </div>`;

            ped.lineas.forEach((lin, i) => {
                const pct = lin.solicitadas > 0 ? Math.round(lin.acopiado / lin.solicitadas * 100) : 0;
                const estaCogida = lin.pendiente === 0 && lin.solicitadas > 0;
                const esParcial = lin.acopiado > 0 && lin.pendiente > 0;

                html += `<div class="faena-linea-row ${lin.prioridad === 'PRIORITARIO' ? 'faena-linea-prioritaria' : ''} ${lin.marcado ? 'faena-linea-marcada' : ''}">
                  <div class="faena-linea-top">
                    <span class="faena-linea-nombre">${escHtml(lin.producto)}</span>
                    <span class="faena-linea-pend">${lin.pendiente} uds</span>
                  </div>
                  <div class="faena-linea-meta">
                    <span>🌱 ${escHtml(lin.fincaProcedencia)}</span>
                    ${lin.sector ? `<span>S: ${escHtml(lin.sector)}</span>` : ''}
                    ${lin.litraje ? `<span>${escHtml(lin.litraje)}</span>` : ''}
                    ${lin.ubicacion ? `<span>📍 ${escHtml(lin.ubicacion)}</span>` : ''}
                  </div>`;

                if (lin.marca) {
                    html += `<div class="faena-linea-badges"><span class="badge b-yellow">Marca: ${escHtml(lin.marca)}</span></div>`;
                }
                if (lin.observaciones) {
                    html += `<div class="faena-linea-obs">📝 ${escHtml(lin.observaciones)}</div>`;
                }
                if (lin.prioridad === 'PRIORITARIO') {
                    html += `<div class="faena-linea-badges"><span class="badge faena-badge-prio">⚡ PRIORITARIO</span></div>`;
                }
                if (estaCogida) {
                    html += `<div class="faena-linea-badges"><span class="badge b-green">✅ COGIDA (${lin.solicitadas}/${lin.solicitadas})</span></div>`;
                } else if (esParcial) {
                    html += `<div class="faena-linea-badges"><span class="badge b-yellow">PARCIAL (cogidas ${lin.acopiado}/${lin.solicitadas})</span></div>`;
                }

                if (lin.solicitadas > 0) {
                    html += `<div class="faena-linea-progress">
                      <div class="progress-bar-bg"><div class="progress-bar-fill" style="width:${pct}%;background:${pct >= 100 ? 'var(--ok)' : pct > 0 ? 'var(--warn)' : 'var(--line)'};"></div></div>
                    </div>`;
                }
                html += `</div>`;
            });

            html += `</div>`;
        });
        html += `</div>`;
    });

    html += `</div></div></div>`;
    return html;
}

// ── Carga de datos ──────────────────────────────────────────────────

async function loadCarga() {
    const container = el('cargaContainer');
    try {
        const filas = await dc.fetchCarga();
        cargaOpsGlobal = filas;
        container.innerHTML = renderTablaCargaOperarios(filas);
        container.querySelectorAll('.btn-ver-faena').forEach(btn => {
            btn.addEventListener('click', () => abrirFaenaOperario(btn.dataset.email, btn.dataset.nombre));
        });
    } catch (e) {
        console.error(e);
        container.innerHTML = '<div style="color:var(--bad); text-align:center; padding:20px;">Error al cargar la carga de operarios.</div>';
    }
}

async function abrirFaenaOperario(email, nombre) {
    const modal = el('faenaModalWrap');
    if (!modal) return;
    modal.innerHTML = `<div class="modal open" style="z-index:2000;"><div class="modal-content" style="max-width:600px; text-align:center; padding:40px;">
      <p style="color:var(--mut);">Cargando faena de <b>${escHtml(nombre)}</b>...</p></div></div>`;
    try {
        if (typeof dc.fetchFaenaOperario !== 'function') {
            throw new Error('fetchFaenaOperario no disponible — recarga la página (Ctrl+Shift+R)');
        }
        const data = await dc.fetchFaenaOperario(email);
        modal.innerHTML = renderFaenaModal({ ...data, operario: nombre || email });
        document.getElementById('closeFaenaModal')?.addEventListener('click', () => { modal.innerHTML = ''; });
        document.getElementById('printFaena')?.addEventListener('click', () => { window.print(); });
        modal.querySelector('.modal')?.addEventListener('click', e => {
            if (e.target === e.currentTarget) modal.innerHTML = '';
        });
    } catch (e) {
        console.error('Error cargando faena del operario:', email, e);
        modal.innerHTML = `<div class="modal open" style="z-index:2000;"><div class="modal-content" style="max-width:500px; text-align:center; padding:40px;">
          <p style="color:var(--bad);">Error al cargar la faena de ${escHtml(nombre)}.</p>
          <p style="font-size:0.78rem; color:var(--mut); margin-top:8px;">${escHtml(e.message || String(e))}</p>
          <button class="btn-sec" onclick="this.closest('.modal').parentElement.innerHTML=''" style="margin-top:12px;">Cerrar</button></div></div>`;
    }
}

// ── Export ───────────────────────────────────────────────────────────

export async function renderCargaOperarios(root, dataConnector) {
    if (!root || !dataConnector) return;
    dc = dataConnector;
    rootEl = root;
    cargaOpsGlobal = null;
    root.innerHTML = `
        <div class="carga-box" style="background:var(--card); border:1px solid var(--line); border-radius:14px; padding:16px 18px; margin-bottom:18px;">
            <h3 style="color:var(--info); font-size:1rem; margin-bottom:10px;">⚖️ Carga por Operario</h3>
            <p style="font-size:0.82rem; color:var(--mut);">Faena prevista total — todas las fechas de carga. La tabla muestra la carga real por operario, con preasignación de cambios de reparto sin guardar.</p>
            <div style="display:flex; gap:8px; margin-top:10px;">
                <button class="btn-sec" id="carga-refresh">🔄 Actualizar</button>
            </div>
        </div>
        <div id="cargaContainer"><p class="text-muted">Cargando tabla de carga por operario...</p></div>
        <div id="faenaModalWrap"></div>
    `;
    el('carga-refresh').addEventListener('click', loadCarga);
    await loadCarga();
}
