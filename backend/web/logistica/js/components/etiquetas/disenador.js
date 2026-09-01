/**
 * etiquetas/disenador.js — Submenú "Diseñador de etiquetas".
 *
 * Port fiel del diseñador industrial de etiquetas (etiquetas.html, WIP propio
 * del portal /logistica): plantillas BD, canvas WYSIWYG de bobina, capas,
 * inspector, barcode EAN-13 (JsBarcode) y preview de artículo real desde API.
 *
 * Se integra en el portal como componente ES con CSS scoped (.designer-root)
 * para no colisionar con el resto de secciones.
 */
let dc = null;
let rootEl = null;
let plantillasCargadas = [];
let plantillaActual = null;
let elementoSeleccionadoIdx = null;
let dragState = null;
let pendingImageElement = null;
let modalCallback = null;
let articuloActualIdx = 0;
let toastsCreados = false;

const SCALE = 3.78;
const SNAP = 2;
const TPL_STORAGE = 'pickingve_etiquetas_templates';
const TPL_LEGACY = 'pickingve_plantillas';

// Bandera de la UE: fallback vectorial del elemento de imagen del pasaporte.
function estrellaPath(cx, cy, R) {
    const r = R * 0.382;
    let d = '';
    for (let i = 0; i < 10; i++) {
        const rad = i % 2 === 0 ? R : r;
        const a = (-90 + i * 36) * Math.PI / 180;
        d += (i === 0 ? 'M' : 'L') + (cx + rad * Math.cos(a)).toFixed(2) + ' ' + (cy + rad * Math.sin(a)).toFixed(2) + ' ';
    }
    return d + 'Z';
}
function generarBanderaUE() {
    const w = 27, h = 18, cx = 13.5, cy = 9, radio = 5.5, R = 1.15;
    let stars = '';
    for (let i = 0; i < 12; i++) {
        const ang = (-90 + i * 30) * Math.PI / 180;
        stars += estrellaPath(cx + radio * Math.cos(ang), cy + radio * Math.sin(ang), R);
    }
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${w} ${h}"><rect width="${w}" height="${h}" fill="#003399"/><g fill="#FFCC00">${stars}</g></svg>`;
    return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
}
const BANDERA_UE_SVG = generarBanderaUE();

const camposDisponibles = [
    { id: 'BLOQUE_PASAPORTE', label: 'Pasaporte Fitosanitario (bloque A-D)' },
    { id: 'NOMBRE_CIENTIFICO', label: 'Pasaporte A: Nombre Científico (Especie)' },
    { id: 'VARIEDAD_FORMACION', label: 'Nombre Comercial Planta' },
    { id: 'ID_ARTICULO', label: 'Referencia Artículo (ej: 10001-AS)' },
    { id: 'CONTENEDOR', label: 'Litraje / Formato Contenedor (ej: 235-350L)' },
    { id: 'GGN', label: 'Certificación GGN / GlobalG.A.P.' },
    { id: 'UBICACION_SECTOR', label: 'Sector Cultivo / Ubicación (ej: UMBRACULOS)' },
    { id: 'CODIGO_EAN13_BARRAS', label: 'Código de Barras EAN-13' },
    { id: 'CODIGO_LOTE', label: 'Código Trazabilidad / Lote (Campo C)' },
    { id: 'CODIGO_QR', label: 'Código QR Unifilar' },
    { id: 'TEXTO_LIBRE', label: 'Texto Libre / Nº Lote' },
];

let articulosPrueba = [];

function el(id) { return rootEl ? rootEl.querySelector(`#${id}`) : null; }

function getCampoLabel(campoId) {
    const found = camposDisponibles.find(c => c.id === campoId);
    return found ? found.label : campoId;
}

/** Plantilla del sistema: no se puede sobrescribir ni eliminar (D-273). */
function esPlantillaSistema(plantilla) {
    if (!plantilla) return false;
    if (plantilla.es_sistema) return true;
    const id = plantilla.id || '';
    return id.startsWith('tpl-sistema-') || id === 'tpl-grande-default' || id === 'tpl-pequena-default';
}

/** Guarda la lista en localStorage SIN tocar el modelo (caché offline). */
function persistirPlantillasLocalCache(lista) {
    try { localStorage.setItem(TPL_STORAGE, JSON.stringify(lista || [])); } catch (e) { console.error('Error persistiendo caché de plantillas:', e); }
}

function obtenerPlantilla9992() {
    return {
        id: 'tpl-sistema-9992', nombre: '9992 - Etiqueta Gran', ancho_mm: 50, alto_mm: 80,
        margen_sup_mm: 1.30, margen_izq_mm: 1.00, cols: 2, rows: 1, gap_h_mm: 3.00, gap_v_mm: 3.00,
        tipo_origen: 'GENERAL', es_sistema: true,
        elementos_json: [
            { tipo: 'rect', pos_x_mm: 1, pos_y_mm: 1, ancho_mm: 47, alto_mm: 35, borde_color: '#000000', borde_grosor: 1, relleno_color: 'transparent', radio_borde: 0, estilo_linea: 'solid' },
            { tipo: 'image', pos_x_mm: 3, pos_y_mm: 3, ancho_mm: 12, alto_mm: 8, imagen_data: '', mantener_proporcion: true, fallback: 'bandera_ue' },
            { tipo: 'text', pos_x_mm: 17, pos_y_mm: 4, ancho_mm: 30, alto_mm: 6, texto: 'Plant Passport', tamano_fuente_pt: 12, negrita: true, alineacion: 'left', color: '#000000' },
            { tipo: 'text', pos_x_mm: 3, pos_y_mm: 12, ancho_mm: 6, alto_mm: 5, texto: 'A:', tamano_fuente_pt: 10, negrita: true, alineacion: 'left', color: '#000000' },
            { tipo: 'db', campo_id: 'NOMBRE_CIENTIFICO', pos_x_mm: 10, pos_y_mm: 12, ancho_mm: 36, alto_mm: 5, tamano_fuente_pt: 10, negrita: false, alineacion: 'left', prefijo: '', sufijo: '' },
            { tipo: 'text', pos_x_mm: 3, pos_y_mm: 18, ancho_mm: 6, alto_mm: 5, texto: 'B:', tamano_fuente_pt: 10, negrita: true, alineacion: 'left', color: '#000000' },
            { tipo: 'text', pos_x_mm: 10, pos_y_mm: 18, ancho_mm: 36, alto_mm: 5, texto: 'ES-17031672', tamano_fuente_pt: 10, negrita: false, alineacion: 'left', color: '#000000' },
            { tipo: 'text', pos_x_mm: 3, pos_y_mm: 24, ancho_mm: 6, alto_mm: 5, texto: 'C:', tamano_fuente_pt: 10, negrita: true, alineacion: 'left', color: '#000000' },
            { tipo: 'db', campo_id: 'CODIGO_LOTE', pos_x_mm: 10, pos_y_mm: 24, ancho_mm: 36, alto_mm: 5, tamano_fuente_pt: 10, negrita: false, alineacion: 'left', prefijo: '', sufijo: '' },
            { tipo: 'text', pos_x_mm: 3, pos_y_mm: 30, ancho_mm: 6, alto_mm: 5, texto: 'D:', tamano_fuente_pt: 10, negrita: true, alineacion: 'left', color: '#000000' },
            { tipo: 'text', pos_x_mm: 10, pos_y_mm: 30, ancho_mm: 36, alto_mm: 5, texto: 'ES', tamano_fuente_pt: 10, negrita: false, alineacion: 'left', color: '#000000' },
            { tipo: 'db', campo_id: 'CONTENEDOR', pos_x_mm: 3, pos_y_mm: 37, ancho_mm: 44, alto_mm: 6, tamano_fuente_pt: 14, negrita: true, alineacion: 'left' },
            { tipo: 'db', campo_id: 'VARIEDAD_FORMACION', pos_x_mm: 3, pos_y_mm: 43, ancho_mm: 44, alto_mm: 5, tamano_fuente_pt: 11, negrita: true, alineacion: 'left' },
            { tipo: 'db', campo_id: 'UBICACION_SECTOR', pos_x_mm: 3, pos_y_mm: 49, ancho_mm: 25, alto_mm: 4, tamano_fuente_pt: 7, negrita: false, alineacion: 'left', prefijo: '', sufijo: ' * GGN ' },
            { tipo: 'db', campo_id: 'GGN', pos_x_mm: 28, pos_y_mm: 49, ancho_mm: 19, alto_mm: 4, tamano_fuente_pt: 7, negrita: false, alineacion: 'left', prefijo: 'GGN ', sufijo: '' },
            { tipo: 'db', campo_id: 'CODIGO_EAN13_BARRAS', pos_x_mm: 3, pos_y_mm: 54, ancho_mm: 44, alto_mm: 22, tamano_fuente_pt: 10 },
        ],
    };
}

function cargarPlantillasLocales() {
    try {
        const data = localStorage.getItem(TPL_STORAGE);
        if (data) return JSON.parse(data);
        const legacy = localStorage.getItem(TPL_LEGACY);
        if (legacy) {
            localStorage.setItem(TPL_STORAGE, legacy);
            localStorage.removeItem(TPL_LEGACY);
            return JSON.parse(legacy);
        }
    } catch (e) { console.error('Error cargando plantillas locales:', e); }
    return [];
}

function persistirPlantillasLocal() {
    try {
        sincronizarFormularioAModelo();
        localStorage.setItem(TPL_STORAGE, JSON.stringify(plantillasCargadas));
    } catch (e) { console.error('Error persistiendo plantillas:', e); }
}

function actualizarSelectPlantillas(seleccionarId) {
    const sel = el('select-plantilla');
    if (!sel) return;
    sel.innerHTML = plantillasCargadas.map(t => {
        const prefix = t.es_sistema ? '[Sistema] ' : '';
        return `<option value="${t.id}">${prefix}${t.nombre}</option>`;
    }).join('');
    if (seleccionarId) sel.value = seleccionarId;
    else if (plantillaActual) sel.value = plantillaActual.id;
}

function getBobinaConfig() {
    return {
        cols: parseInt(el('tpl-cols').value) || 1,
        rows: parseInt(el('tpl-rows').value) || 1,
        margen_sup: parseFloat(el('tpl-margen-sup').value) || 0,
        margen_izq: parseFloat(el('tpl-margen-izq').value) || 0,
        gap_h: parseFloat(el('tpl-gap-h').value) || 0,
        gap_v: parseFloat(el('tpl-gap-v').value) || 0,
    };
}

function getLabelDimensions() {
    return { ancho: parseFloat(el('tpl-ancho').value) || 100, alto: parseFloat(el('tpl-alto').value) || 50 };
}

