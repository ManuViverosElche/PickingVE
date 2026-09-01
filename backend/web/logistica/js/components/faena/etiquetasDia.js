/**
 * etiquetasDia.js — Componente "Etiquetas a Sacar" (subpestaña de Faena).
 *
 * Replica la funcionalidad de /manager.renderEtiquetasDia + marcarEtiquetaDia
 * usando SIEMPRE el dataConnector (sin fetch directo).
 *
 * El botón "Imprimir Etiquetas" abre un modal de configuración (printModal.js)
 * que genera el lote con printBatchBuilder.js y lo envía al motor de impresión
 * masiva existente (#print-batch-container + window.print()).
 */
import { openPrintModal } from '/logistica/js/components/printModal.js?k=logistica-2026';

let dc = null;
let _lastPedidos = [];

export async function renderEtiquetasDia(root, dataConnector) {
    if (!root || !dataConnector) return;
    dc = dataConnector;
    root.innerHTML = `
        <div class="filters">
            <input type="date" id="filterDateEtiquetas">
            <button id="etiquetas-dia-load">Cargar etiquetas del día</button>
            <button class="btn-sec" id="etiquetas-dia-informe">📄 Informe del día</button>
        </div>
        <div id="etiquetasDiaResumen" class="chips"></div>
        <div id="etiquetasDiaBody"><p class="text-muted">Selecciona una fecha y pulsa "Cargar etiquetas del día".</p></div>
    `;
    const dateInput = root.querySelector('#filterDateEtiquetas');
    dateInput.value = dataConnector.hoyStr();
    root.querySelector('#etiquetas-dia-load').addEventListener('click', () => loadEtiquetasDia(dateInput.value));
    root.querySelector('#etiquetas-dia-informe').addEventListener('click', () => {
        const dateVal = dateInput.value;
        const url = `/api/manager/etiquetas/dia/informe?fecha=${dateVal}&k=logistica-2026`;
        window.open(url, '_blank');
    });

    // Delegación de eventos en el body de etiquetas: un único listener que
    // sobrevive a los re-renderizados de loadEtiquetasDia. El binding NO depende
    // del orden render→bind ni de que el render completo termine sin errores,
    // y resuelve el pedido real desde _lastPedidos para abrir el modal.
    const body = root.querySelector('#etiquetasDiaBody');
    body.addEventListener('click', async (e) => {
        const printBtn = e.target.closest('[data-order-print-labels]');
        if (printBtn) {
            e.preventDefault();
            e.stopPropagation();
            const numero = printBtn.dataset.orderPrintLabels;
            const pedido = _lastPedidos.find(p => String(p.pedido) === String(numero));
            if (!pedido) {
                alert('No se encontraron datos del pedido para imprimir.');
                return;
            }
            openPrintModal(pedido, dc);
            return;
        }
        const estadoBtn = e.target.closest('[data-estado]');
        if (estadoBtn) {
            e.preventDefault();
            e.stopPropagation();
            try {
                await dc.marcarEtiquetaDia({
                    pedido: estadoBtn.dataset.pedido,
                    referencia: estadoBtn.dataset.ref,
                    litraje: estadoBtn.dataset.litraje,
                    sector: estadoBtn.dataset.sector,
                    estado: estadoBtn.dataset.estado,
                    por: 'Logistica'
                });
                await loadEtiquetasDia(dateInput.value);
            } catch (err) {
                console.error(err);
                alert('Error al marcar estado de la etiqueta.');
            }
            return;
        }
    });

    await loadEtiquetasDia(dateInput.value);
}

