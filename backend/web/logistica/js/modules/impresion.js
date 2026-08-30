import { apiService } from '/logistica/js/services/apiService.js?k=logistica-2026';
import { renderLabelSelector } from '/logistica/js/components/labelSelector.js?k=logistica-2026';

let queue = [];

export function initImpresion() {
    const root = document.getElementById('printing-module');
    if (!root) return;
    root.innerHTML = `<div class="filters"><select id="etiqueta-origen"><option value="PICKING">Pedido de Picking</option><option value="INVENTARIO">Lote de Inventario</option></select><input id="etiqueta-input-id" placeholder="Número de pedido o finca"><span id="etiqueta-formato"></span><button id="load-label-batch">Cargar lote</button><button id="add-label-manual">Añadir manual</button></div><h4>Cola de impresión (<span id="cola-count">0</span>)</h4><div id="cola-impresion-list"><p class="text-muted">La cola está vacía.</p></div><button id="print-label-batch">Imprimir lote</button><button class="btn-secondary" id="clear-label-batch">Limpiar</button>`;
    renderLabelSelector(document.getElementById('etiqueta-formato'), apiService);
    document.getElementById('load-label-batch').addEventListener('click', loadBatch);
    document.getElementById('add-label-manual').addEventListener('click', () => { queue.push({tipo:'manual', titulo:'Etiqueta manual'}); renderQueue(); });
    document.getElementById('clear-label-batch').addEventListener('click', () => { queue = []; renderQueue(); });
    document.getElementById('print-label-batch').addEventListener('click', printBatch);
}

async function loadBatch() {
    const origin = document.getElementById('etiqueta-origen').value;
    const id = document.getElementById('etiqueta-input-id').value.trim();
    if (!id) return;
    const data = await apiService.generateLabelBatch({origen: origin, informe_id: id, plantilla_id: document.getElementById('etiqueta-formato-select').value});
    queue.push(...(data.lote || []).flatMap(item => Array.from({length: item.cantidad_copias || 1}, () => item)));
    renderQueue();
}

function renderQueue() {
    document.getElementById('cola-count').textContent = queue.length;
    document.getElementById('cola-impresion-list').innerHTML = queue.length ? `<table><tbody>${queue.map((item, index) => `<tr><td>${index + 1}</td><td>${item.titulo || item.tipo}</td></tr>`).join('')}</tbody></table>` : '<p class="text-muted">La cola está vacía.</p>';
}

function printBatch() {
    if (!queue.length) return;
    document.getElementById('print-batch-container').innerHTML = queue.map(item => `<div class="label-sheet-item"><strong>VIVEROS ELCHE</strong><br>${item.titulo || item.tipo}<br>${item.variables?.CODIGO_EAN13_BARRAS || ''}</div>`).join('');
    window.print();
}

window.prepareOrderLabels = orderId => { document.querySelector('[data-tab="etiquetas"]')?.click(); document.getElementById('etiqueta-origen').value = 'PICKING'; document.getElementById('etiqueta-input-id').value = orderId; return loadBatch(); };
