/**
 * inventario/resumenSector.js — Submenú "Resumen por sector".
 *
 * Réplica COMPLETA de la pestaña Resumen por Sector de /inventario: selector de
 * finca/sector/fecha (con calendario), KPIs, tabla de resumen esperado/contado/
 * diferencia/estado y acciones de cierre/reabrir de inventario por sector.
 */
let dc = null;
let fincasCache = [];
let fechasConInventario = [];
let datosActuales = null;
let mapaInvInstance = null;
let calPopTop = null;
let calPopPartes = null;

function el(id) { return document.getElementById(id); }

function fechaSeleccionada() { return el('inpDesde').value || dc.fechaLocalISO(new Date()); }
function pintarBtnFecha() { const b = el('btnFecha'); if (b) b.textContent = dc.fmtFechaInv(fechaSeleccionada()) || 'Hoy'; }

/** Calendario popup (misma lógica que /inventario.crearCalendario). */
function crearCalendario(cfg) {
    let mes = 0;
    function renderCal() {
        const pop = cfg.pop;
        const ahora = new Date();
        const base = new Date(ahora.getFullYear(), ahora.getMonth() + mes, 1);
        const anio = base.getFullYear(), mm = base.getMonth();
        const seleccion = cfg.input.value;
        const primerDow = base.getDay();
        const diasMes = new Date(anio, mm + 1, 0).getDate();
        let html = '<div class="cal-head"><button type="button" data-nav="-1">‹</button>' +
            `<span class="cal-titulo">${('0' + (mm + 1)).slice(-2)}/${anio}</span>` +
            '<button type="button" data-nav="1">›</button></div><div class="cal-grid">' +
            ['L', 'M', 'X', 'J', 'V', 'S', 'D'].map(d => `<div class="cal-dow">${d}</div>`).join('');
        const inicio = (primerDow === 0) ? 6 : primerDow - 1;
        for (let i = 0; i < inicio; i++) html += '<div class="cal-dia vacio"></div>';
        for (let dia = 1; dia <= diasMes; dia++) {
            const iso = `${anio}-${('0' + (mm + 1)).slice(-2)}-${('0' + dia).slice(-2)}`;
            const cls = 'cal-dia' + (fechasConInventario.indexOf(iso) >= 0 ? ' inv' : '') + (iso === seleccion ? ' seleccionado' : '');
            html += `<div class="${cls}" data-fecha="${iso}">${dia}</div>`;
        }
        pop.innerHTML = html;
        pop.querySelectorAll('[data-nav]').forEach(b => { b.onclick = () => { mes += parseInt(b.getAttribute('data-nav'), 10); renderCal(); }; });
        pop.querySelectorAll('.cal-dia:not(.vacio)').forEach(c => {
            c.onclick = () => {
                cfg.input.value = c.getAttribute('data-fecha');
                cfg.btn.textContent = dc.fmtFechaInv(cfg.input.value) || 'Hoy';
                pop.style.display = 'none';
                if (cfg.onSelect) cfg.onSelect();
            };
        });
    }
    cfg.btn.addEventListener('click', e => {
        e.stopPropagation();
        if (cfg.pop.style.display === 'block') { cfg.pop.style.display = 'none'; return; }
        document.querySelectorAll('.cal-popup').forEach(p => { p.style.display = 'none'; });
        mes = 0; renderCal(); cfg.pop.style.display = 'block';
    });
    document.addEventListener('click', e => {
        const wrap = cfg.btn.closest('.datepicker-wrap');
        if (wrap && !wrap.contains(e.target)) cfg.pop.style.display = 'none';
    });
    return { render: renderCal };
}

async function cargarFechasGlobal() {
    try {
        fechasConInventario = await dc.fetchInventarioFechas();
        calPopTop.render();
    } catch (e) { fechasConInventario = []; }
}

