/**
 * printModal.js — Modal de configuración de impresión directa.
 *
 * Abre un modal con:
 *   1. Selector de impresora destino (lógico; hoy = impresora del navegador).
 *   2. Selector de plantilla con el catálogo COMPLETO de /api/etiquetas/plantillas
 *      incluyendo las plantillas [Sistema] (p.ej. 9992 - Etiqueta Gran).
 *   3. Preview VISUAL REAL de la cabecera y del primer item, compilado con el
 *      renderizador de plantillas (printTemplateRenderer): capas, fuentes,
 *      posiciones, pasaportes y códigos de barras EAN-13 precargados.
 *   4. Info de maquetación de bobina (nº etiquetas por fila, ancho bobina, alto fila).
 *
 * Al confirmar, maqueta la bobina física (2 etiquetas por fila, @page size en mm,
 * break-after por fila) y la envía al motor de impresión masiva.
 */
import { buildPedidoBatch, flattenBatch, obtenerPlantillaFresca } from '/logistica/js/services/printBatchBuilder.js?k=logistica-2026';
import {
    obtenerPlantillaSistema9992,
    renderEtiqueta,
    renderEtiquetaCabeceraPlain,
    imprimirBobina,
    construirBobina,
    inyectarEstilosImpresion,
    normalizarBobina,
    anchoBobinaMm,
    altoFilaMm,
    escalaPreview,
    esc,
} from '/logistica/js/services/printTemplateRenderer.js?k=logistica-2026';

const MODAL_ID = 'print-config-modal';
const SCALE = 3.78;

/** Catálogo completo: catálogo remoto de la API (fuente de verdad, D-273) +
 *  plantilla de sistema 9992 solo como fallback si la BD no la contiene. */
async function cargarCatalogo(dc) {
    let remote = [];
    try {
        remote = (await dc.fetchPlantillas()) || [];
    } catch (e) {
        console.warn('No se pudo cargar el catálogo de plantillas:', e);
    }
    const sistema = obtenerPlantillaSistema9992();
    const mapa = new Map([[sistema.id, sistema]]);
    remote.forEach(t => mapa.set(t.id, t));
    return Array.from(mapa.values());
}

/** Carga JsBarcode una sola vez (necesario para EAN-13 en preview e impresión). */
function asegurarJsBarcode() {
    if (typeof JsBarcode !== 'undefined' || document.getElementById('jsbarcode-print-script')) return;
    const s = document.createElement('script');
    s.id = 'jsbarcode-print-script';
    s.src = 'https://cdn.jsdelivr.net/npm/jsbarcode@3.11.5/dist/JsBarcode.all.min.js';
    document.head.appendChild(s);
}

/**
 * Abre el modal de impresión para un pedido.
 *
 * @param {Object} pedido - Objeto del pedido (tal cual de etiquetas/dia).
 * @param {Object} dc - dataConnector (para fetchPlantillas).
 */
export async function openPrintModal(pedido, dc) {
    destroyModal();
    asegurarJsBarcode();

    const batch = buildPedidoBatch(pedido);
    const cola = flattenBatch(batch);
    const catalogo = await cargarCatalogo(dc);

    const modal = document.createElement('div');
    modal.id = MODAL_ID;
    modal.className = 'modal';
    modal.classList.add('open');
    modal.innerHTML = buildModalHtml(pedido, batch, cola, catalogo);
    document.body.appendChild(modal);

    // Cerrar al hacer clic en el fondo
    modal.addEventListener('click', e => { if (e.target === modal) destroyModal(); });
    modal.querySelectorAll('[data-close]').forEach(btn => btn.addEventListener('click', destroyModal));
    document.addEventListener('keydown', handleEsc);

    // Selector de plantilla: al cambiar, re-renderizar el preview visual real.
    const selPlantilla = modal.querySelector('#print-plantilla-select');
    selPlantilla.addEventListener('change', () => {
        const plantilla = catalogo.find(t => t.id === selPlantilla.value);
        renderPreviewVisual(modal, plantilla, batch);
        renderInfoBobina(modal, plantilla);
    });

    renderPreviewVisual(modal, catalogo[0], batch);
    renderInfoBobina(modal, catalogo[0]);

    modal.querySelector('#print-confirm-btn')?.addEventListener('click', async () => {
        const plantillaId = selPlantilla.value;
        // D-273: refrescar SIEMPRE la plantilla desde la base de datos antes de
        // imprimir, para no usar una copia obsoleta en memoria.
        const plantilla = await obtenerPlantillaFresca(dc, plantillaId);
        ejecutarImpresion(cola, plantilla, modal);
    });
}

