/**
 * dataConnector.js — ÚNICA capa de datos del portal /logistica.
 *
 * Frontera del sistema (AGENTS.md): si mañana cambia BigQuery, una URL de API,
 * autenticación o la fuente de datos, SOLO se modifica este módulo (o apiService).
 * Los componentes visuales NUNCA hacen fetch directamente: piden datos aquí.
 *
 * Reúne TODA la funcionalidad real que hoy viven en /manager y /inventario
 * (solo lectura, WIP ajeno que NO se modifica):
 *   - Faena: pedidos, etiquetas, histórico, reparto, carga, mensajes.
 *   - Inventario: fincas/sectores, datos/análisis, partes, pistoleos, cierres,
 *     configuración (fincas propias, faena a inventariar, operarios autorizados).
 *   - Configuración unificada: encargados, operarios, fincas, maquinaria,
 *     familias de maquinaria.
 *   - Etiquetas: plantillas del diseñador, render de ejemplo, generación de lote.
 */
import { apiService } from '/logistica/js/services/apiService.js?k=logistica-2026';

// ============================================================
// Utilidades puras (compartidas con /manager y /inventario)
// ============================================================

export function esRefControl(ref) {
    const n = parseInt(ref, 10);
    return (n >= 99990 && n <= 99999) || String(ref || '').startsWith('9999');
}

export function totalesPlanta(pedido) {
    const lineas = (pedido.lineas || []).filter(l => !esRefControl(l.referencia));
    const solicitada = lineas.reduce((a, l) => a + (l.pendientes || 0), 0);
    const acopiada = lineas.reduce((a, l) => a + (l.acopiado || 0), 0);
    const asignada = lineas.filter(l => (l.operarioAsignado || '').trim())
        .reduce((a, l) => a + (l.pendientes || 0), 0);
    return { solicitada, acopiada, asignada };
}