function generarBarcodeSVG(valor, anchoMm, altoMm) {
    const codigo = String(valor || '').trim();
    if (!/^\d{13}$/.test(codigo) || typeof JsBarcode === 'undefined') return '';
    try {
        const svgEl = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        JsBarcode(svgEl, codigo, { format: 'EAN13', width: 1.5, height: Math.max(20, (altoMm || 20) * 2.5), displayValue: true, fontSize: 11, margin: 0, background: 'transparent', lineColor: '#000000' });
        const w = parseFloat(svgEl.getAttribute('width')) || 142;
        const h = parseFloat(svgEl.getAttribute('height')) || Math.max(20, (altoMm || 20) * 2.5) + 16;
        return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${w} ${h}" preserveAspectRatio="none" style="width:100%; height:100%; display:block;">${svgEl.innerHTML}</svg>`;
    } catch (e) {
        return `<span style="font-family:monospace; font-size:9px;">${codigo}</span>`;
    }
}

function renderActiveLabelElements() {
    let html = '';
    plantillaActual.elementos_json.forEach((element, idx) => {
        const px = element.pos_x_mm * SCALE, py = element.pos_y_mm * SCALE;
        const pw = element.ancho_mm * SCALE, ph = element.alto_mm * SCALE;
        const sel = idx === elementoSeleccionadoIdx ? 'selected' : '';
        const handles = idx === elementoSeleccionadoIdx ? '<div class="resize-handle nw" data-handle="nw"></div><div class="resize-handle ne" data-handle="ne"></div><div class="resize-handle sw" data-handle="sw"></div><div class="resize-handle se" data-handle="se"></div>' : '';
        let style = `left:${px}px; top:${py}px; width:${pw}px; height:${ph}px;`;
        let extra = '', cls = 'canvas-element';

        const fuente = element.fuente || 'Arial';
        const tamano = element.tamano_fuente_pt || 10;
        const negrita = element.negrita ? 'bold' : 'normal';
        const cursiva = element.cursiva ? 'italic' : 'normal';
        const subrayado = element.subrayado ? 'underline' : 'none';
        const alineacion = element.alineacion || 'left';

        if (element.tipo === 'db') {
            if (element.campo_id === 'CODIGO_EAN13_BARRAS') {
                extra = `<div style="width:100%; height:100%; display:flex; align-items:center; justify-content:center; overflow:visible;">${generarBarcodeSVG('8438002215009', element.ancho_mm, element.alto_mm) || '<span style="font-size:9px;">[EAN-13]</span>'}</div>`;
            } else {
                style += ` font-family:${fuente}; font-size:${tamano}px; font-weight:${negrita}; font-style:${cursiva}; text-decoration:${subrayado}; text-align:${alineacion}; color:${element.color || '#2C3E50'};`;
                extra = `<span style="display:block; width:100%; height:100%; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;">${getCampoLabel(element.campo_id)}</span>`;
            }
        } else if (element.tipo === 'rect') {
            cls += ' shape-rect';
            style += ` border:${element.borde_grosor || 1}px ${element.estilo_linea === 'dashed' ? 'dashed' : 'solid'} ${element.borde_color || '#025C65'}; background:${element.relleno_color || 'transparent'}; border-radius:${element.radio_borde || 0}px;`;
        } else if (element.tipo === 'circle') {
            cls += ' shape-circle';
            style += ` border:${element.borde_grosor || 1}px solid ${element.borde_color || '#025C65'}; background:${element.relleno_color || 'transparent'};`;
        } else if (element.tipo === 'line') {
            cls += ' shape-line';
            const isH = element.ancho_mm > element.alto_mm;
            style += ` width:${(isH ? element.ancho_mm : (element.borde_grosor || 1)) * SCALE}px; height:${(isH ? (element.borde_grosor || 1) : element.alto_mm) * SCALE}px; border-top:${element.estilo_linea === 'dashed' ? '2px dashed' : '2px solid'} ${element.borde_color || '#025C65'};`;
        } else if (element.tipo === 'image') {
            cls += ' shape-image';
            const src = element.imagen_data || (element.fallback === 'bandera_ue' ? BANDERA_UE_SVG : '');
            extra = src ? `<img src="${src}" alt="img">` : '<i class="fa-solid fa-image" style="font-size:16px; color:#94a3b8;"></i>';
        } else if (element.tipo === 'text') {
            style += ` font-family:${fuente}; font-size:${tamano}px; font-weight:${negrita}; font-style:${cursiva}; text-decoration:${subrayado}; text-align:${alineacion}; color:${element.color || '#2C3E50'};`;
            extra = `<span style="overflow:hidden; text-overflow:ellipsis; white-space:nowrap; width:100%; display:block;">${element.texto || 'Texto'}</span>`;
        }
        html += `<div class="${cls} ${sel}" data-idx="${idx}" style="${style}">${extra}${handles}</div>`;
    });
    return html;
}

function renderBobina() {
    if (!plantillaActual) return;
    const bobina = el('bobina-canvas');
    const dim = getLabelDimensions();
    const cfg = getBobinaConfig();
    const totalW = cfg.margen_izq + (cfg.cols * dim.ancho) + ((cfg.cols - 1) * cfg.gap_h) + cfg.margen_izq;
    const totalH = cfg.margen_sup + (cfg.rows * dim.alto) + ((cfg.rows - 1) * cfg.gap_v) + cfg.margen_sup;
    bobina.style.width = (totalW * SCALE) + 'px';
    bobina.style.height = (totalH * SCALE) + 'px';

    let html = '';
    for (let r = 0; r < cfg.rows; r++) {
        for (let c = 0; c < cfg.cols; c++) {
            const lx = cfg.margen_izq + c * (dim.ancho + cfg.gap_h);
            const ly = cfg.margen_sup + r * (dim.alto + cfg.gap_v);
            const isActive = (r === 0 && c === 0);
            const isGhost = (r === 0 && c === 1) || (r === 1 && c === 0);
            const cls = isActive ? 'label-slot active' : (isGhost ? 'label-slot ghost' : 'label-slot');
            html += `<div class="${cls}" style="left:${lx * SCALE}px; top:${ly * SCALE}px; width:${dim.ancho * SCALE}px; height:${dim.alto * SCALE}px;" data-label-x="${lx}" data-label-y="${ly}">`;
            if (isActive) html += renderActiveLabelElements();
            html += '</div>';
        }
    }
    bobina.innerHTML = html;
    attachCanvasEvents();
    renderLayersList();
}

function renderLayersList() {
    const c = el('layers-list');
    if (!plantillaActual || !plantillaActual.elementos_json.length) { if (c) c.innerHTML = '<div style="font-size:11px; color:var(--text-muted);">Sin elementos</div>'; return; }
    c.innerHTML = plantillaActual.elementos_json.map((element, idx) => {
        const active = idx === elementoSeleccionadoIdx ? 'active' : '';
        let icon = 'fa-font', name = '';
        if (element.tipo === 'db') { icon = 'fa-database'; name = getCampoLabel(element.campo_id); }
        else if (element.tipo === 'rect') { icon = 'fa-square'; name = 'Rectángulo'; }
        else if (element.tipo === 'circle') { icon = 'fa-circle'; name = 'Círculo'; }
        else if (element.tipo === 'line') { icon = 'fa-minus'; name = 'Línea'; }
        else if (element.tipo === 'image') { icon = 'fa-image'; name = 'Imagen'; }
        else if (element.tipo === 'text') { icon = 'fa-font'; name = element.texto || 'Texto'; }
        return `<div class="layer-item ${active}" onclick="window._dgSeleccionarElemento(${idx})"><i class="fa-solid ${icon} l-icon"></i><span class="layer-name">${name}</span><span class="layer-del" onclick="event.stopPropagation(); window._dgEliminarElemento(${idx})"><i class="fa-solid fa-trash" style="font-size:10px; color:var(--danger);"></i></span></div>`;
    }).join('');
}

function attachCanvasEvents() {
    const activeSlot = rootEl.querySelector('.label-slot.active');
    if (!activeSlot) return;
    activeSlot.querySelectorAll('.canvas-element').forEach(elDiv => {
        const idx = parseInt(elDiv.dataset.idx);
        elDiv.addEventListener('mousedown', (e) => {
            if (e.target.classList.contains('resize-handle')) return;
            e.preventDefault(); e.stopPropagation();
            seleccionarElemento(idx);
            dragState = { type: 'move', idx, startX: e.clientX, startY: e.clientY, origX: plantillaActual.elementos_json[idx].pos_x_mm, origY: plantillaActual.elementos_json[idx].pos_y_mm };
        });
        elDiv.querySelectorAll('.resize-handle').forEach(h => {
            h.addEventListener('mousedown', (e) => {
                e.preventDefault(); e.stopPropagation();
                const element = plantillaActual.elementos_json[idx];
                dragState = { type: 'resize', idx, corner: h.dataset.handle, startX: e.clientX, startY: e.clientY, origX: element.pos_x_mm, origY: element.pos_y_mm, origW: element.ancho_mm, origH: element.alto_mm };
            });
        });
    });
    activeSlot.addEventListener('click', (e) => {
        if (e.target === activeSlot) { elementoSeleccionadoIdx = null; const insp = el('inspector-elemento'); if (insp) insp.style.display = 'none'; renderBobina(); }
    });
}

function updateElementPositionOnCanvas(idx) {
    const activeSlot = rootEl.querySelector('.label-slot.active');
    if (!activeSlot) return;
    const elDiv = activeSlot.querySelector(`[data-idx="${idx}"]`);
    if (!elDiv) return;
    const element = plantillaActual.elementos_json[idx];
    elDiv.style.left = (element.pos_x_mm * SCALE) + 'px';
    elDiv.style.top = (element.pos_y_mm * SCALE) + 'px';
    elDiv.style.width = (element.ancho_mm * SCALE) + 'px';
    elDiv.style.height = (element.alto_mm * SCALE) + 'px';
}

function updateInspectorFromModel() {
    if (elementoSeleccionadoIdx === null) return;
    const element = plantillaActual.elementos_json[elementoSeleccionadoIdx];
    const inspX = el('insp-x'), inspY = el('insp-y'), inspW = el('insp-w'), inspH = el('insp-h');
    if (inspX) inspX.value = element.pos_x_mm;
    if (inspY) inspY.value = element.pos_y_mm;
    if (inspW) inspW.value = element.ancho_mm;
    if (inspH) inspH.value = element.alto_mm;
}

