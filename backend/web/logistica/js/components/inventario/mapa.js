/**
 * inventario/mapa.js â€” Submenú "Mapa" (Leaflet).
 *
 * Réplica COMPLETA de la pestaña Mapa de /inventario: capas OSM/Satélite,
 * sesiones lineales (Aâ†’B con polilínea), puntos estándar con heatmap y
 * marcadores por estado (verde=pertenece, amarillo=reetiquetar, rojo=fuera).
 */
let dc = null;
let fincasCache = [];
let fechasConInventario = [];
let ultimosPuntosGps = [];
let mapaInvInstance = null;

function el(id) { return document.getElementById(id); }

function fechaSeleccionada() { return el('inpDesde').value || dc.fechaLocalISO(new Date()); }

function crearCalendario(cfg) {
    let mes = 0;
    function render() {
        const pop = cfg.pop;
        const ahora = new Date();
        const base = new Date(ahora.getFullYear(), ahora.getMonth() + mes, 1);
        const anio = base.getFullYear(), mm = base.getMonth();
        const seleccion = cfg.input.value;
        const primerDow = base.getDay();
        const diasMes = new Date(anio, mm + 1, 0).getDate();
        let html = '<div class="cal-head"><button type="button" data-nav="-1">â€¹</button>' +
            `<span class="cal-titulo">${('0' + (mm + 1)).slice(-2)}/${anio}</span>` +
            '<button type="button" data-nav="1">â€º</button></div><div class="cal-grid">' +
            ['L', 'M', 'X', 'J', 'V', 'S', 'D'].map(d => `<div class="cal-dow">${d}</div>`).join('');
        const inicio = (primerDow === 0) ? 6 : primerDow - 1;
        for (let i = 0; i < inicio; i++) html += '<div class="cal-dia vacio"></div>';
        for (let dia = 1; dia <= diasMes; dia++) {
            const iso = `${anio}-${('0' + (mm + 1)).slice(-2)}-${('0' + dia).slice(-2)}`;
            const cls = 'cal-dia' + (fechasConInventario.indexOf(iso) >= 0 ? ' inv' : '') + (iso === seleccion ? ' seleccionado' : '');
            html += `<div class="${cls}" data-fecha="${iso}">${dia}</div>`;
        }
        pop.innerHTML = html;
        pop.querySelectorAll('[data-nav]').forEach(b => { b.onclick = () => { mes += parseInt(b.getAttribute('data-nav'), 10); render(); }; });
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
        mes = 0; render(); cfg.pop.style.display = 'block';
    });
    document.addEventListener('click', e => {
        const wrap = cfg.btn.closest('.datepicker-wrap');
        if (wrap && !wrap.contains(e.target)) cfg.pop.style.display = 'none';
    });
    return { render };
}

async function cargarFechasGlobal() {
    try { fechasConInventario = await dc.fetchInventarioFechas(); } catch (e) { fechasConInventario = []; }
}