export function escHtml(s) {
    return String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

export function jsAttr(s) {
    return escHtml(s).replace(/'/g, '&#39;').replace(/"/g, '&quot;');
}

export function formatearFechaDDMMYYYY(isoStr) {
    if (!isoStr) return '';
    const clean = String(isoStr).split('T')[0].split(' ')[0];
    const parts = clean.split('-');
    if (parts.length === 3) return `${parts[2]}/${parts[1]}/${parts[0]}`;
    return isoStr;
}

/** Formato fecha/hora para inventario (DD/MM/YYYY HH:MM) — misma lógica que /inventario.fmtFecha. */
export function fmtFechaInv(isoStr) {
    if (!isoStr) return '';
    const s = String(isoStr).trim();
    const parts = s.split(' ')[0].split('-');
    if (parts.length === 3) {
        const hora = s.split(' ')[1] ? (' ' + s.split(' ')[1].slice(0, 5)) : '';
        return parts[2] + '/' + parts[1] + '/' + parts[0] + hora;
    }
    return s;
}

/** Formato duración (segundos -> "X h Y min Z s") — misma lógica que /inventario.fmtDuracion. */
export function fmtDuracion(seg) {
    seg = Number(seg || 0);
    if (seg <= 0) return '—';
    const h = Math.floor(seg / 3600), m = Math.floor((seg % 3600) / 60), s = seg % 60;
    return (h ? h + ' h ' : '') + (m ? m + ' min ' : '') + s + ' s';
}

/** Formato número: entero si es redondo, si no 1 decimal con coma. */
export function fmtNum(n) {
    const v = Number(n || 0);
    return Math.abs(v - Math.round(v)) < 0.05 ? String(Math.round(v)) : v.toFixed(1).replace('.', ',');
}

/** Badge de estado inventario (EXCESO/FALTA/OK). */
export function badgeEstadoInv(estado) {
    const cls = estado === 'EXCESO' ? 'badge-exceso' : estado === 'FALTA' ? 'badge-falta' : 'badge-ok';
    return `<span class="estado-badge ${cls}">${escHtml(estado)}</span>`;
}

export function hoyStr() {
    const hoy = new Date();
    return `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, '0')}-${String(hoy.getDate()).padStart(2, '0')}`;
}

export function fechaLocalISO(d) {
    return d.getFullYear() + '-' + ('0' + (d.getMonth() + 1)).slice(-2) + '-' + ('0' + d.getDate()).slice(-2);
}

// ---------- utilidades visuales de pedidos (faena) ----------

export function estadoBadgeHtml(pedido) {
    if (pedido.estado === 'enviado') return '<span class="badge b-green">✅ Enviado</span>';
    if (pedido.estado === 'camion_asignado') return '<span class="badge b-blue" style="background:var(--info-bg)">🚛 Camión Asignado</span>';
    if (pedido.estado === 'en_proceso') return '<span class="badge b-yellow">⚙️ En Proceso</span>';
    return '<span class="badge b-blue">⏳ Sin Acopiar</span>';
}

export function cardClassEstado(pedido) {
    return {
        enviado: 'card-enviado',
        camion_asignado: 'card-camion_asignado',
        en_proceso: 'card-en_proceso',
    }[pedido.estado] || 'card-sin_acopiar';
}

export function buildInformePunteoUrl(numero) {
    return apiService.buildPunteoUrls(numero);
}

export function buildInformeDetalleUrl(numero) {
    return apiService.buildDetalleUrl(numero);
}

export function buildInformeControlUrl(numero) {
    return apiService.buildControlUrl(numero);
}

export function buildInformeDesgloseUrl(numero) {
    return apiService.buildDesgloseUrl(numero);
}

export function buildEtiquetasDiaInformeUrl(fecha) {
    return apiService.buildEtiquetasDiaInformeUrl(fecha);
}

// ============================================================
// Estado de lectura (localStorage) — mismo mecanismo que /manager
// ============================================================

const LECTURAS_KEY = 'pickingve_panel_lecturas';
let unreadMap = {};

function getLecturas() {
    try { return JSON.parse(localStorage.getItem(LECTURAS_KEY) || '{}'); } catch (e) { return {}; }
}

export function marcarLeido(clave) {
    const lecturas = getLecturas();
    lecturas[clave] = new Date().toISOString();
    localStorage.setItem(LECTURAS_KEY, JSON.stringify(lecturas));
    document.querySelectorAll(`[data-unread-key="${CSS.escape(clave)}"]`).forEach(el => el.classList.remove('btn-msg-unread'));
}

export function esSinLeer(pedidoId, lineaHuella) {
    const clave = lineaHuella ? `${pedidoId}|${lineaHuella}` : pedidoId;
    const ultimo = unreadMap[clave] || unreadMap[pedidoId];
    if (!ultimo) return false;
    const leido = getLecturas()[clave];
    return !leido || new Date(ultimo) > new Date(leido);
}

// ============================================================
// Faena: pedidos del día
// ============================================================

export async function fetchFechas() {
    return (await apiService.getOrderDates()).fechas || [];
}

export function resolverFechaInicial(fechas) {
    const hoy = hoyStr();
    return fechas.find(f => f.fecha >= hoy)?.fecha || fechas.at(-1)?.fecha || hoy;
}

export async function fetchPedidosDia(fecha, estado = 'todos') {
    return (await apiService.getOrders(fecha, estado)).pedidos || [];
}

export async function fetchDetallePedido(numero) {
    return apiService.getOrderDetail(numero);
}

export async function fetchRecientes() {
    unreadMap = {};
    try {
        const data = await apiService.getRecentComments();
        (data.recientes || []).forEach(r => {
            const clave = r.linea_huella ? `${r.pedido_id}|${r.linea_huella}` : r.pedido_id;
            unreadMap[clave] = r.ultimo_mensaje;
        });
    } catch (e) {
        console.error('Error cargando mensajes recientes', e);
    }
}

export async function fetchComentarios(pedidoId, lineaHuella) {
    return apiService.getComentarios(pedidoId, lineaHuella);
}

export async function postComentario(body) {
    return apiService.postComentario(body);
}

// ============================================================
// Faena: etiquetas a sacar
// ============================================================

export async function fetchEtiquetasDia(fecha) {
    return apiService.getEtiquetasDia(fecha);
}

export async function marcarEtiquetaDia(payload) {
    return apiService.setEtiquetaEstado(payload);
}

// ============================================================
// Faena: histórico
// ============================================================

export async function fetchHistoricoFechas() {
    return (await apiService.getHistoricoFechas()).fechas || [];
}

export async function fetchHistoricoPedidos(fecha) {
    return (await apiService.getHistoricoPedidos(fecha)).pedidos || [];
}

export async function fetchHistoricoDetalle(numero) {
    return apiService.getHistoricoDetalle(numero);
}

// ============================================================
// Faena: reparto y carga
// ============================================================

export async function fetchReparto(fecha) {
    return (await apiService.getReparto(fecha)).asignaciones || [];
}

export async function guardarReparto(body) {
    return apiService.postReparto(body);
}

export async function fetchCarga() {
    return (await apiService.getCarga()).operarios || [];
}

export async function fetchFaenaOperario(email) {
    return apiService.getFaenaOperario(email);
}

export async function fetchOperariosLista() {
    return (await apiService.getOperarios()).operarios || [];
}

export async function fetchEncargadosLista() {
    return (await apiService.getEncargados()).encargados || [];
}

export function filtrarPedidos(pedidos, { finca = '', query = '' } = {}) {
    const fincaFilter = finca.toUpperCase();
    const q = (query || '').toLowerCase();
    return pedidos.filter(o => {
        const matchFinca = !fincaFilter || (o.finca || '').toUpperCase() === fincaFilter;
        if (!matchFinca) return false;
        if (!q) return true;
        return [
            o.numero, o.serie, o.cliente, o.clienteFiscal, o.referenciaPedido, o.agente,
            o.direccionDescarga, o.marcaPedido, o.finca, o.sector,
        ].some(v => String(v || '').toLowerCase().includes(q));
    });
}

export function agruparPorFinca(pedidos) {
    const byFinca = {};
    pedidos.forEach(o => {
        const f = o.finca || 'SIN FINCA';
        if (!byFinca[f]) byFinca[f] = [];
        byFinca[f].push(o);
    });
    return byFinca;
}

// ============================================================
// Inventario: fincas, sectores y análisis
// ============================================================

export async function fetchInventarioFechas() {
    return (await apiService.getInventoryFechas()).fechas || [];
}

export async function fetchInventarioFincas() {
    return (await apiService.getInventoryFincas()).fincas || [];
}

export async function fetchInventarioDatos(params) {
    return apiService.getInventoryDatos(params);
}

export function buildReportePdfUrl(params) {
    return apiService.buildReportePdfUrl(params);
}

export async function fetchPartesInventario(params) {
    return (await apiService.getPartesInventario(params)).partes || [];
}

export async function fetchPistoleos(params) {
    return (await apiService.getPistoleos(params)).pistoleos || [];
}

export async function eliminarPistoleos(body) {
    return apiService.eliminarPistoleos(body);
}

export async function setCierreSector(body) {
    return apiService.setCierreSector(body);
}

// ============================================================
// Inventario: configuración
// ============================================================

export async function fetchFincasConfig() {
    return apiService.getFincasConfig();
}

export async function setFincaInventariable(body) {
    return apiService.setFincaInventariable(body);
}

export async function fetchOperariosInventario() {
    return (await apiService.getOperariosInventario()).operarios || [];
}

export async function guardarFaenaInventario(body) {
    return apiService.guardarFaenaInventario(body);
}

// ============================================================
// Configuración unificada: personas, fincas, maquinaria
// ============================================================

export async function fetchEncargados() {
    return (await apiService.getEncargados()).encargados || [];
}

export async function guardarEncargado(body) {
    return apiService.postEncargadoGestion(body);
}

export async function fetchOperarios() {
    return (await apiService.getOperarios()).operarios || [];
}

export async function guardarOperario(body) {
    return apiService.postOperario(body);
}

export async function eliminarOperario(body) {
    return apiService.eliminarOperario(body);
}

export async function fetchFincasGestion() {
    return (await apiService.getFincasGestion()).fincas || [];
}

export async function guardarFinca(body) {
    return apiService.postFinca(body);
}

export async function setFincaPropia(body) {
    return apiService.postFincaPropia(body);
}

export async function eliminarFinca(body) {
    return apiService.eliminarFinca(body);
}

export async function fetchMaquinarias() {
    return (await apiService.getMaquinarias()).maquinarias || [];
}

export async function guardarMaquinaria(body) {
    return apiService.postMaquinaria(body);
}

export async function eliminarMaquinaria(body) {
    return apiService.eliminarMaquinaria(body);
}

export async function fetchFamiliasMaquinaria() {
    return (await apiService.getFamiliasMaquinaria()).familias || [];
}

export async function guardarFamiliaMaquinaria(body) {
    return apiService.postFamiliaMaquinaria(body);
}

export async function eliminarFamiliaMaquinaria(body) {
    return apiService.eliminarFamiliaMaquinaria(body);
}

// ============================================================
// Etiquetas: diseñador de plantillas
// ============================================================

export async function fetchPlantillas() {
    return (await apiService.getTemplates()).plantillas || [];
}

export async function guardarPlantilla(body) {
    return apiService.postTemplate(body);
}

export async function eliminarPlantilla(id) {
    return apiService.deleteTemplate(id);
}

export async function fetchRenderEjemplo() {
    return apiService.getTemplatePreview();
}

export async function generarLote(body) {
    return apiService.generateLabelBatch(body);
}