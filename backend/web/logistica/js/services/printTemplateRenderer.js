/**
 * printTemplateRenderer.js — Motor de renderizado visual de plantillas de etiquetas.
 *
 * Desacoplado del DOM: compila una plantilla (formato diseñador O formato API
 * antiguo) a HTML/SVG visual real con sus capas, fuentes, posiciones, pasaportes,
 * imágenes y códigos de barras EAN-13 precargados con variables de datos.
 *
 * También maqueta la BOBINA física:
 *   - Respeta ancho/alto de etiqueta, nº de etiquetas por fila (cols), gap
 *     horizontal y márgenes definidos en la plantilla.
 *   - Agrupa la cola en contenedores de fila con exactamente 2 etiquetas.
 *   - Genera el @page { size: ancho_bobina alto_fila; margin:0 } dinámico y las
 *     reglas @media print (break-after: page por fila, sin márgenes del navegador).
 */

// Escala de pantalla para previews (px por mm, 96dpi). En impresión se usa mm físico.
const SCALE = 3.78;
// Config por defecto de bobina cuando la plantilla no la define (estándar 2/fila).
const BOBINA_DEFAULT = { cols: 2, rows: 1, gap_h_mm: 3.0, gap_v_mm: 3.0, margen_sup_mm: 1.3, margen_izq_mm: 1.0 };