async function cargarFincas() {
    try {
        fincasCache = await dc.fetchInventarioFincas();
        const sel = el('selFinca');
        sel.innerHTML = '<option value="">â€” Selecciona una finca â€”</option>';
        fincasCache.forEach(f => {
            const op = document.createElement('option');
            op.value = f.finca;
            op.textContent = f.finca + ((f.sectores || []).length ? ` (${f.sectores.length})` : '');
            sel.appendChild(op);
        });
        sel.onchange = cargarSectores;
        cargarSectores();
    } catch (e) { /* silencioso */ }
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

function dibujarMapa(puntos) {
    const elMapa = el('mapaInventario');
    if (!elMapa || elMapa.offsetWidth === 0) return;
    if (mapaInvInstance) { mapaInvInstance.remove(); mapaInvInstance = null; }
    if (!puntos || puntos.length === 0) {
        elMapa.innerHTML = '<div class="vacio" style="padding:140px 0;">No hay coordenadas GPS registradas para este filtro o fecha.</div>';
        return;
    }
    elMapa.innerHTML = '';
    const osm = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 21, attribution: '&copy; OpenStreetMap' });
    const sat = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', { maxZoom: 21, attribution: 'Tiles &copy; Esri' });
    const m = L.map(elMapa, { layers: [osm], maxZoom: 21 }).setView([38.3453, -0.7681], 17);
    mapaInvInstance = m;
    L.control.layers({ 'Mapa (OSM)': osm, 'Satélite (Esri)': sat }, {}).addTo(m);

    const bounds = [];
    const linealSessions = {};
    const puntosEstandar = [];

    puntos.forEach(p => {
        if (p.linealSessionId && p.linealSessionId.trim() !== '') {
            (linealSessions[p.linealSessionId] = linealSessions[p.linealSessionId] || []).push(p);
        } else {
            puntosEstandar.push(p);
        }
    });

    // Sesiones lineales A -> B
    Object.keys(linealSessions).forEach(sessionId => {
        const sesionPuntos = linealSessions[sessionId];
        sesionPuntos.sort((a, b) => (a.fechaHora || '').localeCompare(b.fechaHora || ''));
        const latLngs = sesionPuntos.map(p => [p.latitud, p.longitud]);
        latLngs.forEach(ll => bounds.push(ll));
        L.polyline(latLngs, { color: '#025C65', weight: 4, opacity: 0.85, dashArray: '6, 6' }).addTo(m);
        const pA = sesionPuntos[0];
        const pB = sesionPuntos[sesionPuntos.length - 1];
        const markerA = L.circleMarker([pA.latitud, pA.longitud], { radius: 8, fillColor: '#2e7d32', color: '#fff', weight: 2, fillOpacity: 1 }).addTo(m);
        markerA.bindTooltip(`<b>Bancal Lineal - INICIO (A)</b><br>Hora: ${dc.escHtml(pA.fechaHora)}<br>Plantas: ${sesionPuntos.length}`, { direction: 'top' });
        const markerB = L.circleMarker([pB.latitud, pB.longitud], { radius: 8, fillColor: '#962622', color: '#fff', weight: 2, fillOpacity: 1 }).addTo(m);
        markerB.bindTooltip(`<b>Bancal Lineal - FIN (B)</b><br>Hora: ${dc.escHtml(pB.fechaHora)}<br>Plantas: ${sesionPuntos.length}`, { direction: 'top' });
        sesionPuntos.forEach((p, idx) => {
            if (idx > 0 && idx < sesionPuntos.length - 1) {
                const pt = L.circleMarker([p.latitud, p.longitud], { radius: 5, fillColor: '#025C65', color: '#fff', weight: 1, fillOpacity: 0.8 }).addTo(m);
                pt.bindTooltip(`<b>Planta / Registro Lineal</b><br>Ref: ${dc.escHtml(p.ref)} ${dc.escHtml(p.nombre)}<br>Hora: ${dc.escHtml(p.fechaHora)}`, { direction: 'top' });
            }
        });
    });

    // Puntos estándar (clustering / heatmap)
    if (puntosEstandar.length > 0) {
        const grupos = {};
        puntosEstandar.forEach(p => {
            const key = p.latitud.toFixed(5) + ',' + p.longitud.toFixed(5);
            (grupos[key] = grupos[key] || []).push(p);
        });
        const heatData = [];
        Object.keys(grupos).forEach(key => {
            const items = grupos[key];
            const p0 = items[0];
            const peor = items.some(i => i.color === 'rojo') ? 'rojo' : (items.some(i => i.color === 'amarillo') ? 'amarillo' : 'verde');
            const intensity = peor === 'rojo' ? 1.0 : (peor === 'amarillo' ? 0.8 : 0.4);
            heatData.push([p0.latitud, p0.longitud, intensity]);
            bounds.push([p0.latitud, p0.longitud]);
        });
        if (typeof L.heatLayer === 'function') L.heatLayer(heatData, { radius: 20, blur: 12, maxZoom: 21 }).addTo(m);

        Object.keys(grupos).forEach(key => {
            const items = grupos[key];
            const p0 = items[0];
            const peor = items.some(i => i.color === 'rojo') ? 'rojo' : (items.some(i => i.color === 'amarillo') ? 'amarillo' : 'verde');
            const colorHex = peor === 'rojo' ? '#962622' : (peor === 'amarillo' ? '#d97706' : '#2e7d32');
            const totalCant = items.reduce((a, b) => a + b.cantidad, 0);
            const radius = 6 + Math.min(8, Math.floor(Math.sqrt(items.length)));
            const marker = L.circleMarker([p0.latitud, p0.longitud], { radius, fillColor: colorHex, color: '#fff', weight: 1.5, fillOpacity: 0.95 }).addTo(m);

            const artMap = {};
            items.forEach(i => {
                const k = i.ref + '|' + i.litraje;
                if (!artMap[k]) artMap[k] = { ref: i.ref, nombre: i.nombre, litraje: i.litraje, cant: 0 };
                artMap[k].cant += i.cantidad;
            });
            const listaArticulos = Object.keys(artMap).map(k => {
                const a = artMap[k];
                return `&nbsp;&nbsp;â€¢ <b>${dc.escHtml(a.ref)}</b> ${dc.escHtml(a.nombre)}${a.litraje ? ' (' + dc.escHtml(a.litraje) + ')' : ''}: ${dc.fmtNum(a.cantidad)}`;
            }).join('<br>');
            const fechasItems = items.map(i => i.fechaHora).sort();
            const emp = [];
            items.forEach(i => { if (emp.indexOf(i.empleado) < 0) emp.push(i.empleado); });

            const tooltipHtml = '<div style="font-size:12px; line-height:1.5;">' +
                `<b>${items.length} escaneos Â· ${dc.fmtNum(totalCant)} plantas</b><br>` +
                `<b>Sector:</b> ${dc.escHtml(p0.sectorDesc)}<br>` +
                (listaArticulos ? listaArticulos + '<br>' : '') +
                `<b>Fecha:</b> ${dc.escHtml(dc.fmtFechaInv(fechasItems[0]))}${fechasItems.length > 1 ? ' â†’ ' + dc.escHtml(dc.fmtFechaInv(fechasItems[fechasItems.length - 1])) : ''}<br>` +
                `<b>Empleados:</b> ${dc.escHtml(emp.join(', '))}<br>` +
                `<b>GPS:</b> ${p0.latitud.toFixed(5)}, ${p0.longitud.toFixed(5)}` +
                '</div>';
            marker.bindTooltip(tooltipHtml, { direction: 'top', sticky: true, opacity: 0.95 });
        });
    }

    if (bounds.length > 0) m.fitBounds(L.latLngBounds(bounds), { padding: [35, 35], maxZoom: 19 });
}

