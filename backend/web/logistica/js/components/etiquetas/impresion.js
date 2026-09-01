/**
 * etiquetas/impresion.js — Submenú "Impresión masiva de etiquetas".
 *
 * Funcionalidad real de impresión por lote: origen (Picking / Inventario),
 * selector de plantilla (catálogo completo incl. [Sistema]), generación de lote
 * desde API, cola de impresión, añadido manual y salida a impresora.
 *
 * El renderizado NO es texto plano: usa el motor printTemplateRenderer.js que
 * compila el HTML/SVG visual de cada plantilla (capas, fuentes, posiciones,
 * pasaportes y EAN-13) y maqueta la bobina física (2 etiquetas por fila,
 * @page size en mm, break-after por fila, sin márgenes del navegador).
 */
import {
    obtenerPlantillaSistema9992,
    imprimirBobina,
    normalizarBobina,
    anchoBobinaMm,
    altoFilaMm,
    esc,
} from '/logistica/js/services/printTemplateRenderer.js?k=logistica-2026';
import { obtenerPlantillaFresca } from '/logistica/js/services/printBatchBuilder.js?k=logistica-2026';

let dc = null;
let queue = [];
let plantillas = [];
let _rootForQuery = null;

export async function renderImpresion(root, dataConnector) {
    if (!root || !dataConnector) return;
    dc = dataConnector;
    _rootForQuery = root;
    queue = [];

    root.innerHTML = `
        <div class="impresion-section">
            <div class="filters">
                <select id="etiqueta-origen"><option value="PICKING">Pedido de Picking</option><option value="INVENTARIO">Lote de Inventario</option></select>
                <input id="etiqueta-input-id" placeholder="Número de pedido o finca">
                <span id="etiqueta-formato"></span>
                <button id="load-label-batch">Cargar lote</button>
                <button class="btn-sec" id="add-label-manual">Añadir manual</button>
            </div>
            <h4>Cola de impresión (<span id="cola-count">0</span>)</h4>
            <div id="cola-impresion-list"><p class="text-muted">La cola está vacía.</p></div>
            <div id="impresion-bobina-info" style="font-size:0.75rem; color:var(--mut); margin:6px 0;"></div>
            <div style="display:flex; gap:8px; margin-top:14px;">
                <button id="print-label-batch">🖨️ Imprimir lote</button>
                <button class="btn-sec" id="clear-label-batch">Limpiar</button>
            </div>
        </div>
        <div id="print-batch-container"></div>
    `;

    await renderSelectorPlantilla(root.querySelector('#etiqueta-formato'));
    root.querySelector('#load-label-batch').addEventListener('click', loadBatch);
    root.querySelector('#add-label-manual').addEventListener('click', () => { queue.push({ tipo: 'manual', titulo: 'Etiqueta manual', variables: { TEXTO_LIBRE: 'Etiqueta manual' } }); renderQueue(); });
    root.querySelector('#clear-label-batch').addEventListener('click', () => { queue = []; renderQueue(); });
    root.querySelector('#print-label-batch').addEventListener('click', printBatch);

    const pendingOrder = sessionStorage.getItem('pickingve_print_order');
    if (pendingOrder) {
        sessionStorage.removeItem('pickingve_print_order');
        rootElQuery('#etiqueta-origen').value = 'PICKING';
        rootElQuery('#etiqueta-input-id').value = pendingOrder;
        const sel = rootElQuery('#etiqueta-formato-select');
        if (sel && sel.options.length > 1) {
            sel.selectedIndex = 1;
        }
        setTimeout(() => {
            loadBatch();
        }, 250);
    }
}

async function renderSelectorPlantilla(container) {
    container.innerHTML = '<select id="etiqueta-formato-select"><option value="">Cargando plantillas...</option></select>';
    try {
        const remote = (await dc.fetchPlantillas()) || [];
        const sistema = obtenerPlantillaSistema9992();
        const mapa = new Map([[sistema.id, sistema]]);
        remote.forEach(t => mapa.set(t.id, t));
        plantillas = Array.from(mapa.values());
        const sel = container.querySelector('#etiqueta-formato-select');
        sel.innerHTML = '<option value="">— Selecciona plantilla —</option>' +
            plantillas.map(t => `<option value="${esc(t.id)}">${t.es_sistema ? '[Sistema] ' : ''}${esc(t.nombre)}</option>`).join('');
        if (plantillas.length) sel.selectedIndex = 1;
        renderBobinaInfo();
    } catch (e) {
        container.innerHTML = '<span class="text-muted">No se pudieron cargar plantillas.</span>';
    }
}

