/**
 * inventario/inventarios.js — Submenú "Inventarios" (Gestión de Pistoleos).
 *
 * Carga todos los pistoleos sin filtros. Muestra una tabla agrupada
 * (Finca|Sector|Fecha|Empleado|Registros|Duración) con filtros por columna
 * estilo Excel. El despliegue de registros GPS se mantiene en acordeón.
 */
let dc = null;
let gruposCache = [];

function el(id) { return document.getElementById(id); }

let columnFilters = { finca: '', sector: '', fecha: '', empleado: '', registros: '', duracion: '' };

function textoGrupo(g) {
    return [g.finca, g.sector, g.fecha, g.empleado, String(g.items.length), g.dur || ''];
}

function aplicarFiltros() {
    let lista = gruposCache;
    const keys = Object.keys(columnFilters);
    for (const k of keys) {
        const val = columnFilters[k].toLowerCase().trim();
        if (!val) continue;
        lista = lista.filter(g => {
            const campos = textoGrupo(g);
            const idx = keys.indexOf(k);
            return campos[idx].toLowerCase().includes(val);
        });
    }
    return lista;
}

async function cargarPistoleos() {
    try {
        const lista = await dc.fetchPistoleos({});
        const fincasData = await dc.fetchInventarioFincas();
        const grupos = {};
        lista.forEach(p => {
            const fechaDia = (p.fechaHora || '').slice(0, 10);
            const key = `${p.finca}|${p.sector}|${fechaDia}|${p.empleado}`;
            if (!grupos[key]) grupos[key] = { finca: p.finca, sector: p.sector, fecha: fechaDia, empleado: p.empleado, items: [] };
            grupos[key].items.push(p);
        });
        Object.values(grupos).forEach(g => {
            const fechas = g.items.map(i => new Date(i.fechaHora.replace(' ', 'T')).getTime()).filter(t => !isNaN(t));
            if (fechas.length > 1) {
                const ms = Math.max(...fechas) - Math.min(...fechas);
                g.dur = dc.fmtDuracion(Math.round(ms / 1000));
            } else {
                g.dur = '0 s';
            }
        });
        gruposCache = Object.values(grupos);
        renderTabla(fincasData);
    } catch (e) {
        console.error(e);
        const tb = document.querySelector('#tbInventarios tbody');
        if (tb) tb.innerHTML = '<tr><td class="vacio" colspan="7">Error al cargar los pistoleos.</td></tr>';
    }
}

function renderTabla(fincasData) {
    const lista = aplicarFiltros();
    const tb = document.querySelector('#tbInventarios tbody');
    if (!tb) return;
    tb.innerHTML = lista.map((g, idx) => {
        return `<tr>
            <td><b>${dc.escHtml(g.finca)}</b></td>
            <td>${dc.escHtml(g.sector)}</td>
            <td>${dc.escHtml(dc.fmtFechaInv(g.fecha))}</td>
            <td>${dc.escHtml(g.empleado)}</td>
            <td>${g.items.length}</td>
            <td>${dc.escHtml(g.dur)}</td>
            <td><button style="padding:3px 10px; font-size:11px;" onclick="toggleAcordeonInv('acord-inv-${idx}')">Ver</button></td>
        </tr>` + `<tr id="acord-inv-${idx}" style="display:none;"><td colspan="7" style="padding:0;">
            <div style="padding:10px; background:var(--card2);">
                <div style="margin-bottom:8px;"><button style="padding:3px 10px; font-size:11px;" onclick="seleccionarGrupoInv('acord-inv-${idx}', true)">Seleccionar todos</button> <button class="secundario" style="padding:3px 10px; font-size:11px;" onclick="seleccionarGrupoInv('acord-inv-${idx}', false)">Deseleccionar</button></div>
                <div class="table-scroll" style="max-height:280px;"><table><thead><tr><th style="width:30px;">#</th><th>Hora</th><th>Ref.</th><th>Planta</th><th>Litraje</th><th>Cant.</th><th>GPS</th></tr></thead><tbody>` +
                g.items.map((item, i) => {
                    const gps = (item.latitud != null && item.longitud != null) ? (item.latitud.toFixed(5) + ', ' + item.longitud.toFixed(5)) : 'Sin GPS';
                    return `<tr><td><input type="checkbox" class="chkPistoleo" value="${dc.escHtml(item.recordId)}"></td><td>${dc.escHtml((item.fechaHora || '').slice(11, 19))}</td><td><b>${dc.escHtml(item.ref)}</b></td><td style="white-space:normal">${dc.escHtml(item.nombre)}</td><td>${dc.escHtml(item.litraje || '—')}</td><td>${dc.fmtNum(item.cantidad)}</td><td style="font-family:monospace; font-size:11px;">${gps}</td></tr>`;
                }).join('') + `</tbody></table></div></div></td></tr>`;
    }).join('') || '<tr><td class="vacio" colspan="7">Sin pistoleos registrados.</td></tr>';

    const total = gruposCache.length;
    const visibles = lista.length;
    const counter = el('invCounter');
    if (counter) counter.textContent = visibles === total ? `${total} grupos` : `${visibles} de ${total}`;
}

