/**
 * inventario/resumenReferencia.js — Submenú "Resumen por referencia".
 *
 * Réplica COMPLETA de la pestaña Referencias y Plantas de /inventario:
 * tabla de líneas con filtro por estado (Todas/Faltan/Excesos/OK), plantas
 * fuera de sector (reetiquetado) y etiquetas a sacar (conteo manual / OCR).
 */
let dc = null;
let fincasCache = [];
let datosActuales = null;
let filtroEstado = 'todas';

function el(id) { return document.getElementById(id); }

function estadoSector(sectorId) {
    const finca = el('selFinca').value;
    const f = fincasCache.find(x => x.finca === finca);
    const s = f ? (f.sectores || []).find(y => y.id === sectorId) : null;
    return s || { cerrado: false, tieneInventario: false, asignado: false };
}

function render(d) {
    datosActuales = d;
    el('contenido').style.display = '';
    pintarLineas();

    const fb = document.querySelector('#tbFuera tbody');
    fb.innerHTML = (d.fueraSector || []).map(r => {
        return `<tr><td>${dc.escHtml(dc.fmtFechaInv(r.fechaHora))}</td><td><b>${dc.escHtml(r.ref)}</b></td><td>${dc.escHtml(r.nombre)}</td>
            <td>${dc.escHtml(r.litraje || '—')}</td><td>${dc.escHtml(r.sectorEtiquetaDesc || '—')}</td>
            <td><b>${dc.escHtml(r.sectorDesc || '—')}</b></td><td>${dc.fmtNum(r.cantidad)}</td><td>${dc.escHtml(r.empleado)}</td></tr>`;
    }).join('') || '<tr><td class="vacio" colspan="8">Sin datos para este ámbito.</td></tr>';

    const eb = document.querySelector('#tbEtiquetas tbody');
    eb.innerHTML = (d.etiquetas || []).map(r => {
        const motivo = r.reetiquetar ? 'Reetiquetado' : (r.sinEan ? 'OCR sin EAN' : 'Conteo manual');
        return `<tr><td><b>${dc.escHtml(r.ref)}</b></td><td>${dc.escHtml(r.litraje || '—')}</td><td>${dc.escHtml(r.sectorEtiquetaDesc || r.sectorDesc || '—')}</td>
            <td>${motivo}</td><td>${dc.fmtNum(r.cantidad)}</td><td>${dc.escHtml(dc.fmtFechaInv(r.fechaHora))}</td></tr>`;
    }).join('') || '<tr><td class="vacio" colspan="6">Sin datos para este ámbito.</td></tr>';
}

function pintarLineas() {
    if (!datosActuales) return;
    const lineas = datosActuales.lineas || [];
    const visibles = lineas.filter(l => filtroEstado === 'todas' || l.estado === filtroEstado);
    const lb = document.querySelector('#tbLineas tbody');
    lb.innerHTML = visibles.map(l => {
        const clsFila = l.estado === 'EXCESO' ? ' class="exceso"' : l.estado === 'FALTA' ? ' class="falta"' : '';
        return `<tr${clsFila}><td>${dc.escHtml(l.sectorDesc)}</td><td><b>${dc.escHtml(l.ref)}</b></td><td style="white-space:normal">${dc.escHtml(l.nombre)}</td>
            <td>${dc.escHtml(l.litraje || '—')}</td><td>${dc.fmtNum(l.esperado)}</td><td>${dc.fmtNum(l.contado)}</td>
            <td class="${l.dif > 0 ? 'ambar' : l.dif < 0 ? 'rojo' : 'verde'}">${dc.fmtNum(l.dif)}</td><td>${dc.badgeEstadoInv(l.estado)}</td></tr>`;
    }).join('') || '<tr><td class="vacio" colspan="8">Sin datos para este ámbito.</td></tr>';
}

async function verAhora() {
    const finca = el('selFinca').value;
    if (!finca) { return; }
    const sector = el('selSector').value;
    const desde = el('inpDesde').value || dc.fechaLocalISO(new Date());
    try {
        const data = await dc.fetchInventarioDatos({ finca, sector, desde });
        render(data);
    } catch (e) {
        const elErr = el('error');
        if (elErr) { elErr.textContent = `No se pudo cargar el análisis (${e.message}).`; elErr.style.display = ''; }
    }
}