function renderInspector() {
    const panel = el('inspector-elemento');
    const content = el('inspector-content');
    if (elementoSeleccionadoIdx === null || !plantillaActual) { if (panel) panel.style.display = 'none'; return; }
    panel.style.display = '';
    const element = plantillaActual.elementos_json[elementoSeleccionadoIdx];
    let html = '';

    const tipoLabel = element.tipo === 'db' ? 'Campo BD: ' + getCampoLabel(element.campo_id) : element.tipo.toUpperCase();
    html += '<label>Tipo</label><input type="text" readonly value="' + tipoLabel + '" style="background:#f1f5f9;">';
    html += '<div class="row-2"><div><label>Pos X (mm)</label><input type="number" id="insp-x" step="0.5" value="' + element.pos_x_mm + '" oninput="window._dgActualizarDesdeInspector()"></div>';
    html += '<div><label>Pos Y (mm)</label><input type="number" id="insp-y" step="0.5" value="' + element.pos_y_mm + '" oninput="window._dgActualizarDesdeInspector()"></div></div>';
    html += '<div class="row-2"><div><label>Ancho (mm)</label><input type="number" id="insp-w" step="0.5" value="' + element.ancho_mm + '" oninput="window._dgActualizarDesdeInspector()"></div>';
    html += '<div><label>Alto (mm)</label><input type="number" id="insp-h" step="0.5" value="' + element.alto_mm + '" oninput="window._dgActualizarDesdeInspector()"></div></div>';

    const esTexto = (element.tipo === 'db' || element.tipo === 'text');
    if (esTexto) {
        const fuentes = ['Arial', 'Calibri', 'Times New Roman', 'Courier New', 'Verdana', 'Roboto'];
        const fuenteActual = element.fuente || 'Arial';
        html += '<label>Familia Tipográfica</label><select onchange="window._dgUpdProp(\'fuente\', this.value)">';
        fuentes.forEach(f => { html += '<option value="' + f + '"' + (fuenteActual === f ? ' selected' : '') + ' style="font-family:' + f + ';">' + f + '</option>'; });
        html += '</select>';

        html += '<div class="row-2"><div><label>Tamaño (pt)</label><input type="number" id="insp-fontsize" min="6" max="36" step="1" value="' + (element.tamano_fuente_pt || 10) + '" oninput="window._dgUpdProp(\'tamano_fuente_pt\', parseFloat(this.value))"></div>';
        html += '<div><label>Color</label><input type="color" value="' + (element.color || '#2C3E50') + '" onchange="window._dgUpdProp(\'color\', this.value)"></div></div>';

        const negrita = element.negrita ? 'active' : '';
        const cursiva = element.cursiva ? 'active' : '';
        const subrayado = element.subrayado ? 'active' : '';
        html += '<div class="text-style-btns">';
        html += '<button class="btn-bold ' + negrita + '" onclick="window._dgToggleEstilo(\'negrita\')" title="Negrita">B</button>';
        html += '<button class="btn-italic ' + cursiva + '" onclick="window._dgToggleEstilo(\'cursiva\')" title="Cursiva">I</button>';
        html += '<button class="btn-underline ' + subrayado + '" onclick="window._dgToggleEstilo(\'subrayado\')" title="Subrayado">U</button>';
        html += '</div>';

        const alineacion = element.alineacion || 'left';
        html += '<label>Alineación</label><select onchange="window._dgUpdProp(\'alineacion\', this.value)">';
        html += '<option value="left"' + (alineacion === 'left' ? ' selected' : '') + '>Izquierda</option>';
        html += '<option value="center"' + (alineacion === 'center' ? ' selected' : '') + '>Centro</option>';
        html += '<option value="right"' + (alineacion === 'right' ? ' selected' : '') + '>Derecha</option>';
        html += '</select>';
    }

    if (element.tipo === 'db') {
        html += '<label>Prefijo</label><input type="text" value="' + (element.prefijo || '') + '" onchange="window._dgUpdProp(\'prefijo\', this.value)">';
        html += '<label>Sufijo</label><input type="text" value="' + (element.sufijo || '') + '" onchange="window._dgUpdProp(\'sufijo\', this.value)">';
    } else if (element.tipo === 'text') {
        html += '<label>Texto</label><input type="text" value="' + (element.texto || '') + '" onchange="window._dgUpdProp(\'texto\', this.value)">';
    } else if (element.tipo === 'rect') {
        html += '<div class="row-2"><div><label>Color Borde</label><input type="color" value="' + (element.borde_color || '#025C65') + '" onchange="window._dgUpdProp(\'borde_color\', this.value)"></div>';
        html += '<div><label>Grosor (px)</label><input type="number" value="' + (element.borde_grosor || 1) + '" onchange="window._dgUpdProp(\'borde_grosor\', parseFloat(this.value))"></div></div>';
        html += '<div class="row-2"><div><label>Relleno</label><input type="color" value="' + (element.relleno_color && element.relleno_color !== 'transparent' ? element.relleno_color : '#ffffff') + '" onchange="window._dgUpdProp(\'relleno_color\', this.value)"></div>';
        html += '<div><label>Radio (px)</label><input type="number" value="' + (element.radio_borde || 0) + '" onchange="window._dgUpdProp(\'radio_borde\', parseFloat(this.value))"></div></div>';
        html += '<label><input type="checkbox" ' + (element.relleno_color === 'transparent' ? '' : 'checked') + ' onchange="window._dgUpdProp(\'relleno_color\', this.checked ? \'#e0f2f1\' : \'transparent\')"> Relleno sólido</label>';
        html += '<label>Estilo Borde</label><select onchange="window._dgUpdProp(\'estilo_linea\', this.value)"><option value="solid"' + (element.estilo_linea === 'solid' ? ' selected' : '') + '>Sólido</option><option value="dashed"' + (element.estilo_linea === 'dashed' ? ' selected' : '') + '>Punteado</option></select>';
    } else if (element.tipo === 'circle') {
        html += '<div class="row-2"><div><label>Color Borde</label><input type="color" value="' + (element.borde_color || '#025C65') + '" onchange="window._dgUpdProp(\'borde_color\', this.value)"></div>';
        html += '<div><label>Grosor (px)</label><input type="number" value="' + (element.borde_grosor || 1) + '" onchange="window._dgUpdProp(\'borde_grosor\', parseFloat(this.value))"></div></div>';
        html += '<label><input type="checkbox" ' + (element.relleno_color === 'transparent' ? '' : 'checked') + ' onchange="window._dgUpdProp(\'relleno_color\', this.checked ? \'#e0f2f1\' : \'transparent\')"> Relleno sólido</label>';
    } else if (element.tipo === 'line') {
        html += '<div class="row-2"><div><label>Color</label><input type="color" value="' + (element.borde_color || '#025C65') + '" onchange="window._dgUpdProp(\'borde_color\', this.value)"></div>';
        html += '<div><label>Grosor (px)</label><input type="number" value="' + (element.borde_grosor || 1) + '" onchange="window._dgUpdProp(\'borde_grosor\', parseFloat(this.value))"></div></div>';
        html += '<label>Estilo</label><select onchange="window._dgUpdProp(\'estilo_linea\', this.value)"><option value="solid"' + (element.estilo_linea === 'solid' ? ' selected' : '') + '>Sólido</option><option value="dashed"' + (element.estilo_linea === 'dashed' ? ' selected' : '') + '>Punteado</option></select>';
    } else if (element.tipo === 'image') {
        html += '<label><input type="checkbox" ' + (element.mantener_proporcion ? 'checked' : '') + ' onchange="window._dgUpdProp(\'mantener_proporcion\', this.checked)"> Mantener proporción</label>';
        html += '<button class="btn btn-sm btn-secondary" onclick="window._dgReemplazarImagen()"><i class="fa-solid fa-image"></i> Reemplazar imagen</button>';
    }
    content.innerHTML = html;
}

function seleccionarElemento(idx) {
    elementoSeleccionadoIdx = idx;
    const insp = el('inspector-elemento');
    insp.style.display = '';
    insp.open = true;
    renderBobina();
    renderInspector();
    renderLayersList();
}

function eliminarElemento(idx) {
    if (!plantillaActual) return;
    plantillaActual.elementos_json.splice(idx, 1);
    if (elementoSeleccionadoIdx === idx) { elementoSeleccionadoIdx = null; const insp = el('inspector-elemento'); if (insp) insp.style.display = 'none'; }
    else if (elementoSeleccionadoIdx > idx) elementoSeleccionadoIdx--;
    renderizarListaElementosToggle();
    renderBobina();
}

function toggleEstilo(estilo) {
    if (elementoSeleccionadoIdx === null) return;
    const element = plantillaActual.elementos_json[elementoSeleccionadoIdx];
    element[estilo] = !element[estilo];
    renderInspector();
    renderBobina();
}

function actualizarDesdeInspector() {
    if (elementoSeleccionadoIdx === null) return;
    const element = plantillaActual.elementos_json[elementoSeleccionadoIdx];
    element.pos_x_mm = parseFloat(el('insp-x').value) || 0;
    element.pos_y_mm = parseFloat(el('insp-y').value) || 0;
    element.ancho_mm = parseFloat(el('insp-w').value) || 2;
    element.alto_mm = parseFloat(el('insp-h').value) || 2;
    updateElementPositionOnCanvas(elementoSeleccionadoIdx);
}

function updProp(prop, value) {
    if (elementoSeleccionadoIdx === null) return;
    plantillaActual.elementos_json[elementoSeleccionadoIdx][prop] = value;
    renderBobina();
    renderLayersList();
}

function reemplazarImagen() {
    pendingImageElement = null;
    const idx = elementoSeleccionadoIdx;
    const input = el('file-input-image');
    if (!input) return;
    const handler = (e) => {
        const file = e.target.files[0];
        if (!file) return;
        const reader = new FileReader();
        reader.onload = (ev) => { plantillaActual.elementos_json[idx].imagen_data = ev.target.result; renderBobina(); };
        reader.readAsDataURL(file);
        input.removeEventListener('change', handler);
        input.value = '';
    };
    input.addEventListener('change', handler);
    input.click();
}

function renderizarListaElementosToggle() {
    const c = el('elementos-lista');
    if (!c) return;
    c.innerHTML = camposDisponibles.map(cmp => {
        const activo = plantillaActual.elementos_json.some(e => e.tipo === 'db' && e.campo_id === cmp.id);
        return `<div class="element-toggle-item"><span>${cmp.label}</span><input type="checkbox" ${activo ? 'checked' : ''} onchange="window._dgToggleCampoBD('${cmp.id}', this.checked)"></div>`;
    }).join('');
}