async function loadEtiquetasDia(fecha) {
    const body = document.getElementById('etiquetasDiaBody');
    try {
        const data = await dc.fetchEtiquetasDia(fecha);
        const pedidos = data.pedidos || [];
        _lastPedidos = pedidos;
        let totalPend = 0, totalImp = 0, totalEnc = 0;
        pedidos.forEach(p => {
            totalPend += p.resumen.pendiente || 0;
            totalImp += p.resumen.impresa || 0;
            totalEnc += p.resumen.encolada || 0;
        });
        const chips = [
            ['Pendientes', totalPend, 'var(--blue)'],
            ['Impresas', totalImp, 'var(--warn)'],
            ['Encoladas', totalEnc, 'var(--lime)'],
        ].map(([k, v, c]) =>
            `<span style="border:1px solid ${c}; color:${c}; border-radius:20px; padding:4px 12px; font-size:0.8rem; font-weight:700;">${k}: ${v}</span>`
        ).join('');
        document.getElementById('etiquetasDiaResumen').innerHTML = chips || 'Sin etiquetas';

        if (!pedidos.length) {
            body.innerHTML = '<div style="color:var(--lime); padding:30px; text-align:center;">✅ No hay etiquetas a sacar en los pedidos de esta fecha.</div>';
            return;
        }
        let html = '';
        pedidos.forEach(p => {
            const pend = p.resumen.pendiente || 0;
            const managed = p.resumen.impresa + p.resumen.encolada;
            const badge = pend === 0 ? 'Todas gestionadas ✓' : (managed > 0 ? `Parcial (${pend} pend.)` : `Nuevas (${pend})`);
            const bColor = pend === 0 ? 'var(--lime)' : (managed > 0 ? 'var(--warn)' : 'var(--blue)');
            const printLabelsBtn = `<button class="btn-sec" data-order-print-labels="${dc.jsAttr(p.pedido)}" title="Imprimir etiquetas a sacar de este pedido" style="padding:2px 8px; font-size:0.75rem; background:var(--primary); color:var(--on-primary); cursor:pointer;">🖨️ Imprimir Etiquetas</button>`;
            html += `<div class="card" style="border-color:${bColor}; margin-bottom:12px;">
                <div class="card-header">
                    <div>
                        <div class="order-id">Pedido #${p.pedido} <small style="color:var(--mut)">(${dc.escHtml(p.cliente)})</small></div>
                        <div class="client-name">📍 ${dc.escHtml(p.finca || 'SIN FINCA')}</div>
                    </div>
                    <div style="display:flex; align-items:center; gap:8px;">
                        ${printLabelsBtn}
                        <span class="badge" style="border:1px solid ${bColor}; color:${bColor};">${badge}</span>
                    </div>
                </div>
                <table>
                    <thead><tr><th>Referencia</th><th>Litraje</th><th>Sector</th><th>Cantidad</th><th>Motivo</th><th>Estado</th></tr></thead><tbody>`;
            (p.etiquetas || []).forEach(e => {
                const btns = ['pendiente', 'impresa', 'encolada'].map(st =>
                    `<button data-estado="${st}" data-pedido="${dc.jsAttr(p.pedido)}" data-ref="${dc.jsAttr(e.referencia)}" data-litraje="${dc.jsAttr(e.litraje || '')}" data-sector="${dc.jsAttr(e.sector || '')}"
                       style="padding:2px 7px; font-size:0.68rem; border-radius:6px; border:1px solid var(--line);
                        background:${e.estado === st ? 'var(--primary)' : 'var(--card2)'}; color:${e.estado === st ? 'var(--on-primary)' : 'var(--txt)'};">${st}</button>`
                ).join(' ');
                html += `<tr>
                    <td style="color:var(--blue); font-weight:700;">${dc.escHtml(e.referencia)}</td>
                    <td>${dc.escHtml(e.litraje || '—')}</td>
                    <td>${dc.escHtml(e.sector || '—')}</td>
                    <td style="font-weight:700;">${e.cantidad}</td>
                    <td style="font-size:0.75rem; color:var(--mut);">${dc.escHtml(e.motivo)}</td>
                    <td style="white-space:nowrap;">${btns}</td>
                </tr>`;
            });
            html += '</tbody></table></div>';
        });
        body.innerHTML = html;
    } catch (e) {
        console.error(e);
        body.innerHTML = '<div style="color:var(--bad); text-align:center; padding:20px;">Error al cargar etiquetas.</div>';
    }
}