async function cargarFincas() {
    try {
        fincasCache = await dc.fetchInventarioFincas();
        const sel = el('selFinca');
        sel.innerHTML = '<option value="">— Selecciona una finca —</option>';
        fincasCache.forEach(f => {
            const sects = f.sectores || [];
            const tieneInv = sects.some(s => s.tieneInventario);
            const todosCerrados = sects.length > 0 && sects.every(s => s.cerrado);
            const algunAsignado = sects.some(s => s.asignado);
            const cls = todosCerrados ? 'sector-cerrado' : (tieneInv ? 'sector-abierto' : (algunAsignado ? 'sector-asignado' : ''));
            const op = document.createElement('option');
            if (cls) op.className = cls;
            op.value = f.finca;
            op.textContent = f.finca + (sects.length ? ` (${sects.length})` : '');
            sel.appendChild(op);
        });
        sel.onchange = cargarSectores;
        cargarSectores();
    } catch (e) { mostrarError('No se pudo cargar la lista de fincas. Comprueba la conexión.'); }
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
        if (s.cerrado) op.className = 'sector-cerrado';
        else if (s.tieneInventario) op.className = 'sector-abierto';
        else if (s.asignado) op.className = 'sector-asignado';
        sel.appendChild(op);
    });
}

function estadoSector(sectorId) {
    const finca = el('selFinca').value;
    const f = fincasCache.find(x => x.finca === finca);
    const s = f ? (f.sectores || []).find(y => y.id === sectorId) : null;
    return s || { cerrado: false, tieneInventario: false, asignado: false };
}

function kpi(t, v, s) {
    return `<div class="kpi"><div class="t">${dc.escHtml(t)}</div><div class="v">${dc.escHtml(v)}</div><div class="s">${dc.escHtml(s)}</div></div>`;
}

function vacio(ncols) {
    return `<tr><td class="vacio" colspan="${ncols || 5}">Sin datos para este ámbito.</td></tr>`;
}

function render(d) {
    datosActuales = d;
    el('contenido').style.display = '';
    let totalEsp = 0, totalCon = 0, fueraN = (d.fueraSector || []).length, etiN = (d.etiquetas || []).length;
    let huecosN = 0;
    (d.huecos || []).forEach(h => { huecosN += h.huecos; });
    (d.resumen || []).forEach(r => { totalEsp += r.esperado; totalCon += r.contado; });
    const difTotal = totalCon - totalEsp;
    el('kpis').innerHTML =
        kpi('Sectores', (d.resumen || []).length, '') +
        kpi('Esperado', dc.fmtNum(totalEsp), 'stock FactuSOL') +
        kpi('Contado', dc.fmtNum(totalCon), 'pistoleado compartido') +
        kpi('Diferencia', dc.fmtNum(difTotal), difTotal === 0 ? 'cuadra' : (difTotal > 0 ? 'exceso' : 'faltan')) +
        kpi('Fuera de sector', fueraN, 'reetiquetar') +
        kpi('Etiquetas a sacar', etiN, 'manual/OCR') +
        kpi('Huecos (lineal)', huecosN, 'espacios vacíos');

    const rb = document.querySelector('#tbResumen tbody');
    const fincaActual = el('selFinca').value;
    rb.innerHTML = (d.resumen || []).map(r => {
        const dif = r.dif || 0;
        const cls = dif === 0 ? 'verde' : dif > 0 ? 'ambar' : 'rojo';
        const est = estadoSector(r.sector);
        let badge = '';
        if (est.cerrado) {
            badge = `<span class="estado-badge badge-cerrado">&#10003; Cerrado</span> <button class="secundario" style="padding:2px 8px; font-size:10px;" onclick="toggleCierreSector('${dc.escHtml(fincaActual)}', '${dc.escHtml(r.sector)}', false)">Reabrir</button>`;
        } else if (est.tieneInventario) {
            badge = `<span class="estado-badge badge-abierto">&#9888; Abierto</span> <button style="padding:2px 8px; font-size:10px; background:var(--primary); color:var(--on-primary);" onclick="toggleCierreSector('${dc.escHtml(fincaActual)}', '${dc.escHtml(r.sector)}', true)">Cerrar inventario</button>`;
        } else if (est.asignado) {
            badge = '<span class="estado-badge badge-asignado">&#128221; Asignado</span>';
        } else {
            badge = '<span style="color:var(--gris-tx); font-size:11px;">Sin inventario</span>';
        }
        return `<tr><td><b>${dc.escHtml(r.sectorDesc)}</b></td><td>${dc.fmtNum(r.esperado)}</td><td>${dc.fmtNum(r.contado)}</td><td class="${cls}">${dc.fmtNum(dif)}</td><td>${badge}</td></tr>`;
    }).join('') || vacio(5);
}