function toggleCampoBD(campoId, activo) {
    if (activo) {
        const existing = plantillaActual.elementos_json.filter(e => e.tipo === 'db').length;
        plantillaActual.elementos_json.push({ tipo: 'db', campo_id: campoId, pos_x_mm: 5, pos_y_mm: 5 + (existing * 10), ancho_mm: 80, alto_mm: 10, tamano_fuente_pt: 10, negrita: false, alineacion: 'left' });
    } else {
        plantillaActual.elementos_json = plantillaActual.elementos_json.filter(e => !(e.tipo === 'db' && e.campo_id === campoId));
    }
    renderizarListaElementosToggle();
    renderBobina();
}

function sincronizarFormularioAModelo() {
    if (!plantillaActual) return;
    plantillaActual.nombre = el('tpl-nombre').value;
    plantillaActual.ancho_mm = parseFloat(el('tpl-ancho').value);
    plantillaActual.alto_mm = parseFloat(el('tpl-alto').value);
    plantillaActual.margen_sup_mm = parseFloat(el('tpl-margen-sup').value);
    plantillaActual.margen_izq_mm = parseFloat(el('tpl-margen-izq').value);
    plantillaActual.cols = parseInt(el('tpl-cols').value);
    plantillaActual.rows = parseInt(el('tpl-rows').value);
    plantillaActual.gap_h_mm = parseFloat(el('tpl-gap-h').value);
    plantillaActual.gap_v_mm = parseFloat(el('tpl-gap-v').value);
    plantillaActual.tipo_origen = el('tpl-origen').value;
}

function clonarPlantilla(plantilla, nuevoNombre) {
    const clone = JSON.parse(JSON.stringify(plantilla));
    clone.id = 'tpl-' + Date.now();
    clone.nombre = nuevoNombre;
    clone.es_sistema = false;
    return clone;
}

function seleccionarPlantilla(id) {
    plantillaActual = plantillasCargadas.find(t => t.id === id);
    if (!plantillaActual) return;
    el('tpl-nombre').value = plantillaActual.nombre;
    el('tpl-ancho').value = plantillaActual.ancho_mm;
    el('tpl-alto').value = plantillaActual.alto_mm;
    el('tpl-margen-sup').value = plantillaActual.margen_sup_mm || 4;
    el('tpl-margen-izq').value = plantillaActual.margen_izq_mm || 4;
    el('tpl-cols').value = plantillaActual.cols || 1;
    el('tpl-rows').value = plantillaActual.rows || 1;
    el('tpl-gap-h').value = plantillaActual.gap_h_mm || 2;
    el('tpl-gap-v').value = plantillaActual.gap_v_mm || 2;
    el('tpl-origen').value = plantillaActual.tipo_origen || 'GENERAL';
    if (!plantillaActual.elementos_json) plantillaActual.elementos_json = [];
    elementoSeleccionadoIdx = null;
    const insp = el('inspector-elemento');
    if (insp) insp.style.display = 'none';
    renderizarListaElementosToggle();
    renderBobina();
    actualizarEstadoBotonesPlantilla();
}

/** D-273: en plantillas del sistema se deshabilita "Eliminar" (no son borrables). */
function actualizarEstadoBotonesPlantilla() {
    const btnDel = el('btn-eliminar-plantilla');
    if (!btnDel) return;
    const esSistema = esPlantillaSistema(plantillaActual);
    btnDel.disabled = esSistema;
    btnDel.title = esSistema ? 'Las plantillas predeterminadas del sistema no se pueden eliminar' : '';
    btnDel.style.opacity = esSistema ? '0.45' : '';
    btnDel.style.cursor = esSistema ? 'not-allowed' : '';
}

function addShape(shapeType) {
    if (!plantillaActual) return;
    let element;
    if (shapeType === 'rect') {
        element = { tipo: 'rect', pos_x_mm: 5, pos_y_mm: 5, ancho_mm: 30, alto_mm: 20, borde_color: '#025C65', borde_grosor: 1, relleno_color: 'transparent', radio_borde: 0 };
    } else if (shapeType === 'circle') {
        element = { tipo: 'circle', pos_x_mm: 5, pos_y_mm: 5, ancho_mm: 20, alto_mm: 20, borde_color: '#025C65', borde_grosor: 1, relleno_color: 'transparent' };
    } else if (shapeType === 'line') {
        element = { tipo: 'line', pos_x_mm: 5, pos_y_mm: 15, ancho_mm: 80, alto_mm: 0, borde_color: '#025C65', borde_grosor: 1, estilo_linea: 'solid' };
    } else if (shapeType === 'image') {
        const input = el('file-input-image');
        if (input) input.click();
        pendingImageElement = { tipo: 'image', pos_x_mm: 5, pos_y_mm: 5, ancho_mm: 25, alto_mm: 25, imagen_data: '', mantener_proporcion: true };
        return;
    } else if (shapeType === 'text') {
        element = { tipo: 'text', pos_x_mm: 5, pos_y_mm: 5, ancho_mm: 60, alto_mm: 8, texto: 'Texto libre', tamano_fuente_pt: 10, negrita: false, alineacion: 'left', color: '#2C3E50' };
    }
    if (element) { plantillaActual.elementos_json.push(element); renderBobina(); }
}

function crearNuevaPlantilla() {
    plantillaActual = {
        id: 'tpl-' + Date.now(), nombre: 'Nueva Plantilla', ancho_mm: 50, alto_mm: 80,
        margen_sup_mm: 1.30, margen_izq_mm: 1.00, cols: 2, rows: 1, gap_h_mm: 3.00, gap_v_mm: 3.00,
        tipo_origen: 'GENERAL', es_sistema: false,
        elementos_json: [
            { tipo: 'db', campo_id: 'NOMBRE_CIENTIFICO', pos_x_mm: 3, pos_y_mm: 5, ancho_mm: 44, alto_mm: 10, tamano_fuente_pt: 12, negrita: true, alineacion: 'left' },
            { tipo: 'db', campo_id: 'CODIGO_EAN13_BARRAS', pos_x_mm: 3, pos_y_mm: 54, ancho_mm: 44, alto_mm: 22, tamano_fuente_pt: 10 },
        ],
    };
    plantillasCargadas.push(plantillaActual);
    actualizarSelectPlantillas(plantillaActual.id);
    seleccionarPlantilla(plantillaActual.id);
    persistirPlantillasLocal();
}

async function cargarPlantillas() {
    const currentId = plantillaActual ? plantillaActual.id : null;
    const tpl9992Default = obtenerPlantilla9992();
    let remote = [];
    let servidorDisponible = true;
    try {
        remote = (await dc.fetchPlantillas()) || [];
    } catch (e) {
        servidorDisponible = false;
        console.warn('Servidor remoto no disponible, usando locales y sistema');
    }

    let lista;
    if (servidorDisponible) {
        // La base de datos es la fuente de verdad: NO se mezclan copias locales,
        // o las plantillas borradas reaparecerían al recargar (D-273).
        const mapa = new Map();
        mapa.set(tpl9992Default.id, tpl9992Default);
        remote.forEach(t => mapa.set(t.id, t));
        lista = Array.from(mapa.values());
        // Reconstruir la caché local SOLO con lo que existe en BD.
        persistirPlantillasLocalCache(lista);
    } else {
        const locales = cargarPlantillasLocales();
        const mapa = new Map();
        mapa.set(tpl9992Default.id, tpl9992Default);
        locales.forEach(t => mapa.set(t.id, t));
        lista = Array.from(mapa.values());
    }

    plantillasCargadas = lista;
    actualizarSelectPlantillas();
    const target = (currentId && plantillasCargadas.find(t => t.id === currentId)) 
        || plantillasCargadas.find(t => t.id === tpl9992Default.id) 
        || plantillasCargadas[0];
    if (target) seleccionarPlantilla(target.id);
}

function showToast(msg) {
    let c = el('toast-container');
    if (!c) return;
    const t = document.createElement('div'); t.className = 'toast'; t.innerText = msg;
    c.appendChild(t); setTimeout(() => t.remove(), 4000);
}

function abrirModal(titulo, valorPorDefecto, callback) {
    el('modal-titulo').textContent = titulo;
    el('modal-input').value = valorPorDefecto || '';
    modalCallback = callback;
    el('modal-nombre').classList.add('visible');
    el('modal-input').focus();
    el('modal-input').select();
}

function cerrarModal() {
    el('modal-nombre').classList.remove('visible');
    modalCallback = null;
}

function confirmarModal() {
    const valor = el('modal-input').value.trim();
    if (valor && modalCallback) modalCallback(valor);
    cerrarModal();
}

function duplicarPlantilla() {
    if (!plantillaActual) return;
    abrirModal('Duplicar Plantilla', plantillaActual.nombre + ' (copia)', async (nombre) => {
        const clone = clonarPlantilla(plantillaActual, nombre);
        plantillasCargadas.push(clone);
        actualizarSelectPlantillas(clone.id);
        persistirPlantillasLocal();
        try {
            const res = await dc.guardarPlantilla(clone);
            if (res && res.plantilla) {
                const rIdx = plantillasCargadas.findIndex(t => t.id === res.plantilla.id);
                if (rIdx >= 0) plantillasCargadas[rIdx] = res.plantilla;
                else plantillasCargadas.push(res.plantilla);
                actualizarSelectPlantillas(res.plantilla.id);
                persistirPlantillasLocal();
            }
            showToast('Plantilla duplicada en la base de datos: ' + nombre);
        } catch (e) {
            showToast('Plantilla duplicada solo localmente (servidor no disponible).');
        }
    });
}

function guardarComoNueva() {
    if (!plantillaActual) return;
    sincronizarFormularioAModelo();
    abrirModal('Guardar Como Nueva', plantillaActual.nombre + ' - Nueva', async (nombre) => {
        const clone = clonarPlantilla(plantillaActual, nombre);
        plantillasCargadas.push(clone);
        actualizarSelectPlantillas(clone.id);
        persistirPlantillasLocal();
        try {
            const res = await dc.guardarPlantilla(clone);
            if (res && res.plantilla) {
                const rIdx = plantillasCargadas.findIndex(t => t.id === res.plantilla.id);
                if (rIdx >= 0) plantillasCargadas[rIdx] = res.plantilla;
                else plantillasCargadas.push(res.plantilla);
                actualizarSelectPlantillas(res.plantilla.id);
                persistirPlantillasLocal();
            }
            showToast('Guardada en la base de datos como: ' + nombre);
        } catch (e) {
            showToast('Guardada solo localmente como: ' + nombre + ' (servidor no disponible).');
        }
    });
}