async function verAhora() {
    const finca = el('selFinca').value;
    if (!finca) return;
    const sector = el('selSector').value;
    const desde = fechaSeleccionada();
    try {
        const data = await dc.fetchInventarioDatos({ finca, sector, desde });
        ultimosPuntosGps = data.puntosGps || [];
        dibujarMapa(ultimosPuntosGps);
    } catch (e) {
        console.error(e);
    }
}

export async function renderMapa(root, dataConnector) {
    if (!root || !dataConnector) return;
    dc = dataConnector;
    root.innerHTML = `
        <div class="inventario-section">
            <div class="controles">
                <div class="wrap">
                    <div class="campo"><label for="selFinca">Finca</label><select id="selFinca"><option value="">â€” Selecciona una finca â€”</option></select></div>
                    <div class="campo"><label for="selSector">Sector</label><select id="selSector"><option value="">Todos</option></select></div>
                    <div class="campo">
                        <label>Conteos desde</label>
                        <div class="datepicker-wrap">
                            <button type="button" class="datepicker-btn" id="btnFecha"></button>
                            <input type="hidden" id="inpDesde">
                            <div id="calPop" class="cal-popup" style="display:none;"></div>
                        </div>
                    </div>
                    <button id="btnVer">Ver</button>
                </div>
            </div>
            <section id="sec-mapa" style="display:block;">
                <h2>
                    Mapa de Calor y Puntos GPS (Escaneos en Campo)
                    <span style="font-size:11px; font-weight:normal; color:var(--gris-tx);">
                        <span style="color:#2e7d32; font-weight:700;">â— Verde:</span> Pertenece al sector &nbsp;|&nbsp;
                        <span style="color:#d97706; font-weight:700;">â— Amarillo:</span> Reetiquetar / Sin etiqueta &nbsp;|&nbsp;
                        <span style="color:#962622; font-weight:700;">â— Rojo:</span> Fuera de sector
                    </span>
                </h2>
                <div id="mapaInventario" style="height:520px; width:100%;"></div>
            </section>
        </div>`;

    const cal = crearCalendario({ btn: el('btnFecha'), input: el('inpDesde'), pop: el('calPop'), onSelect: verAhora });
    cal.render();
    el('btnFecha').textContent = dc.fmtFechaInv(fechaSeleccionada()) || 'Hoy';
    el('btnVer').addEventListener('click', verAhora);

    await cargarFechasGlobal();
    await cargarFincas();
    const finca = el('selFinca').value;
    if (finca) await verAhora();
}