function mostrarError(msg) {
    const elErr = el('error');
    if (elErr) { elErr.textContent = msg; elErr.style.display = ''; }
}

async function verAhora() {
    const finca = el('selFinca').value;
    if (!finca) { mostrarPartesDelDia(); return; }
    const sector = el('selSector').value;
    const desde = fechaSeleccionada();
    el('error').style.display = 'none';
    try {
        const data = await dc.fetchInventarioDatos({ finca, sector, desde });
        render(data);
    } catch (e) {
        mostrarError(`No se pudo cargar el análisis (${e.message}). Comprueba la conexión.`);
    }
}

function mostrarPartesDelDia() {
    // Sin finca: redirigir al submenú de partes (día de hoy).
    const nav = document.querySelector('#portal-sidebar .nav-item[data-key="inventario/partes"]');
    if (nav) nav.click();
}

async function toggleCierreSector(finca, sector, cerrar) {
    try {
        await dc.setCierreSector({ finca, sector, cerrado: cerrar });
        await cargarFincas();
        await verAhora();
    } catch (e) {
        alert('Error al actualizar cierre: ' + e.message);
    }
}

export async function renderResumenSector(root, dataConnector) {
    if (!root || !dataConnector) return;
    dc = dataConnector;
    root.innerHTML = `
        <div class="inventario-section">
            <div class="controles">
                <div class="wrap">
                    <div class="campo"><label for="selFinca">Finca</label><select id="selFinca"><option value="">— Selecciona una finca —</option></select></div>
                    <div class="campo"><label for="selSector">Sector</label><select id="selSector"><option value="">Todos</option></select></div>
                    <div class="campo">
                        <label>Conteos desde</label>
                        <div class="datepicker-wrap">
                            <button type="button" class="datepicker-btn" id="btnFecha"></button>
                            <input type="hidden" id="inpDesde">
                            <div id="calPop" class="cal-popup" style="display:none;"></div>
                        </div>
                    </div>
                    <button id="btnVer">Ver análisis</button>
                    <button class="secundario" id="btnPdf">📄 PDF</button>
                </div>
            </div>
            <div id="error" class="error" style="display:none"></div>
            <div id="contenido" style="display:none;">
                <div class="tarjetas" id="kpis"></div>
                <section id="sec-sectores" class="activa" style="display:block;">
                    <h2>Resumen por Sector y Cierre de Inventario</h2>
                    <div class="table-scroll">
                        <table id="tbResumen">
                            <thead><tr><th>Sector</th><th>Esperado</th><th>Contado</th><th>Dif.</th><th>Estado</th></tr></thead>
                            <tbody></tbody>
                        </table>
                    </div>
                </section>
            </div>
        </div>`;

    calPopTop = crearCalendario({
        btn: el('btnFecha'),
        input: el('inpDesde'),
        pop: el('calPop'),
        onSelect: () => verAhora(),
    });
    el('btnVer').addEventListener('click', verAhora);
    el('btnPdf').addEventListener('click', () => {
        const finca = el('selFinca').value;
        if (!finca) { alert('Selecciona una finca para generar el PDF del análisis.'); return; }
        window.open(dc.buildReportePdfUrl({ finca, sector: el('selSector').value, desde: fechaSeleccionada() }), '_blank');
    });
    window.toggleCierreSector = toggleCierreSector;

    pintarBtnFecha();
    await cargarFechasGlobal();
    await cargarFincas();
    const finca = el('selFinca').value;
    if (finca) await verAhora();
}