function renombrarPlantilla() {
    if (!plantillaActual) return;
    if (esPlantillaSistema(plantillaActual)) {
        showToast('Las plantillas predeterminadas del sistema no se pueden renombrar.');
        return;
    }
    abrirModal('Renombrar Plantilla', plantillaActual.nombre, async (nombre) => {
        plantillaActual.nombre = nombre;
        actualizarSelectPlantillas(plantillaActual.id);
        persistirPlantillasLocal();
        try {
            const res = await dc.guardarPlantilla(plantillaActual);
            if (res && res.plantilla) {
                const rIdx = plantillasCargadas.findIndex(t => t.id === res.plantilla.id);
                if (rIdx >= 0) plantillasCargadas[rIdx] = res.plantilla;
                plantillaActual = res.plantilla;
                actualizarSelectPlantillas(res.plantilla.id);
                persistirPlantillasLocal();
            }
            showToast('Renombrada en la base de datos a: ' + nombre);
        } catch (e) {
            showToast('Renombrada solo localmente a: ' + nombre + ' (servidor no disponible).');
        }
    });
}

async function eliminarPlantilla() {
    if (!plantillaActual) return;
    if (esPlantillaSistema(plantillaActual)) {
        showToast('Las plantillas predeterminadas del sistema no se pueden eliminar; utiliza "Guardar como nueva" para crear una versión personalizable.');
        return;
    }
    if (!confirm('¿Eliminar plantilla "' + plantillaActual.nombre + '"?')) return;
    const idEliminar = plantillaActual.id;
    try {
        await dc.eliminarPlantilla(idEliminar);
    } catch (e) {
        const msg = String((e && e.message) || '');
        if (msg.includes('403')) {
            showToast('Las plantillas predeterminadas del sistema no se pueden eliminar; utiliza "Guardar como nueva" para crear una versión personalizable.');
            return;
        }
        // 404 = la plantilla no existe en el servidor (solo local) → se borra igualmente.
        if (!msg.includes('404')) {
            showToast('No se pudo eliminar en el servidor. La plantilla no se ha borrado.');
            return;
        }
    }
    const idx = plantillasCargadas.findIndex(t => t.id === idEliminar);
    if (idx >= 0) plantillasCargadas.splice(idx, 1);
    plantillaActual = null;
    elementoSeleccionadoIdx = null;
    const insp = el('inspector-elemento');
    if (insp) insp.style.display = 'none';
    actualizarSelectPlantillas();
    persistirPlantillasLocal();
    if (plantillasCargadas.length > 0) {
        seleccionarPlantilla(plantillasCargadas[0].id);
    } else {
        crearNuevaPlantilla();
    }
    showToast('Plantilla eliminada correctamente (base de datos).');
}

async function guardarPlantilla() {
    if (!plantillaActual) return;
    // Captura el estado COMPLETO del canvas: dimensiones, bobina, campos BD,
    // elementos (incluidas imágenes base64) y origen.
    sincronizarFormularioAModelo();

    if (esPlantillaSistema(plantillaActual)) {
        showToast('Las plantillas predeterminadas del sistema no se pueden sobrescribir; utiliza "Guardar como nueva" para crear una versión personalizable.');
        return;
    }

    const idx = plantillasCargadas.findIndex(t => t.id === plantillaActual.id);
    if (idx >= 0) {
        plantillasCargadas[idx] = JSON.parse(JSON.stringify(plantillaActual));
    } else {
        plantillasCargadas.push(JSON.parse(JSON.stringify(plantillaActual)));
    }
    persistirPlantillasLocal();

    try {
        const res = await dc.guardarPlantilla(plantillaActual);
        if (res && res.plantilla) {
            const rIdx = plantillasCargadas.findIndex(t => t.id === res.plantilla.id);
            if (rIdx >= 0) plantillasCargadas[rIdx] = res.plantilla;
            else plantillasCargadas.push(res.plantilla);
            plantillaActual = res.plantilla;
            actualizarSelectPlantillas(plantillaActual.id);
            persistirPlantillasLocal();
        }
        showToast('Plantilla guardada en la base de datos.');
    } catch (e) {
        console.error('Error al guardar plantilla en servidor:', e);
        showToast('No se pudo guardar en el servidor: la plantilla solo quedó guardada localmente.');
    }
}

async function probarArticuloReal() {
    if (!plantillaActual) { showToast('No hay plantilla seleccionada'); return; }
    try {
        const data = await dc.fetchRenderEjemplo();
        const reales = (data.articulos_reales || []).map(a => ({
            nombre: a.nombre || a.ID_ARTICULO || 'Artículo Real (API)',
            NOMBRE_CIENTIFICO: a.NOMBRE_CIENTIFICO || '',
            VARIEDAD_FORMACION: a.VARIEDAD_FORMACION || a.DESCRIPCION_ARTICULO || '',
            CONTENEDOR: a.CONTENEDOR || a.DESCRIPCION_LITRAJE || '',
            GGN: a.GGN || a.GLOBALGAP || '',
            UBICACION_SECTOR: a.UBICACION_SECTOR || a.UBICACIONES_FINCAS || '',
            CODIGO_EAN13_BARRAS: a.CODIGO_EAN13_BARRAS || a.CODIGO_EAN || '',
            CODIGO_LOTE: a.CODIGO_LOTE || a.codigo_lote || a.ID_ARTICULO || a.ref_factusol || '',
            CODIGO_QR: '',
            TEXTO_LIBRE: a.ID_ARTICULO || '',
        }));
        if (!reales.length) {
            articulosPrueba = [];
            showToast('No se pudo obtener ningún artículo real activo.');
            return;
        }
        articulosPrueba = reales;
        articuloActualIdx = 0;
        mostrarPreviewArticulo();
        showToast('Datos reales cargados desde BigQuery EU');
        return;
    } catch (e) {
        articulosPrueba = [];
        showToast('Endpoint no disponible. No hay artículos de prueba.');
    }
}

function mostrarPreviewArticulo() {
    const articulo = articulosPrueba[articuloActualIdx];
    if (!articulo) { showToast('No hay artículo para previsualizar.'); return; }
    const previewLabel = el('preview-label-content');
    const previewData = el('preview-data-fields');

    let labelHtml = '';
    plantillaActual.elementos_json.forEach(element => {
        const px = element.pos_x_mm * SCALE, py = element.pos_y_mm * SCALE;
        const pw = element.ancho_mm * SCALE, ph = element.alto_mm * SCALE;
        let style = `left:${px}px; top:${py}px; width:${pw}px; height:${ph}px;`;
        let content = '';
        let esVacio = false;

        const fuente = element.fuente || 'Arial';
        const tamano = element.tamano_fuente_pt || 10;
        const negrita = element.negrita ? 'bold' : 'normal';
        const cursiva = element.cursiva ? 'italic' : 'normal';
        const subrayado = element.subrayado ? 'underline' : 'none';
        const alineacion = element.alineacion || 'left';

        if (element.tipo === 'db') {
            const valor = articulo[element.campo_id] || '';
            esVacio = !valor;
            if (!esVacio) {
                if (element.campo_id === 'CODIGO_EAN13_BARRAS') {
                    content = `<div style="width:100%; height:100%; display:flex; align-items:center; justify-content:center; overflow:visible;">${generarBarcodeSVG(valor, element.ancho_mm, element.alto_mm)}</div>`;
                } else {
                    const textoCompleto = (element.prefijo || '') + valor + (element.sufijo || '');
                    style += ` font-family:${fuente}; font-size:${tamano}px; font-weight:${negrita}; font-style:${cursiva}; text-decoration:${subrayado}; text-align:${alineacion}; color:${element.color || '#2C3E50'};`;
                    content = `<span style="display:block; width:100%; height:100%; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;">${textoCompleto}</span>`;
                }
            }
        } else if (element.tipo === 'text') {
            style += ` font-family:${fuente}; font-size:${tamano}px; font-weight:${negrita}; font-style:${cursiva}; text-decoration:${subrayado}; text-align:${alineacion}; color:${element.color || '#2C3E50'};`;
            content = `<span style="overflow:hidden; text-overflow:ellipsis; white-space:nowrap; width:100%; display:block;">${element.texto || ''}</span>`;
        } else if (element.tipo === 'rect') {
            style += ` border:${element.borde_grosor || 1}px ${element.estilo_linea === 'dashed' ? 'dashed' : 'solid'} ${element.borde_color || '#025C65'}; background:${element.relleno_color || 'transparent'}; border-radius:${element.radio_borde || 0}px;`;
        } else if (element.tipo === 'circle') {
            style += ` border:${element.borde_grosor || 1}px solid ${element.borde_color || '#025C65'}; background:${element.relleno_color || 'transparent'}; border-radius:50%;`;
        } else if (element.tipo === 'line') {
            const isH = element.ancho_mm > element.alto_mm;
            style += ` width:${(isH ? element.ancho_mm : (element.borde_grosor || 1)) * SCALE}px; height:${(isH ? (element.borde_grosor || 1) : element.alto_mm) * SCALE}px; border-top:2px solid ${element.borde_color || '#025C65'};`;
        } else if (element.tipo === 'image') {
            const src = element.imagen_data || (element.fallback === 'bandera_ue' ? BANDERA_UE_SVG : '');
            if (src) { content = `<img src="${src}" style="width:100%; height:100%; object-fit:contain;">`; }
            else { content = '<i class="fa-solid fa-image" style="font-size:16px; color:#94a3b8;"></i>'; }
        }

        if (!esVacio) labelHtml += `<div class="preview-element" style="${style}">${content}</div>`;
    });
    previewLabel.innerHTML = labelHtml;

    let dataHtml = `<h4>Datos del Artículo: ${articulo.nombre}</h4>`;
    Object.keys(articulo).forEach(key => {
        if (key === 'nombre') return;
        const valor = articulo[key];
        const label = getCampoLabel(key);
        const esVacio = !valor;
        dataHtml += `<div class="field"><span class="field-label">${label}:</span>`;
        dataHtml += esVacio ? `<span class="field-value field-empty">(vacío - elemento oculto)</span>` : `<span class="field-value">${valor}</span>`;
        dataHtml += '</div>';
    });
    previewData.innerHTML = dataHtml;
    el('modal-preview').classList.add('visible');
}

function cambiarArticuloPreview() {
    articuloActualIdx = (articuloActualIdx + 1) % articulosPrueba.length;
    mostrarPreviewArticulo();
}

function cerrarPreview() {
    el('modal-preview').classList.remove('visible');
}

function handleImageUpload(event) {
    const file = event.target.files[0];
    if (!file || !pendingImageElement) return;
    const reader = new FileReader();
    reader.onload = (e) => { pendingImageElement.imagen_data = e.target.result; plantillaActual.elementos_json.push(pendingImageElement); pendingImageElement = null; renderBobina(); };
    reader.readAsDataURL(file);
    event.target.value = '';
}