// ────────────────────────────────────────────────────────────
// Bandera de la Unión Europea (fallback del pasaporte fitosanitario)
// ────────────────────────────────────────────────────────────
function estrellaPath(cx, cy, R) {
    const r = R * 0.382;
    let d = '';
    for (let i = 0; i < 10; i++) {
        const rad = i % 2 === 0 ? R : r;
        const a = (-90 + i * 36) * Math.PI / 180;
        const x = cx + rad * Math.cos(a);
        const y = cy + rad * Math.sin(a);
        d += (i === 0 ? 'M' : 'L') + x.toFixed(2) + ' ' + y.toFixed(2) + ' ';
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

/** Plantilla de sistema 9992 — la bobina de pasaporte de 2 etiquetas por fila. */
export function obtenerPlantillaSistema9992() {
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

/** Devuelve la configuración de bobina de una plantilla con defaults estándar (2/fila). */
export function normalizarBobina(plantilla) {
    const ancho = parseFloat(plantilla?.ancho_mm) || 50;
    const alto = parseFloat(plantilla?.alto_mm) || 80;
    const cols = Math.max(1, parseInt(plantilla?.cols) || BOBINA_DEFAULT.cols);
    const rows = Math.max(1, parseInt(plantilla?.rows) || BOBINA_DEFAULT.rows);
    const f = (v, dflt) => Number.isFinite(parseFloat(v)) ? parseFloat(v) : dflt;
    const gap_h = f(plantilla?.gap_h_mm, BOBINA_DEFAULT.gap_h_mm);
    const gap_v = f(plantilla?.gap_v_mm, BOBINA_DEFAULT.gap_v_mm);
    // Las plantillas antiguas solo traen margen_mm; las nuevas margen_sup_mm/margen_izq_mm.
    const margen = f(plantilla?.margen_mm, 0);
    const margen_sup = f(plantilla?.margen_sup_mm, plantilla?.margen_mm != null ? margen : BOBINA_DEFAULT.margen_sup_mm);
    const margen_izq = f(plantilla?.margen_izq_mm, plantilla?.margen_mm != null ? margen : BOBINA_DEFAULT.margen_izq_mm);
    return { ancho, alto, cols, rows, gap_h, gap_v, margen_sup, margen_izq };
}

/** Ancho total físico de la bobina (margen izq + n etiquetas + gaps + margen der). */
export function anchoBobinaMm(plantilla) {
    const b = normalizarBobina(plantilla);
    return b.margen_izq + b.cols * b.ancho + (b.cols - 1) * b.gap_h + b.margen_izq;
}

/** Alto de una fila física de la bobina. */
export function altoFilaMm(plantilla) {
    const b = normalizarBobina(plantilla);
    return b.margen_sup + b.alto + b.gap_v;
}

// ────────────────────────────────────────────────────────────
// Código de barras EAN-13 (JsBarcode → SVG)
// ────────────────────────────────────────────────────────────

function generarBarcodeSVG(valor, altoMm) {
    const codigo = String(valor || '').trim();
    // D-270: validación estricta EAN-13 (13 dígitos numéricos).
    if (!/^\d{13}$/.test(codigo) || typeof JsBarcode === 'undefined') {
        return `<span style="font-family:monospace; font-size:9pt;">${esc(codigo)}</span>`;
    }
    try {
        const svgEl = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        JsBarcode(svgEl, codigo, { format: 'EAN13', width: 2, height: Math.max(40, (altoMm || 18) * 3.0), displayValue: true, fontSize: 14, margin: 0, background: 'transparent', lineColor: '#000000' });
        const w = parseFloat(svgEl.getAttribute('width')) || 190;
        const h = parseFloat(svgEl.getAttribute('height')) || Math.max(40, (altoMm || 18) * 3.0) + 22;
        // viewBox + preserveAspectRatio="none": el SVG rellena la caja sin recorte lateral.
        return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${w} ${h}" preserveAspectRatio="none" style="width:100%; height:100%; display:block;">${svgEl.innerHTML}</svg>`;
    } catch (e) {
        return `<span style="font-family:monospace; font-size:9pt;">${esc(codigo)}</span>`;
    }
}

// ────────────────────────────────────────────────────────────
// Renderizado de una etiqueta (capa a capa)
// ────────────────────────────────────────────────────────────

/**
 * Compila el HTML visual de una etiqueta a partir de la plantilla y variables.
 * Soporta elementos del formato diseñador (tipo: db/text/rect/circle/line/image)
 * y del formato API antiguo (tipo_render: TEXTO/CODIGO_BARRAS_EAN).
 * Los campos con valor vacío se ocultan (igual que el diseñador).
 *
 * La plantilla proviene de /api/etiquetas/plantillas (tabla BigQuery
 * pickingve.etiquetas_plantillas, D-273); elementos_json se tolera tanto como
 * array como string JSON por defensa en profundidad.
 *
 * @param {Object} plantilla - Plantilla (elementos_json + dimensiones).
 * @param {Object} variables - Variables precargadas (NOMBRE_CIENTIFICO, CONTENEDOR, ...).
 * @returns {string} HTML del interior de la etiqueta (position:absolute en mm).
 */
export function renderEtiqueta(plantilla, variables) {
    const vars = variables || {};
    let elementos = plantilla?.elementos_json || [];
    if (typeof elementos === 'string') {
        try { elementos = JSON.parse(elementos) || []; } catch (e) { elementos = []; }
    }
    if (!Array.isArray(elementos)) elementos = [];
    let html = '';
    for (const element of elementos) {
        const compilado = compilarElemento(element, vars);
        if (!compilado) continue; // elemento vacío u oculto
        html += compilado;
    }
    return html;
}

function compilarElemento(element, vars) {
    const tipo = element.tipo || (element.tipo_render ? 'api_' + element.tipo_render.toLowerCase() : null);

    // Estilos base posicional en mm físicos.
    const baseStyle = `position:absolute; left:${num(element.pos_x_mm)}mm; top:${num(element.pos_y_mm)}mm; width:${num(element.ancho_mm)}mm; height:${num(element.alto_mm)}mm;`;

    // ---- Formato API antiguo ----
    if (tipo === 'api_texto') {
        const valor = valorVariable(vars, element.campo_id);
        if (!valor) return null;
        return `<div class="lbl-elem" style="${baseStyle}; font-family:${element.fuente || 'Arial'}; font-size:${num(element.tamano_fuente_pt) || 10}pt; font-weight:${element.negrita ? 'bold' : 'normal'}; text-align:${(element.alineacion || 'LEFT').toLowerCase()}; color:${element.color || '#000000'}; overflow:hidden;"><span>${esc(valor)}</span></div>`;
    }
    if (tipo === 'api_codigo_barras_ean') {
        const valor = valorVariable(vars, element.campo_id);
        if (!valor) return null;
        return `<div class="lbl-elem lbl-barcode" style="${baseStyle}; display:flex; align-items:center; justify-content:center; overflow:visible;">${generarBarcodeSVG(valor, element.alto_mm)}</div>`;
    }

    // ---- Formato diseñador ----
    const fuente = element.fuente || 'Arial';
    const tamano = element.tamano_fuente_pt || 10;
    const negrita = element.negrita ? 'bold' : 'normal';
    const cursiva = element.cursiva ? 'italic' : 'normal';
    const subrayado = element.subrayado ? 'underline' : 'none';
    const alineacion = element.alineacion || 'left';

    if (tipo === 'db') {
        const valor = valorVariable(vars, element.campo_id);
        if (!valor) return null;
        if (element.campo_id === 'CODIGO_EAN13_BARRAS' || element.campo_id === 'ean13') {
            return `<div class="lbl-elem lbl-barcode" style="${baseStyle}; display:flex; align-items:center; justify-content:center; overflow:visible;">${generarBarcodeSVG(valor, element.alto_mm)}</div>`;
        }
        const textoCompleto = (element.prefijo || '') + valor + (element.sufijo || '');
        return `<div class="lbl-elem" style="${baseStyle}; font-family:${fuente}; font-size:${tamano}pt; font-weight:${negrita}; font-style:${cursiva}; text-decoration:${subrayado}; text-align:${alineacion}; color:${element.color || '#000000'}; overflow:hidden;"><span>${esc(textoCompleto)}</span></div>`;
    }
    if (tipo === 'text') {
        let textoRaw = element.texto || '';
        if (textoRaw.startsWith('{') && textoRaw.endsWith('}')) {
            const key = textoRaw.slice(1, -1).trim();
            textoRaw = valorVariable(vars, key) || textoRaw;
        } else if (vars[textoRaw] != null) {
            textoRaw = String(vars[textoRaw]);
        } else {
            textoRaw = textoRaw.replace(/\{([^}]+)\}/g, (match, key) => valorVariable(vars, key.trim()) || match);
        }
        return `<div class="lbl-elem" style="${baseStyle}; font-family:${fuente}; font-size:${tamano}pt; font-weight:${negrita}; font-style:${cursiva}; text-decoration:${subrayado}; text-align:${alineacion}; color:${element.color || '#000000'}; overflow:hidden;"><span>${esc(textoRaw)}</span></div>`;
    }
    if (tipo === 'rect') {
        return `<div class="lbl-elem lbl-rect" style="${baseStyle}; border:${num(element.borde_grosor) || 1}px ${element.estilo_linea === 'dashed' ? 'dashed' : 'solid'} ${element.borde_color || '#000000'}; background:${element.relleno_color || 'transparent'}; border-radius:${num(element.radio_borde) || 0}px;"></div>`;
    }
    if (tipo === 'circle') {
        return `<div class="lbl-elem lbl-circle" style="${baseStyle}; border:${num(element.borde_grosor) || 1}px solid ${element.borde_color || '#000000'}; background:${element.relleno_color || 'transparent'}; border-radius:50%;"></div>`;
    }
    if (tipo === 'line') {
        const isH = (element.ancho_mm || 0) > (element.alto_mm || 0);
        return `<div class="lbl-elem lbl-line" style="position:absolute; left:${num(element.pos_x_mm)}mm; top:${num(element.pos_y_mm)}mm; width:${num(isH ? element.ancho_mm : (element.borde_grosor || 1))}mm; height:${num(isH ? (element.borde_grosor || 1) : element.alto_mm)}mm; border-top:${element.estilo_linea === 'dashed' ? '2px dashed' : '2px solid'} ${element.borde_color || '#000000'};"></div>`;
    }
    if (tipo === 'image') {
        const src = element.imagen_data || (element.fallback === 'bandera_ue' ? BANDERA_UE_SVG : '');
        if (!src) return null;
        return `<div class="lbl-elem lbl-image" style="${baseStyle}; overflow:hidden;"><img src="${src}" style="width:100%; height:100%; object-fit:${element.mantener_proporcion ? 'contain' : 'fill'};"></div>`;
    }
    return null;
}

function valorVariable(vars, campoId) {
    if (!campoId) return '';
    if (vars[campoId] != null && vars[campoId] !== '') return String(vars[campoId]);
    const upper = String(campoId).toUpperCase();
    if (vars[upper] != null && vars[upper] !== '') return String(vars[upper]);
    const lower = String(campoId).toLowerCase();
    if (vars[lower] != null && vars[lower] !== '') return String(vars[lower]);

    // Alias entre formatos (diseñador ↔ API antiguo)
    const alias = {
        'ID_ARTICULO': ['REFERENCIA_ARTICULO', 'REFERENCIA', 'referencia', 'ref', 'ref_factusol', 'ID_ARTICULO', 'articulo', 'ID'],
        'REFERENCIA_ARTICULO': ['ID_ARTICULO', 'REFERENCIA', 'referencia', 'ref', 'ref_factusol'],
        'REFERENCIA': ['ID_ARTICULO', 'REFERENCIA_ARTICULO', 'referencia', 'ref', 'ref_factusol'],
        'referencia': ['ID_ARTICULO', 'REFERENCIA_ARTICULO', 'REFERENCIA', 'ref', 'ref_factusol'],
        'ref': ['ID_ARTICULO', 'REFERENCIA_ARTICULO', 'REFERENCIA', 'referencia', 'ref_factusol'],
        'ref_factusol': ['ID_ARTICULO', 'REFERENCIA_ARTICULO', 'REFERENCIA', 'referencia', 'ref'],
        'NOMBRE_CIENTIFICO': ['nombre_cientifico', 'nombre_comercial', 'descripcion'],
        'VARIEDAD_FORMACION': ['variedad', 'descripcion', 'DESCRIPCION_ARTICULO', 'nombre_comercial'],
        'CONTENEDOR': ['litraje', 'DESCRIPCION_LITRAJE', 'contenedor'],
        'CODIGO_EAN13_BARRAS': ['ean13', 'CODIGO_EAN', 'CODIGO_EAN13', 'ean'],
        'ean13': ['CODIGO_EAN13_BARRAS', 'CODIGO_EAN', 'CODIGO_EAN13', 'ean'],
        'ean': ['CODIGO_EAN13_BARRAS', 'CODIGO_EAN', 'CODIGO_EAN13', 'ean13'],
        'UBICACION_SECTOR': ['sector', 'UBICACIONES_FINCAS'],
        'CODIGO_LOTE': ['codigo_lote', 'lote', 'ref_factusol', 'ID_ARTICULO', 'REFERENCIA_ARTICULO', 'REFERENCIA', 'ref', 'referencia'],
        'codigo_lote': ['CODIGO_LOTE', 'lote', 'ref_factusol', 'ID_ARTICULO', 'REFERENCIA_ARTICULO', 'REFERENCIA', 'ref', 'referencia'],
        'lote': ['CODIGO_LOTE', 'codigo_lote', 'ref_factusol'],
        'TEXTO_LIBRE': ['motivo', 'lote', 'nota'],
        'ID_PEDIDO': ['pedido', 'numero'],
        'CLIENTE': ['cliente_comercial', 'cliente'],
        'FINCA_CARGA': ['finca'],
        'ZONA_CARGA': ['zona'],
        'MARCA_PEDIDO': ['marca'],
    };
    for (const a of (alias[campoId] || alias[upper] || alias[lower] || [])) {
        if (vars[a] != null && vars[a] !== '') return String(vars[a]);
    }
    for (const k of Object.keys(vars)) {
        if (k.toLowerCase() === lower || k.toLowerCase() === upper.toLowerCase()) {
            if (vars[k] != null && vars[k] !== '') return String(vars[k]);
        }
    }
    return '';
}

// ────────────────────────────────────────────────────────────
// Maquetación de bobina (2 etiquetas por fila) y formato físico
// ────────────────────────────────────────────────────────────

/**
 * Cabecera de pedido en SVG NATIVO (vectorial).
 *
 * D-270: sustituye la maqueta HTML/DOM plana (divs + pt) por un SVG con
 * viewBox en unidades de mm, tipografía escalable y trazos vectoriales, de
 * modo que la impresión a 203/300 DPI sale nítida (sin interpolación del
 * raster del navegador). El root SVG recorta por defecto el contenido que
 * excede el viewBox, por lo que los textos largos no rompen la maqueta.
 */
export function renderEtiquetaCabeceraPlain(anchoMm, altoMm, item) {
    const vars = item.variables || item;
    const pedido = vars.ID_PEDIDO || item.pedido || '—';
    const cliente = vars.CLIENTE || item.cliente || '—';
    const finca = vars.FINCA_CARGA || item.finca || '—';
    const zona = vars.ZONA_CARGA || item.zona || '';
    const marca = vars.MARCA_PEDIDO || item.marcaPedido || '';

    const W = num(anchoMm) || 50;
    const H = num(altoMm) || 80;
    const pad = 3;
    const pt2mm = 0.3528;
    // Columna de valor: arranca a la izquierda de la etiqueta y termina en el
    // margen derecho; nunca menor de 4 mm.
    const xVal = Math.min(pad + 20, W - pad - 2);
    const anchoValor = Math.max(4, W - pad - xVal - 0.5);

    // ── Texto multilínea de cabecera (D-279) ──────────────────────────────
    // Ancho medio por carácter en Arial (fuentes mixtas) ≈ 0.52 em. El texto de
    // Cliente / Finca / Zona / Marca se envuelve en 2-3 líneas completas en
    // lugar de truncarse: los nombres largos fluyen verticalmente y el tamaño
    // de fuente se ajusta para que nunca se corte ni quede con puntos suspensivos.
    const CHAR_W_FACTOR = 0.52;
    const LINE_H_RATIO = 1.2;
    const MAX_LINEAS = 3;
    const MAX_ALTO_VALOR_MM = 11;

    const envolverTexto = (value, maxWidthMm, sizePt) => {
        const words = String(value == null ? '' : value).trim().split(/\s+/).filter(Boolean);
        const charMm = sizePt * pt2mm * CHAR_W_FACTOR;
        const maxChars = Math.max(1, Math.floor(maxWidthMm / charMm));
        const lineas = [];
        let cur = '';
        for (const w of words) {
            if (w.length > maxChars) {
                if (cur) { lineas.push(cur); cur = ''; }
                let rest = w;
                while (rest.length > maxChars) { lineas.push(rest.slice(0, maxChars)); rest = rest.slice(maxChars); }
                cur = rest;
            } else if (cur && (cur.length + 1 + w.length) > maxChars) {
                lineas.push(cur);
                cur = w;
            } else {
                cur = cur ? cur + ' ' + w : w;
            }
        }
        if (cur) lineas.push(cur);
        return lineas.length ? lineas : [''];
    };

    // Reduce el tamaño de fuente para que el valor quepa en ≤3 líneas dentro de
    // MAX_ALTO_VALOR_MM, manteniendo la legibilidad al máximo.
    const ajustarFuente = (value, maxWidthMm, sizePt) => {
        let s = sizePt;
        while (s > 5) {
            const n = envolverTexto(value, maxWidthMm, s).length;
            if (n <= MAX_LINEAS && n * (s * pt2mm * LINE_H_RATIO) <= MAX_ALTO_VALOR_MM) return s;
            s -= 0.5;
        }
        return s;
    };

    const textoMultilinea = (value, x, yTop, maxWidthMm, sizePt, weight, color) => {
        const lineas = envolverTexto(value, maxWidthMm, sizePt);
        const lineH = sizePt * pt2mm * LINE_H_RATIO;
        const tspans = lineas.map((ln, i) =>
            `<tspan x="${x}" y="${(yTop + i * lineH).toFixed(3)}">${esc(ln)}</tspan>`
        ).join('');
        return {
            html: `<text x="${x}" y="${yTop}" font-family="Arial, Helvetica, sans-serif" font-size="${(sizePt * pt2mm).toFixed(3)}" font-weight="${weight || 'normal'}" fill="${color || '#1e293b'}" text-anchor="start" dominant-baseline="middle">${tspans}</text>`,
            alto: lineas.length * lineH,
        };
    };

    const t = (txt, x, y, sizePt, weight, color, anchor) =>
        `<text x="${x}" y="${y}" font-family="Arial, Helvetica, sans-serif" font-size="${(sizePt * pt2mm).toFixed(3)}" font-weight="${weight || 'normal'}" fill="${color || '#1e293b'}" text-anchor="${anchor || 'start'}" dominant-baseline="middle">${esc(txt)}</text>`;

    const lines = [];
    const headerY = pad + 3;
    lines.push(t(`PEDIDO #${pedido}`, pad, headerY, 11, 'bold', '#0c3a3f', 'start'));
    lines.push(t('CABECERA', W - pad, headerY, 6.5, 'normal', '#0e8a80', 'end'));
    const dividerY = headerY + 3.4;
    lines.push(`<line x1="${pad}" y1="${dividerY}" x2="${W - pad}" y2="${dividerY}" stroke="#0e8a80" stroke-width="0.4"/>`);

    let y = dividerY + 5;
    const fila = (label, value) => {
        const sizePt = ajustarFuente(value, anchoValor, 8);
        const r = textoMultilinea(value, xVal, y, anchoValor, sizePt, 'normal', '#1e293b');
        lines.push(r.html);
        lines.push(t(label, pad, y + r.alto / 2, 8, 'bold', '#334155', 'start'));
        y += r.alto + 0.8;
    };
    fila('Cliente:', cliente);
    fila('Finca Carga:', finca);
    if (zona) fila('Zona / Muelle:', zona);
    if (marca) fila('Marca:', marca);

    const footLineY = H - pad - 5;
    lines.push(`<line x1="${pad}" y1="${footLineY}" x2="${W - pad}" y2="${footLineY}" stroke="#cbd5e1" stroke-width="0.25" stroke-dasharray="1.4 1.2"/>`);
    lines.push(t('Viveros Elche, S.L.', pad, H - pad - 2, 6, 'normal', '#64748b', 'start'));
    lines.push(t('Documento de Punteo', W - pad, H - pad - 2, 6, 'normal', '#64748b', 'end'));

    return `
        <svg xmlns="http://www.w3.org/2000/svg" width="${W}mm" height="${H}mm" viewBox="0 0 ${W} ${H}" preserveAspectRatio="xMidYMid meet" style="background:#ffffff; display:block;">
            <rect x="0.5" y="0.5" width="${W - 1}" height="${H - 1}" fill="none" stroke="#0e8a80" stroke-width="0.6"/>
            ${lines.join('\n')}
        </svg>
    `;
}

/**
 * Compila la bobina completa: filas con exactamente `cols` etiquetas (2 por
 * defecto), separadas por gap_h_mm y respetando márgenes. Cada fila es un
 * contenedor que fuerza salto de página en impresión.
 *
 * @param {Object} plantilla - Plantilla seleccionada.
 * @param {Object[]} cola - Cola plana de etiquetas: [{ variables, tipo, ... }, ...]
 * @returns {string} HTML de la bobina (filas .label-roll-row con .label-print-item).
 */
export function construirBobina(plantilla, cola) {
    const b = normalizarBobina(plantilla);
    const anchoBobina = anchoBobinaMm(plantilla);
    let html = '';
    for (let i = 0; i < cola.length; i += b.cols) {
        const fila = cola.slice(i, i + b.cols);
        let itemsHtml = '';
        for (const item of fila) {
            const vars = item.variables || item;
            const esCabecera = item.tipo === 'cabecera-picking' || item.tipo === 'cabecera-inventario';
            const etiquetaHtml = esCabecera
                ? renderEtiquetaCabeceraPlain(b.ancho, b.alto, item)
                : renderEtiqueta(plantilla, vars);
            itemsHtml += `<div class="label-print-item" style="width:${b.ancho}mm; height:${b.alto}mm;">${etiquetaHtml}</div>`;
        }
        // Rellenar huecos de fila incompleta con etiquetas vacías para mantener el ancho físico.
        while (fila.length < b.cols) {
            itemsHtml += `<div class="label-print-item label-print-item-empty" style="width:${b.ancho}mm; height:${b.alto}mm;"></div>`;
            fila.push({});
        }
        html += `<div class="label-roll-row" style="width:${anchoBobina}mm; height:${b.margen_sup + b.alto}mm; padding:${b.margen_sup}mm ${b.margen_izq}mm 0 ${b.margen_izq}mm;">${itemsHtml}</div>`;
    }
    return html;
}

/**
 * Genera el CSS de impresión dinámico de la bobina: @page size físico (ancho
 * bobina × alto fila, margin 0), reglas @media print y dimensiones de etiqueta.
 * Se inyecta en <style id="print-batch-styles"> (idempotente).
 */
export function inyectarEstilosImpresion(plantilla) {
    const b = normalizarBobina(plantilla);
    const anchoBobina = anchoBobinaMm(plantilla);
    const altoFila = b.margen_sup + b.alto;

    const css = `
@page {
    size: ${anchoBobina}mm ${altoFila}mm;
    margin: 0;
}
html, body {
    margin: 0 !important;
    padding: 0 !important;
    background: #ffffff !important;
}
@media print {
    /* ── Aislamiento estricto de impresión (D-279) ────────────────────────
       Oculta TODO el portal (sidebar, topbar, modales, tarjetas, navegación)
       y deja como ÚNICO elemento imprimible el contenedor de la bobina.
       El predicado body:has(... .label-roll-row) mantiene estas reglas INERTES
       cuando el contenedor está vacío (p.ej. imprimir faena tras etiquetas o
       tras limpiar el lote): solo se activan mientras hay bobina maquetada. */
    body:has(#print-batch-container .label-roll-row) *:not(#print-batch-container):not(#print-batch-container *):not(:has(#print-batch-container *)) {
        display: none !important;
    }

    /* Chrome del portal oculto también de forma explícita (robustez). */
    body:has(#print-batch-container .label-roll-row) aside,
    body:has(#print-batch-container .label-roll-row) nav,
    body:has(#print-batch-container .label-roll-row) header,
    body:has(#print-batch-container .label-roll-row) .sidebar,
    body:has(#print-batch-container .label-roll-row) .topbar,
    body:has(#print-batch-container .label-roll-row) .modal,
    body:has(#print-batch-container .label-roll-row) .modal-header,
    body:has(#print-batch-container .label-roll-row) .modal-body,
    body:has(#print-batch-container .label-roll-row) .modal-content,
    body:has(#print-batch-container .label-roll-row) .modal-footer,
    body:has(#print-batch-container .label-roll-row) .card,
    body:has(#print-batch-container .label-roll-row) #toast-container,
    body:has(#print-batch-container .label-roll-row) #faenaModalWrap,
    body:has(#print-batch-container .label-roll-row) .carga-box,
    body:has(#print-batch-container .label-roll-row) .filters,
    body:has(#print-batch-container .label-roll-row) .impresion-section {
        display: none !important;
    }

    /* La bobina: único elemento visible, anclado al origen (top-left) de la
       primera hoja y sin margin/padding propio para no generar hoja en blanco. */
    #print-batch-container:has(.label-roll-row) {
        display: block !important;
        position: absolute !important;
        top: 0 !important;
        left: 0 !important;
        margin: 0 !important;
        padding: 0 !important;
        border: 0 !important;
        box-shadow: none !important;
        background: #ffffff !important;
        visibility: visible !important;
    }
    #print-batch-container:has(.label-roll-row) * { visibility: visible !important; }
    /* Un contenedor vacío (o el del submenú sin lote cargado) nunca imprime. */
    #print-batch-container:not(:has(.label-roll-row)) { display: none !important; }

    .label-roll-row {
        display: flex;
        box-sizing: border-box;
        break-after: page;
        page-break-after: always;
        break-inside: avoid;
        page-break-inside: avoid;
        margin: 0;
        padding: 0;
    }
    /* Primera fila: nunca salta antes (evita hoja en blanco/pantalla inicial). */
    .label-roll-row:first-child { break-before: auto !important; page-break-before: auto !important; }
    .label-roll-row:last-child { break-after: auto; page-break-after: auto; }
    .label-print-item {
        box-sizing: border-box;
        position: relative;
        overflow: hidden;
        flex: 0 0 auto;
        background: #ffffff;
        margin-right: ${b.gap_h}mm;
    }
    .label-print-item:last-child { margin-right: 0; }
    .label-print-item .lbl-elem { box-sizing: border-box; }
    .label-print-item .lbl-elem span {
        display: block; width: 100%; height: 100%;
        overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
    }
    .label-print-item .lbl-barcode { overflow: visible !important; }
    .label-print-item .lbl-barcode svg { width: 100% !important; height: 100% !important; }
    .label-print-item .lbl-image img { pointer-events: none; }
    .label-print-item-empty { visibility: hidden; }
    * { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
}
`;
    let st = document.getElementById('print-batch-styles');
    if (!st) {
        st = document.createElement('style');
        st.id = 'print-batch-styles';
        document.head.appendChild(st);
    }
    st.textContent = css;
    return css;
}

/**
 * Vuelca la cola en el contenedor de impresión, inyecta el CSS físico y
 * dispara window.print(). Reutilizable por el modal directo y la impresión masiva.
 */
export function imprimirBobina(cola, plantilla, container) {
    if (!container) return;
    inyectarEstilosImpresion(plantilla);
    container.innerHTML = construirBobina(plantilla, cola);
    requestAnimationFrame(() => {
        window.print();
        // Tras cerrar el diálogo, vaciar el contenedor: el predicado
        // :has(.label-roll-row) deja de cumplirse y el aislamiento de impresión
        // queda inerte para que otras vistas (p.ej. faena) puedan imprimir.
        const limpia = () => {
            window.removeEventListener('afterprint', limpia);
            container.innerHTML = '';
        };
        window.addEventListener('afterprint', limpia);
    });
}

// ────────────────────────────────────────────────────────────
// Utilidades
// ────────────────────────────────────────────────────────────

function num(v) {
    const n = parseFloat(v);
    return Number.isFinite(n) ? n : 0;
}

export function esc(s) {
    return String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

/** Escala visual para miniaturas de preview (px/mm en pantalla). */
export function escalaPreview(anchoMm, maxPx = 260) {
    const px = anchoMm * SCALE;
    return Math.min(1, maxPx / px);
}