function handleEsc(e) {
    if (e.key === 'Escape') destroyModal();
}

function destroyModal() {
    document.removeEventListener('keydown', handleEsc);
    const m = document.getElementById(MODAL_ID);
    if (m) m.remove();
}

// ────────────────────────────────────────────────────────────
// HTML del modal
// ────────────────────────────────────────────────────────────

function buildModalHtml(pedido, batch, cola, catalogo) {
    const totalItems = batch.items.length;
    const totalEtiquetas = cola.length;

    const opcionesPlantilla = catalogo.map(t =>
        `<option value="${esc(t.id)}">${t.es_sistema ? '[Sistema] ' : ''}${esc(t.nombre)}</option>`
    ).join('');

    return `
        <div class="modal-content" style="max-width:760px;">
            <div class="modal-header">
                <h2>🖨️ Imprimir Etiquetas — Pedido #${esc(pedido.pedido)}</h2>
                <button class="close-btn" data-close>✕</button>
            </div>

            <!-- Configuración -->
            <div style="display:grid; grid-template-columns:1fr 1fr; gap:12px; margin-bottom:14px;">
                <div>
                    <label style="font-size:0.78rem; color:var(--mut); display:block; margin-bottom:4px;">Impresora destino</label>
                    <select id="print-destino-select" style="width:100%; background:var(--card2); color:var(--txt); border:1px solid var(--line); border-radius:8px; padding:8px 10px; font-size:0.85rem;">
                        <option value="browser">🖨️ Impresora del navegador</option>
                        <option value="pdf">📄 Guardar como PDF</option>
                    </select>
                </div>
                <div>
                    <label style="font-size:0.78rem; color:var(--mut); display:block; margin-bottom:4px;">Plantilla de etiqueta</label>
                    <select id="print-plantilla-select" style="width:100%; background:var(--card2); color:var(--txt); border:1px solid var(--line); border-radius:8px; padding:8px 10px; font-size:0.85rem;">
                        ${opcionesPlantilla}
                    </select>
                </div>
            </div>

            <!-- Info de bobina -->
            <div id="print-bobina-info" style="font-size:0.75rem; color:var(--mut); margin-bottom:12px;"></div>

            <!-- Preview visual real -->
            <div id="print-preview-visual" style="margin-bottom:14px;">
                <div style="font-size:0.72rem; text-transform:uppercase; color:var(--mut); font-weight:700; margin-bottom:6px;">👁️ Vista previa con la plantilla seleccionada</div>
            </div>

            <!-- Resumen del lote -->
            <div style="margin-bottom:14px;">
                <div style="font-size:0.72rem; text-transform:uppercase; color:var(--mut); font-weight:700; margin-bottom:6px;">
                    📦 ${totalItems} referencia(s) · ${totalEtiquetas} etiqueta(s) en total (cabecera + items)
                </div>
                <div style="max-height:160px; overflow-y:auto; border:1px solid var(--line); border-radius:8px;">
                    <table style="width:100%; border-collapse:collapse; font-size:0.78rem;">
                        <thead>
                            <tr style="background:var(--card2);">
                                <th style="padding:5px 8px; text-align:left; color:var(--mut); font-size:0.68rem;">#</th>
                                <th style="padding:5px 8px; text-align:left; color:var(--mut); font-size:0.68rem;">Tipo</th>
                                <th style="padding:5px 8px; text-align:left; color:var(--mut); font-size:0.68rem;">Contenido</th>
                                <th style="padding:5px 8px; text-align:center; color:var(--mut); font-size:0.68rem;">Copias</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr style="border-top:1px solid var(--line);">
                                <td style="padding:4px 8px; color:var(--mut);">1</td>
                                <td style="padding:4px 8px; color:var(--primary); font-weight:700;">Cabecera</td>
                                <td style="padding:4px 8px;">${esc(batch.cabecera.titulo)}</td>
                                <td style="padding:4px 8px; text-align:center; font-weight:700;">1</td>
                            </tr>
                            ${batch.items.map((it, i) => `
                                <tr style="border-top:1px solid var(--line);">
                                    <td style="padding:4px 8px; color:var(--mut);">${i + 2}</td>
                                    <td style="padding:4px 8px;">Item</td>
                                    <td style="padding:4px 8px; color:var(--blue); font-weight:700;">${esc(it.titulo)}</td>
                                    <td style="padding:4px 8px; text-align:center; font-weight:700;">${it.cantidad}</td>
                                </tr>
                            `).join('')}
                        </tbody>
                    </table>
                </div>
            </div>

            <!-- Acciones -->
            <div style="display:flex; gap:10px; justify-content:flex-end; border-top:1px solid var(--line); padding-top:12px;">
                <button class="btn-sec" data-close>Cancelar</button>
                <button id="print-confirm-btn" style="background:var(--primary); color:var(--on-primary); padding:8px 22px; border-radius:8px; font-weight:700; font-size:0.88rem; cursor:pointer;">
                    🖨️ Confirmar e Imprimir (${totalEtiquetas})
                </button>
            </div>
        </div>
    `;
}