function bindGlobalHandlers() {
    window._dgSeleccionarElemento = seleccionarElemento;
    window._dgEliminarElemento = eliminarElemento;
    window._dgActualizarDesdeInspector = actualizarDesdeInspector;
    window._dgUpdProp = updProp;
    window._dgToggleEstilo = toggleEstilo;
    window._dgToggleCampoBD = toggleCampoBD;
    window._dgReemplazarImagen = reemplazarImagen;
}

function bindDocumentEvents() {
    const onMove = (e) => {
        const coordEl = el('coord-display');
        if (plantillaActual) {
            const activeSlot = rootEl.querySelector('.label-slot.active');
            if (activeSlot) {
                const rect = activeSlot.getBoundingClientRect();
                coordEl.textContent = `X: ${((e.clientX - rect.left) / SCALE).toFixed(1)} mm | Y: ${((e.clientY - rect.top) / SCALE).toFixed(1)} mm`;
            }
        }
        if (!dragState || !plantillaActual) return;
        const dxMm = (e.clientX - dragState.startX) / SCALE;
        const dyMm = (e.clientY - dragState.startY) / SCALE;
        const element = plantillaActual.elementos_json[dragState.idx];
        const dim = getLabelDimensions();

        if (dragState.type === 'move') {
            element.pos_x_mm = Math.round(Math.max(0, Math.min(dim.ancho - element.ancho_mm, dragState.origX + dxMm)) * 10) / 10;
            element.pos_y_mm = Math.round(Math.max(0, Math.min(dim.alto - element.alto_mm, dragState.origY + dyMm)) * 10) / 10;
            if (Math.abs(element.pos_x_mm) < SNAP) element.pos_x_mm = 0;
            if (Math.abs(element.pos_y_mm) < SNAP) element.pos_y_mm = 0;
            if (Math.abs(dim.ancho - (element.pos_x_mm + element.ancho_mm)) < SNAP) element.pos_x_mm = dim.ancho - element.ancho_mm;
            if (Math.abs(dim.alto - (element.pos_y_mm + element.alto_mm)) < SNAP) element.pos_y_mm = dim.alto - element.alto_mm;
            updateInspectorFromModel();
            updateElementPositionOnCanvas(dragState.idx);
        } else if (dragState.type === 'resize') {
            const c = dragState.corner;
            let nX = dragState.origX, nY = dragState.origY, nW = dragState.origW, nH = dragState.origH;
            if (c === 'se') { nW = Math.max(2, dragState.origW + dxMm); nH = Math.max(2, dragState.origH + dyMm); }
            else if (c === 'sw') { nX = dragState.origX + dxMm; nW = Math.max(2, dragState.origW - dxMm); nH = Math.max(2, dragState.origH + dyMm); }
            else if (c === 'ne') { nW = Math.max(2, dragState.origW + dxMm); nY = dragState.origY + dyMm; nH = Math.max(2, dragState.origH - dyMm); }
            else if (c === 'nw') { nX = dragState.origX + dxMm; nW = Math.max(2, dragState.origW - dxMm); nY = dragState.origY + dyMm; nH = Math.max(2, dragState.origH - dyMm); }
            element.pos_x_mm = Math.round(Math.max(0, nX) * 10) / 10;
            element.pos_y_mm = Math.round(Math.max(0, nY) * 10) / 10;
            element.ancho_mm = Math.round(nW * 10) / 10;
            element.alto_mm = Math.round(nH * 10) / 10;
            updateInspectorFromModel();
            renderBobina();
        }
    };
    const onUp = () => { if (dragState) dragState = null; };
    const onKey = (e) => {
        if (elementoSeleccionadoIdx === null || !plantillaActual) return;
        if (e.target.tagName === 'INPUT' || e.target.tagName === 'SELECT' || e.target.tagName === 'TEXTAREA') return;
        const element = plantillaActual.elementos_json[elementoSeleccionadoIdx];
        const step = 0.5;
        let handled = true;
        if (e.key === 'ArrowLeft') element.pos_x_mm = Math.max(0, element.pos_x_mm - step);
        else if (e.key === 'ArrowRight') element.pos_x_mm += step;
        else if (e.key === 'ArrowUp') element.pos_y_mm = Math.max(0, element.pos_y_mm - step);
        else if (e.key === 'ArrowDown') element.pos_y_mm += step;
        else if (e.key === 'Delete' || e.key === 'Backspace') { eliminarElemento(elementoSeleccionadoIdx); return; }
        else handled = false;
        if (handled) { e.preventDefault(); updateInspectorFromModel(); updateElementPositionOnCanvas(elementoSeleccionadoIdx); }
    };
    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', onUp);
    document.addEventListener('keydown', onKey);
    const modalInput = el('modal-input');
    if (modalInput) {
        modalInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') confirmarModal();
            if (e.key === 'Escape') cerrarModal();
        });
    }
}

const DESIGNER_HTML = `
<div class="designer-root">
<aside>
    <div class="header-brand"><i class="fa-solid fa-pen-ruler fa-lg"></i><h2>Diseñador Industrial de Etiquetas</h2></div>
    <details class="accordion">
        <summary><i class="fa-solid fa-folder-open" style="font-size:11px;"></i> Plantilla
            <button class="help-btn" onclick="event.preventDefault(); event.stopPropagation(); toggleTooltip(this)">i<div class="tooltip-card">Selecciona una plantilla existente o crea una nueva. Cada plantilla define el diseño completo de una etiqueta con sus dimensiones, campos y formas.</div></button>
        </summary>
        <div class="accordion-body">
            <div class="row-2">
                <select id="select-plantilla" onchange="cargarPlantillaSeleccionada()"><option value="">Cargando...</option></select>
                <button class="btn btn-secondary btn-sm" onclick="crearNuevaPlantillaPortal()" title="Nueva plantilla vacía"><i class="fa-solid fa-plus"></i></button>
            </div>
            <label>Nombre</label><input type="text" id="tpl-nombre" placeholder="Ej: Pasaporte 100x50">
            <div class="tpl-actions">
                <button class="btn btn-secondary" onclick="duplicarPlantillaPortal()"><i class="fa-solid fa-copy"></i> Duplicar</button>
                <button class="btn btn-secondary" onclick="guardarComoNuevaPortal()"><i class="fa-solid fa-clone"></i> Guardar como nueva</button>
                <button class="btn btn-secondary" onclick="renombrarPlantillaPortal()"><i class="fa-solid fa-pen"></i> Renombrar</button>
                <button class="btn btn-danger" id="btn-eliminar-plantilla" onclick="eliminarPlantillaPortal()"><i class="fa-solid fa-trash"></i> Eliminar</button>
            </div>
        </div>
    </details>
    <details class="accordion">
        <summary><i class="fa-solid fa-ruler-combined" style="font-size:11px;"></i> Dimensiones Etiqueta (mm)
            <button class="help-btn" onclick="event.preventDefault(); event.stopPropagation(); toggleTooltip(this)">i<div class="tooltip-card"><strong>Ancho y Alto:</strong> Tamaño físico de una sola etiqueta en milímetros.<br><strong>Margen Sup/Izq:</strong> Distancia desde el borde del papel hasta la primera etiqueta.</div></button>
        </summary>
        <div class="accordion-body">
            <div class="row-2">
                <div><label>Ancho<label class="field-help" tabindex="0">i<div class="tooltip-card">Ancho físico de cada etiqueta en milímetros.</div></label></label><input type="number" id="tpl-ancho" value="50" onchange="renderBobinaPortal()"></div>
                <div><label>Alto</label><input type="number" id="tpl-alto" value="80" onchange="renderBobinaPortal()"></div>
            </div>
            <div class="row-2">
                <div><label>Margen Sup.<label class="field-help" tabindex="0">i<div class="tooltip-card">Separación desde el borde superior del papel continuo hasta el inicio de la primera etiqueta.</div></label></label><input type="number" id="tpl-margen-sup" value="1.30" step="0.05" onchange="renderBobinaPortal()"></div>
                <div><label>Margen Izq.<label class="field-help" tabindex="0">i<div class="tooltip-card">Separación desde el borde izquierdo del papel continuo hasta el inicio de la primera etiqueta.</div></label></label><input type="number" id="tpl-margen-izq" value="1.00" step="0.05" onchange="renderBobinaPortal()"></div>
            </div>
        </div>
    </details>
    <details class="accordion">
        <summary><i class="fa-solid fa-grip" style="font-size:11px;"></i> Bobina / Maquetación
            <button class="help-btn" onclick="event.preventDefault(); event.stopPropagation(); toggleTooltip(this)">i<div class="tooltip-card">Configura la distribución de etiquetas en el rollo de papel.<br><strong>Nº Ancho:</strong> Etiquetas por fila (ej: 2 para bobinas multipista).<br><strong>Gap:</strong> Distancia en mm entre etiquetas contiguas.</div></button>
        </summary>
        <div class="accordion-body">
            <div class="row-2">
                <div><label>Nº Ancho<label class="field-help" tabindex="0">i<div class="tooltip-card">Cantidad de etiquetas por fila en la bobina. Ej: 2 para bobinas multipista.</div></label></label><input type="number" id="tpl-cols" value="2" min="1" onchange="renderBobinaPortal()"></div>
                <div><label>Nº Alto<label class="field-help" tabindex="0">i<div class="tooltip-card">Cantidad de etiquetas por columna (en la dirección de avance del papel).</div></label></label><input type="number" id="tpl-rows" value="1" min="1" onchange="renderBobinaPortal()"></div>
            </div>
            <div class="row-2">
                <div><label>Gap Horiz. (mm)<label class="field-help" tabindex="0">i<div class="tooltip-card">Distancia en milímetros entre etiquetas contiguas en la misma fila del rollo de papel.</div></label></label><input type="number" id="tpl-gap-h" value="3.00" step="0.05" onchange="renderBobinaPortal()"></div>
                <div><label>Gap Vert. (mm)<label class="field-help" tabindex="0">i<div class="tooltip-card">Distancia en milímetros entre filas de etiquetas en la dirección de avance del papel.</div></label></label><input type="number" id="tpl-gap-v" value="3.00" step="0.05" onchange="renderBobinaPortal()"></div>
            </div>
            <label>Vinculación a Informe</label>
            <select id="tpl-origen">
                <option value="GENERAL">General / Libre</option>
                <option value="INVENTARIO">Informe Inventario</option>
                <option value="PICKING">Informe Picking</option>
            </select>
        </div>
    </details>
    <details class="accordion" open>
        <summary><i class="fa-solid fa-database" style="font-size:11px;"></i> Campos BD Vivero
            <button class="help-btn" onclick="event.preventDefault(); event.stopPropagation(); toggleTooltip(this)">i<div class="tooltip-card">Activa los campos de la base de datos de Viveros Elche que quieras incluir en la etiqueta. Cada campo se mapea directamente a una columna real de BigQuery.</div></button>
        </summary>
        <div class="accordion-body"><div id="elementos-lista"></div></div>
    </details>
    <details class="accordion">
        <summary><i class="fa-solid fa-shapes" style="font-size:11px;"></i> Formas / Herramientas
            <button class="help-btn" onclick="event.preventDefault(); event.stopPropagation(); toggleTooltip(this)">i<div class="tooltip-card">Añade elementos visuales decorativos a la etiqueta: rectángulos, círculos, líneas, imágenes (logos) o texto libre. Todos son arrastrables y redimensionables.</div></button>
        </summary>
        <div class="accordion-body">
            <div class="toolbar-shapes">
                <button onclick="addShapePortal('rect')" title="Rectángulo"><i class="fa-solid fa-square"></i><span>Rect</span></button>
                <button onclick="addShapePortal('circle')" title="Círculo"><i class="fa-solid fa-circle"></i><span>Círc</span></button>
                <button onclick="addShapePortal('line')" title="Línea"><i class="fa-solid fa-minus"></i><span>Línea</span></button>
                <button onclick="addShapePortal('image')" title="Imagen / Logo"><i class="fa-solid fa-image"></i><span>Img</span></button>
                <button onclick="addShapePortal('text')" title="Texto libre"><i class="fa-solid fa-font"></i><span>Texto</span></button>
            </div>
        </div>
    </details>
    <details class="accordion">
        <summary><i class="fa-solid fa-layer-group" style="font-size:11px;"></i> Capas del Diseño
            <button class="help-btn" onclick="event.preventDefault(); event.stopPropagation(); toggleTooltip(this)">i<div class="tooltip-card">Lista de todos los elementos de la etiqueta. Haz clic para seleccionar, usa el icono de papelera para eliminar. El orden de la lista refleja el orden de renderizado.</div></button>
        </summary>
        <div class="accordion-body"><div id="layers-list"></div></div>
    </details>
    <details class="accordion" id="inspector-elemento" style="display:none;" open>
        <summary><i class="fa-solid fa-sliders" style="font-size:11px;"></i> Inspector de Propiedades</summary>
        <div class="accordion-body"><div id="inspector-content"></div></div>
    </details>
    <div class="save-bar">
        <button class="btn" onclick="guardarPlantillaPortal()"><i class="fa-solid fa-floppy-disk"></i> Guardar Plantilla</button>
        <button class="btn btn-secondary" onclick="probarArticuloRealPortal()"><i class="fa-solid fa-vial"></i> Probar Artículo Real</button>
    </div>
</aside>
<main class="designer-main">
    <div class="canvas-toolbar">
        <h3 id="canvas-title">Vista Previa Bobina WYSIWYG</h3>
    </div>
    <div class="canvas-body">
        <div id="bobina-canvas"></div>
        <div class="coord-display" id="coord-display">X: 0.0 mm | Y: 0.0 mm</div>
    </div>
</main>
<div id="toast-container"></div>
<input type="file" id="file-input-image" accept="image/*" style="display:none" onchange="handleImageUploadPortal(event)">
<div class="modal-overlay" id="modal-nombre">
    <div class="modal-box">
        <h3 id="modal-titulo">Nombre de Plantilla</h3>
        <label>Introduce el nombre:</label>
        <input type="text" id="modal-input" placeholder="Ej: 9992 - Modificada Cliente X">
        <div class="modal-actions">
            <button class="btn btn-secondary" onclick="cerrarModalPortal()">Cancelar</button>
            <button class="btn" id="modal-confirmar" onclick="confirmarModalPortal()">Aceptar</button>
        </div>
    </div>
</div>
<div class="preview-modal" id="modal-preview">
    <div class="preview-box">
        <h3><i class="fa-solid fa-vial"></i> Vista Previa - Artículo Real</h3>
        <div class="preview-label-container"><div class="preview-label" id="preview-label-content"></div></div>
        <div class="preview-data" id="preview-data-fields"></div>
        <div class="modal-actions">
            <button class="btn btn-secondary" onclick="cerrarPreviewPortal()">Cerrar</button>
            <button class="btn" onclick="cambiarArticuloPreviewPortal()"><i class="fa-solid fa-shuffle"></i> Cambiar Artículo</button>
        </div>
    </div>
</div>
</div>`;