async function cargarFincas() {
    try {
        fincasCache = await dc.fetchInventarioFincas();
        const sel = el('selFinca');
        sel.innerHTML = '<option value="">— Selecciona una finca —</option>';
        fincasCache.forEach(f => {
            const op = document.createElement('option');
            op.value = f.finca;
            op.textContent = f.finca + ((f.sectores || []).length ? ` (${f.sectores.length})` : '');
            sel.appendChild(op);
        });
        sel.onchange = cargarSectores;
        cargarSectores();
    } catch (e) { /* silencioso: se muestra vacío */ }
}

function cargarSectores() {
    const finca = el('selFinca').value;
    const f = fincasCache.find(x => x.finca === finca) || { sectores: [] };
    const sel = el('selSector');
    sel.innerHTML = '<option value="">Todos</option>';
    (f.sectores || []).forEach(s => {
        const op = document.createElement('option');
        op.value = s.id;
        op.textContent = s.descripcion || s.id;
        sel.appendChild(op);
    });
}

export async function renderResumenReferencia(root, dataConnector) {
    if (!root || !dataConnector) return;
    dc = dataConnector;
    root.innerHTML = `
        <div class="inventario-section">
            <div class="controles">
                <div class="wrap">
                    <div class="campo"><label for="selFinca">Finca</label><select id="selFinca"><option value="">— Selecciona una finca —</option></select></div>
                    <div class="campo"><label for="selSector">Sector</label><select id="selSector"><option value="">Todos</option></select></div>
                    <div class="campo"><label>Conteos desde</label><input type="date" id="inpDesde"></div>
                    <button id="btnVer">Ver análisis</button>
                </div>
            </div>
            <div id="error" class="error" style="display:none"></div>
            <div id="contenido" style="display:none;">
                <section id="sec-referencias" class="activa" style="display:block;">
                    <h2>Referencias y Plantas
                        <span class="filtros-estados">
                            <span class="chip activa" data-f="todas">Todas</span>
                            <span class="chip" data-f="FALTA">Faltan</span>
                            <span class="chip" data-f="EXCESO">Excesos</span>
                            <span class="chip" data-f="OK">OK</span>
                        </span>
                    </h2>
                    <div class="table-scroll">
                        <table id="tbLineas">
                            <thead><tr><th>Sector</th><th>Ref.</th><th>Planta</th><th>Litraje</th><th>Esp.</th><th>Cont.</th><th>Dif.</th><th>Estado</th></tr></thead>
                            <tbody></tbody>
                        </table>
                    </div>
                    <div style="padding:16px;">
                        <h3 style="margin-top:0;">Plantas fuera de sector · reetiquetado posterior</h3>
                        <div class="table-scroll" style="max-height:250px; margin-bottom:14px;">
                            <table id="tbFuera">
                                <thead><tr><th>Fecha</th><th>Ref.</th><th>Planta</th><th>Litraje</th><th>Su sector</th><th>Encontrada en</th><th>Cant.</th><th>Empleado</th></tr></thead>
                                <tbody></tbody>
                            </table>
                        </div>
                        <h3>Etiquetas a sacar (conteo manual / OCR sin EAN)</h3>
                        <div class="table-scroll" style="max-height:250px;">
                            <table id="tbEtiquetas">
                                <thead><tr><th>Ref.</th><th>Litraje</th><th>Sector</th><th>Motivo</th><th>Cant.</th><th>Fecha</th></tr></thead>
                                <tbody></tbody>
                            </table>
                        </div>
                    </div>
                </section>
            </div>
        </div>`;

    el('btnVer').addEventListener('click', verAhora);
    root.querySelectorAll('.chip[data-f]').forEach(chip => chip.addEventListener('click', () => {
        root.querySelectorAll('.chip[data-f]').forEach(c2 => c2.classList.remove('activa'));
        chip.classList.add('activa');
        filtroEstado = chip.getAttribute('data-f');
        pintarLineas();
    }));

    await cargarFincas();
}