window.toggleAcordeonInv = function (id) { const elm = document.getElementById(id); if (elm) elm.style.display = elm.style.display === 'none' ? 'block' : 'none'; };
window.seleccionarGrupoInv = function (id, marcar) { const c = document.getElementById(id); if (c) c.querySelectorAll('.chkPistoleo').forEach(chk => { chk.checked = marcar; }); };

async function eliminarSeleccionados() {
    const checks = document.querySelectorAll('.chkPistoleo:checked');
    if (!checks.length) { alert('Selecciona al menos un registro para eliminar.'); return; }
    if (!confirm(`¿Descartar / eliminar lógicamente los ${checks.length} registros seleccionados?`)) return;
    const ids = [];
    checks.forEach(chk => { ids.push(chk.value); });
    try {
        const r = await dc.eliminarPistoleos({ record_ids: ids });
        alert(`Se han descartado ${r.ok} registros.`);
        await cargarPistoleos();
    } catch (e) { alert('Error al eliminar pistoleos: ' + e.message); }
}

function syncFilter(col, val) {
    columnFilters[col] = val;
    renderTabla();
}

export async function renderInventarios(root, dataConnector) {
    if (!root || !dataConnector) return;
    dc = dataConnector;
    columnFilters = { finca: '', sector: '', fecha: '', empleado: '', registros: '', duracion: '' };

    root.innerHTML = `
        <div class="inventario-section">
            <section id="sec-pistoleos" style="display:block;">
                <h2>Gestión y Borrado Selectivo de Pistoleos
                    <button style="padding:4px 12px; font-size:11px; background:var(--bad);" onclick="eliminarInvSel()">🗑️ Eliminar seleccionados</button>
                </h2>
                <div class="table-scroll" style="max-height:520px;">
                    <table id="tbInventarios">
                        <thead>
                            <tr><th>Finca</th><th>Sector</th><th>Fecha</th><th>Empleado</th><th>Registros</th><th>Duración</th><th>Acción</th></tr>
                            <tr class="filter-row">
                                <td><input type="text" id="fInvFinca" placeholder="Filtrar…" data-col="finca"></td>
                                <td><input type="text" id="fInvSector" placeholder="Filtrar…" data-col="sector"></td>
                                <td><input type="text" id="fInvFecha" placeholder="Filtrar…" data-col="fecha"></td>
                                <td><input type="text" id="fInvEmpleado" placeholder="Filtrar…" data-col="empleado"></td>
                                <td><input type="text" id="fInvRegistros" placeholder="Filtrar…" data-col="registros"></td>
                                <td><input type="text" id="fInvDuracion" placeholder="Filtrar…" data-col="duracion"></td>
                                <td><span id="invCounter" class="text-muted"></span></td>
                            </tr>
                        </thead>
                        <tbody><tr><td class="vacio" colspan="7">Cargando pistoleos…</td></tr></tbody>
                    </table>
                </div>
            </section>
        </div>`;

    root.querySelectorAll('.filter-row input[type=text]').forEach(inp => {
        inp.addEventListener('input', () => syncFilter(inp.dataset.col, inp.value));
    });

    window.eliminarInvSel = eliminarSeleccionados;
    await cargarPistoleos();
}