const DESIGNER_CSS = `
.designer-root{--primary:#025C65;--primary-dark:#014147;--primary-light:rgba(2,92,101,0.08);--danger:#962622;--bg-main:#F4F7F6;--surface:#FFFFFF;--text-main:#2C3E50;--text-muted:#7F8C8D;--border:#E2E8F0;--radius:8px;--shadow:0 4px 6px -1px rgba(0,0,0,0.1);display:flex;background:var(--bg-main);color:var(--text-main);min-height:calc(100vh - 120px);height:calc(100vh - 120px);overflow:hidden;font-family:'Inter',system-ui,sans-serif;box-sizing:border-box;}
.designer-root *{box-sizing:border-box;margin:0;padding:0;}
.designer-root aside{width:350px;min-width:350px;background:var(--surface);border-right:1px solid var(--border);display:flex;flex-direction:column;height:100%;max-height:calc(100vh - 120px);overflow-y:auto;overflow-x:hidden;padding:14px;padding-bottom:20px;z-index:10;flex-shrink:0;}
.designer-root .header-brand{display:flex;align-items:center;gap:10px;margin-bottom:12px;color:var(--primary);}
.designer-root .header-brand h2{font-size:14px;font-weight:700;}
.designer-root details.accordion{margin-bottom:6px;border:1px solid var(--border);border-radius:var(--radius);overflow:visible;flex-shrink:0;}
.designer-root details.accordion>summary{display:flex;align-items:center;gap:6px;padding:8px 12px;font-size:12px;font-weight:600;color:var(--primary);cursor:pointer;background:#f8fafc;user-select:none;list-style:none;}
.designer-root details.accordion>summary::-webkit-details-marker{display:none;}
.designer-root details.accordion>summary::before{content:'\\f054';font-family:'Font Awesome 6 Free';font-weight:900;font-size:9px;transition:transform 0.2s;color:var(--text-muted);}
.designer-root details.accordion[open]>summary::before{transform:rotate(90deg);}
.designer-root details.accordion>summary:hover{background:var(--primary-light);}
.designer-root details.accordion>.accordion-body{padding:10px 12px;}
.designer-root #elementos-lista{max-height:220px;overflow-y:auto;overflow-x:hidden;}
.designer-root #layers-list{max-height:200px;overflow-y:auto;overflow-x:hidden;}
.designer-root #inspector-content{max-height:260px;overflow-y:auto;overflow-x:hidden;}
.designer-root details.accordion>summary .help-btn{margin-left:auto;width:18px;height:18px;border-radius:50%;background:var(--border);color:var(--text-muted);border:none;font-size:10px;font-weight:700;cursor:pointer;display:inline-flex;align-items:center;justify-content:center;flex-shrink:0;position:relative;}
.designer-root details.accordion>summary .help-btn:hover{background:var(--primary);color:white;}
.designer-root .tooltip-card{display:none;position:fixed;width:min(260px,80vw);max-width:80vw;background:white;border:1px solid var(--border);border-radius:6px;padding:10px 12px;font-size:11px;font-weight:400;color:var(--text-main);box-shadow:0 8px 20px rgba(0,0,0,0.12);z-index:12000;line-height:1.5;}
.designer-root .tooltip-card.visible{display:block;}
.designer-root .tooltip-card strong{color:var(--primary);}
.designer-root .field-help{display:inline-flex;align-items:center;justify-content:center;width:15px;height:15px;border-radius:50%;background:var(--border);color:var(--text-muted);font-size:9px;font-weight:700;cursor:help;margin-left:4px;position:relative;flex-shrink:0;vertical-align:middle;}
.designer-root .field-help .tooltip-card{width:200px;}
.designer-root .field-help:hover .tooltip-card,.designer-root .field-help:focus .tooltip-card{display:block;}
.designer-root label{display:block;font-size:11px;font-weight:500;margin-bottom:3px;color:var(--text-main);}
.designer-root label .field-help{margin-left:3px;}
.designer-root input,.designer-root select{width:100%;padding:5px 9px;border:1px solid var(--border);border-radius:var(--radius);font-size:12px;margin-bottom:7px;outline:none;background:white;}
.designer-root input:focus,.designer-root select:focus{border-color:var(--primary);}
.designer-root input[type="color"]{padding:2px;height:28px;cursor:pointer;}
.designer-root input[type="checkbox"]{width:auto;margin:0;}
.designer-root .row-2{display:grid;grid-template-columns:1fr 1fr;gap:8px;}
.designer-root .btn{background:var(--primary);color:white;border:none;padding:7px 12px;border-radius:var(--radius);font-weight:500;font-size:12px;cursor:pointer;display:inline-flex;align-items:center;justify-content:center;gap:6px;width:100%;transition:background 0.2s;text-decoration:none;margin-top:4px;}
.designer-root .btn:hover{background:var(--primary-dark);}
.designer-root .btn-danger{background:var(--danger);}
.designer-root .btn-secondary{background:#475569;}
.designer-root .btn-sm{padding:5px 8px;font-size:11px;width:auto;margin:0;}
.designer-root .toolbar-shapes{display:grid;grid-template-columns:repeat(5,1fr);gap:4px;margin-bottom:6px;}
.designer-root .toolbar-shapes button{background:#f1f5f9;border:1px solid var(--border);border-radius:6px;padding:8px 4px;cursor:pointer;font-size:14px;color:var(--text-main);transition:all 0.15s;}
.designer-root .toolbar-shapes button:hover{background:var(--primary-light);border-color:var(--primary);}
.designer-root .toolbar-shapes button span{display:block;font-size:9px;margin-top:2px;}
.designer-root .element-toggle-item{display:flex;align-items:center;justify-content:space-between;background:#f8fafc;padding:5px 9px;border:1px solid var(--border);border-radius:6px;margin-bottom:3px;font-size:11px;gap:6px;}
.designer-root .element-toggle-item span{flex:1;line-height:1.3;}
.designer-root .layer-item{display:flex;align-items:center;gap:6px;padding:4px 8px;border:1px solid var(--border);border-radius:6px;margin-bottom:3px;font-size:11px;cursor:pointer;background:#f8fafc;}
.designer-root .layer-item.active{border-color:var(--primary);background:var(--primary-light);}
.designer-root .layer-item i.l-icon{font-size:12px;color:var(--text-muted);width:16px;text-align:center;}
.designer-root .layer-item .layer-name{flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}
.designer-root .layer-item .layer-del{opacity:0;transition:opacity 0.15s;}
.designer-root .layer-item:hover .layer-del{opacity:1;}
.designer-root .designer-main{flex:1;display:flex;flex-direction:column;overflow:hidden;background:#eef2f3;}
.designer-root .canvas-toolbar{height:48px;background:var(--surface);border-bottom:1px solid var(--border);display:flex;align-items:center;justify-content:space-between;padding:0 20px;}
.designer-root .canvas-toolbar h3{font-size:14px;color:var(--primary);}
.designer-root .canvas-body{flex:1;display:flex;align-items:center;justify-content:center;position:relative;overflow:auto;padding:30px;}
.designer-root #bobina-canvas{position:relative;background:#f0f0f0;border:2px dashed #cbd5e1;transform-origin:center;}
.designer-root .label-slot{position:absolute;background:white;border:1px solid #cbd5e1;box-shadow:0 2px 8px rgba(0,0,0,0.06);}
.designer-root .label-slot.active{border:2px solid var(--primary);box-shadow:0 4px 16px rgba(2,92,101,0.15);}
.designer-root .label-slot.ghost{opacity:0.35;border:1px dashed #94a3b8;}
.designer-root .canvas-element{position:absolute;border:1px dashed rgba(2,92,101,0.4);background:rgba(2,92,101,0.04);padding:2px;font-size:10px;cursor:move;display:flex;align-items:center;overflow:hidden;transition:box-shadow 0.12s,border-color 0.12s;user-select:none;}
.designer-root .canvas-element.selected{border:2px solid var(--primary);background:rgba(2,92,101,0.1);box-shadow:0 3px 10px rgba(2,92,101,0.2);z-index:5;}
.designer-root .canvas-element.shape-rect,.designer-root .canvas-element.shape-circle{border-style:solid;border-color:rgba(2,92,101,0.5);}
.designer-root .canvas-element.shape-circle{border-radius:50%;}
.designer-root .canvas-element.shape-line{background:transparent;border:none;overflow:visible;}
.designer-root .canvas-element.shape-image{border:1px dashed rgba(150,38,34,0.4);}
.designer-root .canvas-element.shape-image img{width:100%;height:100%;object-fit:contain;pointer-events:none;}
.designer-root .resize-handle{position:absolute;width:9px;height:9px;background:var(--primary);border:2px solid white;border-radius:2px;z-index:10;box-shadow:0 1px 3px rgba(0,0,0,0.3);}
.designer-root .resize-handle.nw{top:-5px;left:-5px;cursor:nw-resize;}
.designer-root .resize-handle.ne{top:-5px;right:-5px;cursor:ne-resize;}
.designer-root .resize-handle.sw{bottom:-5px;left:-5px;cursor:sw-resize;}
.designer-root .resize-handle.se{bottom:-5px;right:-5px;cursor:se-resize;}
.designer-root .coord-display{position:absolute;bottom:8px;left:8px;background:rgba(0,0,0,0.7);color:white;padding:4px 10px;border-radius:4px;font-size:11px;z-index:30;}
.designer-root #toast-container{position:fixed;bottom:20px;right:20px;display:flex;flex-direction:column;gap:10px;z-index:9999;}
.designer-root .toast{background:white;border-left:4px solid var(--primary);padding:12px 16px;box-shadow:var(--shadow);border-radius:4px;font-size:12px;}
.designer-root .save-bar{padding:8px 0 0;border-top:1px solid var(--border);margin-top:6px;}
.designer-root .modal-overlay{display:none;position:fixed;inset:0;background:rgba(0,0,0,0.4);z-index:9000;align-items:center;justify-content:center;}
.designer-root .modal-overlay.visible{display:flex;}
.designer-root .modal-box{background:white;border-radius:12px;padding:24px;width:380px;box-shadow:0 20px 40px rgba(0,0,0,0.2);}
.designer-root .modal-box h3{font-size:15px;color:var(--primary);margin-bottom:14px;}
.designer-root .modal-box .modal-actions{display:flex;gap:8px;margin-top:16px;}
.designer-root .modal-box .modal-actions .btn{flex:1;margin:0;}
.designer-root .tpl-actions{display:grid;grid-template-columns:1fr 1fr;gap:4px;margin-top:6px;}
.designer-root .tpl-actions .btn{margin:0;padding:5px 6px;font-size:10px;}
.designer-root .text-style-btns{display:flex;gap:4px;margin-bottom:7px;}
.designer-root .text-style-btns button{flex:1;padding:6px 0;border:1px solid var(--border);border-radius:6px;background:#f8fafc;cursor:pointer;font-size:13px;transition:all 0.15s;}
.designer-root .text-style-btns button:hover{background:var(--primary-light);}
.designer-root .text-style-btns button.active{background:var(--primary);color:white;border-color:var(--primary);}
.designer-root .text-style-btns .btn-bold{font-weight:700;}
.designer-root .text-style-btns .btn-italic{font-style:italic;}
.designer-root .text-style-btns .btn-underline{text-decoration:underline;}
.designer-root .preview-modal{display:none;position:fixed;inset:0;background:rgba(0,0,0,0.5);z-index:9500;align-items:center;justify-content:center;}
.designer-root .preview-modal.visible{display:flex;}
.designer-root .preview-box{background:white;border-radius:12px;padding:24px;width:600px;max-width:90vw;max-height:90vh;overflow-y:auto;box-shadow:0 20px 40px rgba(0,0,0,0.3);}
.designer-root .preview-box h3{font-size:16px;color:var(--primary);margin-bottom:16px;}
.designer-root .preview-label-container{border:2px solid var(--primary);padding:8px;background:#fafafa;margin:16px 0;position:relative;}
.designer-root .preview-label{position:relative;width:50mm;height:80mm;background:white;border:1px solid #ccc;margin:0 auto;overflow:hidden;}
.designer-root .preview-element{position:absolute;overflow:hidden;}
.designer-root .preview-data{background:#e3f2fd;padding:12px;border-radius:6px;font-size:12px;margin-top:12px;}
.designer-root .preview-data h4{color:var(--primary);margin-bottom:8px;}
.designer-root .preview-data .field{display:flex;gap:8px;margin-bottom:4px;}
.designer-root .preview-data .field-label{font-weight:600;min-width:120px;}
.designer-root .preview-data .field-value{color:var(--text-muted);}
.designer-root .preview-data .field-empty{color:var(--danger);font-style:italic;}
@media(max-width:900px){.designer-root{flex-direction:column;height:auto;overflow:auto;}.designer-root aside{width:100%;min-width:0;height:auto;max-height:50vh;}}
`;