// ────────────────────────────────────────────────────────────
// Preview visual real
// ────────────────────────────────────────────────────────────

function renderPreviewVisual(modal, plantilla, batch) {
    const container = modal.querySelector('#print-preview-visual');
    if (!container || !plantilla) return;
    const b = normalizarBobina(plantilla);
    const s = escalaPreview(b.ancho);
    const thumbW = Math.round(b.ancho * SCALE);
    const thumbH = Math.round(b.alto * SCALE);
    const wrapH = Math.round(thumbH * s);

    const miniaturaCabecera = () => `
        <div style="flex:1; min-width:0;">
            <div style="font-size:0.7rem; color:var(--mut); margin-bottom:4px;">Cabecera del lote (sin plantilla)</div>
            <div style="height:${wrapH + 8}px; overflow:hidden;">
                <div style="width:${thumbW}px; height:${thumbH}px; transform:scale(${s}); transform-origin:top left; border:1px solid var(--line); background:#fff; position:relative; box-shadow:0 2px 8px rgba(0,0,0,0.12);">
                    ${renderEtiquetaCabeceraPlain(b.ancho, b.alto, batch.cabecera)}
                </div>
            </div>
        </div>`;

    const miniaturaItem = (titulo, itemData) => `
        <div style="flex:1; min-width:0;">
            <div style="font-size:0.7rem; color:var(--mut); margin-bottom:4px;">${titulo}</div>
            <div style="height:${wrapH + 8}px; overflow:hidden;">
                <div style="width:${thumbW}px; height:${thumbH}px; transform:scale(${s}); transform-origin:top left; border:1px solid var(--line); background:#fff; position:relative; box-shadow:0 2px 8px rgba(0,0,0,0.12);">
                    ${renderEtiqueta(plantilla, itemData.variables || itemData)}
                </div>
            </div>
        </div>`;

    container.innerHTML = `
        <div style="display:flex; gap:14px; align-items:flex-start; flex-wrap:wrap;">
            ${miniaturaCabecera()}
            ${batch.items[0] ? miniaturaItem('Item (1ª referencia)', batch.items[0]) : ''}
        </div>
        ${plantilla.elementos_json && plantilla.elementos_json.length === 0
            ? '<div style="font-size:0.75rem; color:var(--warn); margin-top:6px;">La plantilla no define elementos: la etiqueta saldrá en blanco.</div>'
            : ''}
    `;
}

function renderInfoBobina(modal, plantilla) {
    const info = modal.querySelector('#print-bobina-info');
    if (!info || !plantilla) return;
    const b = normalizarBobina(plantilla);
    info.innerHTML = `🧾 <b>Bobina</b>: ${b.cols} etiqueta(s) por fila · etiqueta ${b.ancho}×${b.alto} mm · ancho bobina <b>${anchoBobinaMm(plantilla).toFixed(2)} mm</b> · alto fila <b>${altoFilaMm(plantilla).toFixed(2)} mm</b> · separación ${b.gap_h} mm`;
}

// ────────────────────────────────────────────────────────────
// Ejecutar impresión
// ────────────────────────────────────────────────────────────

function ejecutarImpresion(cola, plantilla, modal) {
    const container = document.getElementById('print-batch-container');
    if (!container) {
        alert('No se encontró el contenedor de impresión.');
        return;
    }
    destroyModal();
    // Motor de impresión masiva: maqueta bobina física + @page size + window.print().
    imprimirBobina(cola, plantilla, container);
}