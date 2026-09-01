/**
 * inventario/partes.js — Submenú "Inventarios".
 *
 * Tabla con filtros por columna (estilo Excel): un textbox debajo de cada
 * header que filtra en tiempo real sobre los datos cargados. Sin filtros
 * superiores ni chips de finca. Todo el filtrado es client-side.
 */
let dc = null;
let partesCache = [];
let columnFilters = { fecha: '', finca: '', sectores: '', empleado: '', duracion: '', plantas: '', escaneos: '' };

function el(id) { return document.getElementById(id); }

async function cargarPartes() {
    try {
        partesCache = await dc.fetchPartesInventario({});
        renderTabla();
    } catch (e) {
        console.error(e);
        const tb = document.querySelector('#tbPartes tbody');
        if (tb) tb.innerHTML = '<tr><td class="vacio" colspan="8">Error al cargar los partes.</td></tr>';
    }
}

function jsStr(s) {
    return String(s == null ? '' : s).replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/"/g, '&quot;').replace(/\r?\n/g, ' ');
}

function textoFila(p) {
    const secText = (p.sectoresDesc && p.sectoresDesc.length) ? p.sectoresDesc.join(', ') : (p.sector || p.sectores || []).join(', ');
    return [
        dc.fmtFechaInv(p.fecha),
        p.finca,
        secText,
        p.empleado,
        dc.fmtDuracion(p.duracion_seg),
        String(p.total_plantas),
        String(p.total_escaneos)
    ];
}

function aplicarFiltros() {
    let lista = partesCache;
    const keys = ['fecha', 'finca', 'sectores', 'empleado', 'duracion', 'plantas', 'escaneos'];
    for (const k of keys) {
        const val = columnFilters[k].toLowerCase().trim();
        if (!val) continue;
        lista = lista.filter(p => {
            const campos = textoFila(p);
            const idx = keys.indexOf(k);
            return campos[idx].toLowerCase().includes(val);
        });
    }
    return lista;
}

function renderTabla() {
    const lista = aplicarFiltros();
    const tb = document.querySelector('#tbPartes tbody');
    if (!tb) return;
    tb.innerHTML = lista.map(p => {
        const secText = (p.sectoresDesc && p.sectoresDesc.length) ? p.sectoresDesc.join(', ') : (p.sector || p.sectores || []).join(', ');
        const sectorParam = p.sector || (p.sectores && p.sectores.length ? p.sectores[0] : '');
        const onVer = `verParte('${jsStr(p.finca)}','${jsStr(p.fecha)}','${jsStr(sectorParam || '')}')`;
        return `<tr>
            <td><b>${dc.escHtml(dc.fmtFechaInv(p.fecha))}</b></td>
            <td>${dc.escHtml(p.finca)}</td>
            <td>${dc.escHtml(secText)}</td>
            <td>${dc.escHtml(p.empleado)}</td>
            <td>${dc.escHtml(dc.fmtDuracion(p.duracion_seg))}</td>
            <td>${dc.fmtNum(p.total_plantas)}</td>
            <td>${p.total_escaneos}</td>
            <td><button style="padding:3px 10px; font-size:11px;" onclick="${onVer}">Ver parte</button></td>
        </tr>`;
    }).join('') || '<tr><td class="vacio" colspan="8">Sin datos para este ámbito.</td></tr>';

    const total = partesCache.length;
    const visibles = lista.length;
    const counter = el('partesCounter');
    if (counter) counter.textContent = visibles === total ? `${total} partes` : `${visibles} de ${total}`;
}

function verParte(finca, fecha, sector) {
    const detail = { finca, fecha, sector: sector || '', abrir: 'resumen-sector' };
    localStorage.setItem('pickingve_inventario_ctx', JSON.stringify(detail));
    const nav = document.querySelector('#portal-sidebar .nav-item[data-key="inventario/resumen-sector"]');
    if (nav) nav.click();
}

function syncFilter(col, val) {
    columnFilters[col] = val;
    renderTabla();
}

export async function renderPartes(root, dataConnector) {
    if (!root || !dataConnector) return;
    dc = dataConnector;
    columnFilters = { fecha: '', finca: '', sectores: '', empleado: '', duracion: '', plantas: '', escaneos: '' };

    root.innerHTML = `
        <div class="inventario-section">
            <div class="table-scroll">
                <table id="tbPartes">
                    <thead>
                        <tr>
                            <th>Fecha</th><th>Finca</th><th>Sector(es)</th><th>Empleado</th><th>Duración</th><th>Plantas</th><th>Escaneos</th><th>Acción</th>
                        </tr>
                        <tr class="filter-row">
                            <td><input type="text" id="fFecha" placeholder="Filtrar…" data-col="fecha"></td>
                            <td><input type="text" id="fFinca" placeholder="Filtrar…" data-col="finca"></td>
                            <td><input type="text" id="fSectores" placeholder="Filtrar…" data-col="sectores"></td>
                            <td><input type="text" id="fEmpleado" placeholder="Filtrar…" data-col="empleado"></td>
                            <td><input type="text" id="fDuracion" placeholder="Filtrar…" data-col="duracion"></td>
                            <td><input type="text" id="fPlantas" placeholder="Filtrar…" data-col="plantas"></td>
                            <td><input type="text" id="fEscaneos" placeholder="Filtrar…" data-col="escaneos"></td>
                            <td><span id="partesCounter" class="text-muted"></span></td>
                        </tr>
                    </thead>
                    <tbody><tr><td class="vacio" colspan="8">Cargando inventarios…</td></tr></tbody>
                </table>
            </div>
        </div>`;

    root.querySelectorAll('.filter-row input[type=text]').forEach(inp => {
        inp.addEventListener('input', () => syncFilter(inp.dataset.col, inp.value));
    });

    window.verParte = verParte;
    await cargarPartes();
}