function posicionarTooltipFijo(card, trigger) {
    const r = trigger.getBoundingClientRect();
    const cardW = Math.min(260, window.innerWidth - 24);
    let left = Math.min(Math.max(8, r.left), window.innerWidth - cardW - 8);
    let top = r.bottom + 6;
    if (top + 220 > window.innerHeight) top = Math.max(8, r.top - 220);
    card.style.left = left + 'px';
    card.style.top = top + 'px';
    card.style.width = cardW + 'px';
    card.style.position = 'fixed';
    card.style.zIndex = '12000';
}

function bindPortalGlobals() {
    window.toggleTooltip = function (btn) {
        const card = btn.querySelector('.tooltip-card');
        if (!card) return;
        document.querySelectorAll('.designer-root .tooltip-card.visible').forEach(c => { if (c !== card) c.classList.remove('visible'); });
        const willShow = !card.classList.contains('visible');
        card.classList.toggle('visible');
        if (willShow) posicionarTooltipFijo(card, btn);
    };
    window.cargarPlantillaSeleccionada = function () { seleccionarPlantilla(el('select-plantilla').value); };
    window.crearNuevaPlantillaPortal = crearNuevaPlantilla;
    window.renderBobinaPortal = renderBobina;
    window.addShapePortal = addShape;
    window.guardarPlantillaPortal = guardarPlantilla;
    window.probarArticuloRealPortal = probarArticuloReal;
    window.duplicarPlantillaPortal = duplicarPlantilla;
    window.guardarComoNuevaPortal = guardarComoNueva;
    window.renombrarPlantillaPortal = renombrarPlantilla;
    window.eliminarPlantillaPortal = eliminarPlantilla;
    window.cerrarModalPortal = cerrarModal;
    window.confirmarModalPortal = confirmarModal;
    window.cerrarPreviewPortal = cerrarPreview;
    window.cambiarArticuloPreviewPortal = cambiarArticuloPreview;
    window.handleImageUploadPortal = handleImageUpload;
}

export async function renderDisenador(root, dataConnector) {
    if (!root || !dataConnector) return;
    dc = dataConnector;
    rootEl = root;
    plantillasCargadas = [];
    plantillaActual = null;
    elementoSeleccionadoIdx = null;
    dragState = null;
    pendingImageElement = null;
    modalCallback = null;
    articuloActualIdx = 0;
    articulosPrueba = [];

    // CSS del diseñador (scoped) + librería JsBarcode si no está.
    if (!document.getElementById('designer-css')) {
        const st = document.createElement('style');
        st.id = 'designer-css';
        st.textContent = DESIGNER_CSS;
        document.head.appendChild(st);
    }
    if (typeof JsBarcode === 'undefined' && !document.getElementById('jsbarcode-script')) {
        const s = document.createElement('script');
        s.id = 'jsbarcode-script';
        s.src = 'https://cdn.jsdelivr.net/npm/jsbarcode@3.11.5/dist/JsBarcode.all.min.js';
        document.head.appendChild(s);
    }

    root.innerHTML = DESIGNER_HTML;
    bindPortalGlobals();
    bindGlobalHandlers();
    bindDocumentEvents();

    // Tooltip global: cerrar al hacer clic fuera.
    document.addEventListener('click', (e) => {
        if (!e.target.closest('.help-btn') && !e.target.closest('.field-help')) {
            document.querySelectorAll('.designer-root .tooltip-card.visible').forEach(c => c.classList.remove('visible'));
        }
    });
    // Tooltips sin recortes: al hacer hover/focus se reposicionan como fixed.
    document.addEventListener('mouseover', (e) => {
        const trig = e.target.closest('.designer-root .field-help, .designer-root .help-btn');
        if (!trig) return;
        const card = trig.querySelector('.tooltip-card');
        if (card) posicionarTooltipFijo(card, trig);
    });
    document.addEventListener('focusin', (e) => {
        const trig = e.target.closest('.designer-root .field-help, .designer-root .help-btn');
        if (!trig) return;
        const card = trig.querySelector('.tooltip-card');
        if (card) posicionarTooltipFijo(card, trig);
    });

    await cargarPlantillas();
}