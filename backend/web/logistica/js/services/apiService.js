/**
 * apiService.js — ÚNICO transporte HTTP del portal /logistica.
 *
 * Todos los fetch() del portal viven aquí. Ningún componente visual hace fetch
 * directamente (regla de arquitectura del portal unificado).
 *
 * Si mañana cambia BigQuery, una URL de API, autenticación o la fuente de datos,
 * se modifica principalmente aquí (y en dataConnector.js).
 */
const KEY = 'logistica-2026';

const request = async (path, options = {}) => {
    const separator = path.includes('?') ? '&' : '?';
    const response = await fetch(`${path}${separator}k=${KEY}`, options);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return response.json();
};

/** POST con body JSON (para endpoints que no aceptan k por query). */
const postJson = async (path, body) =>
    request(path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body || {}) });

/** DELETE con query (plantillas). */
const del = async (path) => {
    const separator = path.includes('?') ? '&' : '?';
    const response = await fetch(`${path}${separator}k=${KEY}`, { method: 'DELETE' });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return response.json();
};

export const apiService = {
    // ---------- Sesión ----------
    getAuth: () => request('/api/auth/me'),

    // ---------- Faena: pedidos del día ----------
    getOrderDates: () => request('/api/manager/fechas'),
    getOrders: (date, state) => request(`/api/manager/orders?fecha=${encodeURIComponent(date)}&estado=${encodeURIComponent(state)}`),
    getOrderDetail: number => request(`/api/manager/report/${encodeURIComponent(number)}`),

    // ---------- Faena: informes (URLs para modal iframe / descarga) ----------
    buildPunteoUrls: number => ({
        html: `/api/manager/reporte/${encodeURIComponent(number)}?formato=html&k=${KEY}`,
        pdf: `/api/manager/reporte/${encodeURIComponent(number)}?formato=pdf&k=${KEY}`,
    }),
    buildDetalleUrl: number => `/api/manager/informe/detalle/${encodeURIComponent(number)}?k=${KEY}`,
    buildControlUrl: number => `/api/manager/informe/control/${encodeURIComponent(number)}?k=${KEY}`,
    buildDesgloseUrl: number => `/api/manager/informe/desglose/${encodeURIComponent(number)}?k=${KEY}`,

    // ---------- Faena: etiquetas a sacar ----------
    getEtiquetasDia: fecha => request(`/api/manager/etiquetas/dia?fecha=${encodeURIComponent(fecha)}`),
    setEtiquetaEstado: body => postJson('/api/manager/etiquetas/estado', body),
    buildEtiquetasDiaInformeUrl: fecha => `/api/manager/etiquetas/dia/informe?fecha=${encodeURIComponent(fecha)}&k=${KEY}`,

    // ---------- Faena: histórico ----------
    getHistoricoFechas: () => request('/api/manager/historico'),
    getHistoricoPedidos: fecha => request(`/api/manager/historico?fecha=${encodeURIComponent(fecha)}`),
    getHistoricoDetalle: numero => request(`/api/manager/historico/${encodeURIComponent(numero)}`),

    // ---------- Faena: reparto y carga ----------
    getReparto: fecha => request(`/api/manager/reparto?fecha=${encodeURIComponent(fecha)}`),
    postReparto: body => postJson('/api/manager/reparto', body),
    getCarga: () => request('/api/manager/carga'),
    getFaenaOperario: email => request(`/api/manager/faena-operario?email=${encodeURIComponent(email)}`),

    // ---------- Mensajes / chat ----------
    getRecentComments: () => request('/api/comentarios/recientes'),
    getComentarios: (pedidoId, lineaHuella) =>
        request(`/api/comentarios?pedido=${encodeURIComponent(pedidoId)}${lineaHuella ? `&linea=${encodeURIComponent(lineaHuella)}` : ''}`),
    postComentario: body => postJson('/api/comentarios', body),

    // ---------- Configuración: personas (encargados y operarios) ----------
    getEncargados: () => request('/api/encargados'),
    postEncargadoGestion: body => postJson('/api/encargados/gestion', body),
    getOperarios: () => request('/api/operarios'),
    postOperario: body => postJson('/api/operarios', body),
    eliminarOperario: body => postJson('/api/operarios/eliminar', body),

    // ---------- Configuración: fincas ----------
    getFincasGestion: () => request('/api/fincas/gestion'),
    postFinca: body => postJson('/api/fincas', body),
    postFincaPropia: body => postJson('/api/fincas/propia', body),
    eliminarFinca: body => postJson('/api/fincas/eliminar', body),

    // ---------- Configuración: maquinaria y familias ----------
    getMaquinarias: () => request('/api/manager/maquinarias'),
    postMaquinaria: body => postJson('/api/manager/maquinarias', body),
    eliminarMaquinaria: body => postJson('/api/manager/maquinarias/eliminar', body),
    getFamiliasMaquinaria: () => request('/api/manager/maquinarias-familias'),
    postFamiliaMaquinaria: body => postJson('/api/manager/maquinarias-familias', body),
    eliminarFamiliaMaquinaria: body => postJson('/api/manager/maquinarias-familias/eliminar', body),

    // ---------- Inventario: análisis y fechas ----------
    getInventoryFechas: () => request('/api/inventario/fechas'),
    getInventoryFincas: () => request('/api/inventario/fincas'),
    getInventoryDatos: ({ finca, sector = '', desde = '' } = {}) => {
        const qs = [`finca=${encodeURIComponent(finca)}`];
        if (sector) qs.push(`sector=${encodeURIComponent(sector)}`);
        if (desde) qs.push(`desde=${encodeURIComponent(desde)}`);
        return request(`/api/inventario/datos?${qs.join('&')}`);
    },
    buildReportePdfUrl: ({ finca, sector = '', desde = '' } = {}) => {
        const qs = [`finca=${encodeURIComponent(finca)}`, `k=${KEY}`];
        if (sector) qs.push(`sector=${encodeURIComponent(sector)}`);
        if (desde) qs.push(`desde=${encodeURIComponent(desde)}`);
        return `/api/inventario/reporte.pdf?${qs.join('&')}`;
    },

    // ---------- Inventario: partes ----------
    getPartesInventario: ({ finca = '', empleado = '', sector = '', desde = '' } = {}) => {
        const qs = [];
        if (finca) qs.push(`finca=${encodeURIComponent(finca)}`);
        if (empleado) qs.push(`empleado=${encodeURIComponent(empleado)}`);
        if (sector) qs.push(`sector=${encodeURIComponent(sector)}`);
        if (desde) qs.push(`desde=${encodeURIComponent(desde)}`);
        return request(`/api/inventario/partes${qs.length ? `?${qs.join('&')}` : ''}`);
    },

    // ---------- Inventario: pistoleos (gestión y borrado) ----------
    getPistoleos: ({ finca = '', sector = '', desde = '' } = {}) => {
        const qs = [];
        if (finca) qs.push(`finca=${encodeURIComponent(finca)}`);
        if (sector) qs.push(`sector=${encodeURIComponent(sector)}`);
        if (desde) qs.push(`desde=${encodeURIComponent(desde)}`);
        return request(`/api/inventario/pistoleos${qs.length ? `?${qs.join('&')}` : ''}`);
    },
    eliminarPistoleos: body => postJson('/api/inventario/pistoleos/eliminar', body),
    setCierreSector: body => postJson('/api/inventario/cierres', body),

    // ---------- Inventario: configuración ----------
    getFincasConfig: () => request('/api/inventario/fincas/config'),
    setFincaInventariable: body => postJson('/api/inventario/fincas/activa', body),
    getOperariosInventario: () => request('/api/inventario/operarios'),
    guardarFaenaInventario: body => postJson('/api/inventario/faena/bulk', body),

    // ---------- Etiquetas: plantillas y lotes ----------
    getTemplates: () => request('/api/etiquetas/plantillas'),
    postTemplate: body => postJson('/api/etiquetas/plantillas', body),
    deleteTemplate: id => del(`/api/etiquetas/plantillas/${encodeURIComponent(id)}`),
    getTemplatePreview: () => request('/api/etiquetas/render-ejemplo'),
    generateLabelBatch: body => postJson('/api/etiquetas/generar-lote', body),

    // ---------- Stock (antiguo módulo de inventario del shell) ----------
    getInventoryStock: finca => request(`/api/inventario/stock?finca=${encodeURIComponent(finca)}`),
};