function renderBobinaInfo() {
    const info = rootElQuery('#impresion-bobina-info');
    if (!info) return;
    const plantilla = plantillaSeleccionada();
    if (!plantilla) { info.textContent = ''; return; }
    const b = normalizarBobina(plantilla);
    info.textContent = `Bobina: ${b.cols} etiqueta(s) por fila · ${b.ancho}×${b.alto} mm · ancho ${anchoBobinaMm(plantilla).toFixed(2)} mm · alto fila ${altoFilaMm(plantilla).toFixed(2)} mm · separación ${b.gap_h} mm`;
}

function plantillaSeleccionada() {
    const sel = rootElQuery('#etiqueta-formato-select');
    if (!sel || !sel.value) return null;
    return plantillas.find(t => t.id === sel.value) || null;
}

async function loadBatch() {
    const origin = rootElQuery('#etiqueta-origen').value;
    const id = rootElQuery('#etiqueta-input-id').value.trim();
    const sel = rootElQuery('#etiqueta-formato-select');
    if (!id) { alert('Indica el número de pedido o finca.'); return; }
    if (sel && !sel.value && sel.options.length > 1) {
        sel.selectedIndex = 1;
    }
    const plantillaId = sel ? sel.value : '';
    if (!plantillaId) { alert('Selecciona una plantilla de etiquetas.'); return; }
    try {
        const data = await dc.generarLote({ origen: origin, informe_id: id, plantilla_id: plantillaId });
        const lote = data.lote || [];
        queue.push(...lote.flatMap(item => Array.from({ length: item.cantidad_copias || 1 }, () => item)));
        renderQueue();
    } catch (e) {
        console.error(e);
        alert('Error al cargar el lote.');
    }
}

function rootElQuery(selector) {
    return _rootForQuery ? _rootForQuery.querySelector(selector) : document.querySelector(selector);
}

function renderQueue() {
    rootElQuery('#cola-count').textContent = queue.length;
    rootElQuery('#cola-impresion-list').innerHTML = queue.length
        ? `<table><tbody>${queue.map((item, index) => `<tr><td>${index + 1}</td><td>${esc(item.titulo || item.tipo || 'Etiqueta')}</td><td style="font-size:0.75rem; color:var(--mut);">${esc(item.variables?.CODIGO_EAN13_BARRAS || item.variables?.TEXTO_LIBRE || '')}</td></tr>`).join('')}</tbody></table>`
        : '<p class="text-muted">La cola está vacía.</p>';
}

async function printBatch() {
    if (!queue.length) return;
    const plantillaId = plantillaSeleccionada()?.id;
    if (!plantillaId) { alert('Selecciona una plantilla de etiquetas.'); return; }
    if (typeof JsBarcode === 'undefined' && !document.getElementById('jsbarcode-print-script')) {
        const s = document.createElement('script');
        s.id = 'jsbarcode-print-script';
        s.src = 'https://cdn.jsdelivr.net/npm/jsbarcode@3.11.5/dist/JsBarcode.all.min.js';
        document.head.appendChild(s);
    }
    // D-273: refrescar SIEMPRE la plantilla desde la base de datos antes de
    // imprimir, para no usar una copia obsoleta en memoria.
    let plantilla;
    try {
        plantilla = await obtenerPlantillaFresca(dc, plantillaId);
    } catch (e) {
        plantilla = plantillaSeleccionada();
    }
    if (!plantilla) { alert('Selecciona una plantilla de etiquetas.'); return; }
    const container = rootElQuery('#print-batch-container') || document.getElementById('print-batch-container');
    if (!container) return;
    // Normalizar cola: cada etiqueta debe exponer `variables` para el renderer.
    const colaNormalizada = queue.map(item => ({ ...item, variables: item.variables || item }));
    imprimirBobina(colaNormalizada, plantilla, container);
}

// Exponer raíz para re-uso interno.
export function _setImpresionRoot(root) { _rootForQuery = root; }