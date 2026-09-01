/**
 * printBatchBuilder.js — Generador desacoplado de lotes de impresión.
 *
 * Módulo puro de dominio: construye el payload de impresión (cola de etiquetas)
 * a partir de datos en memoria, sin depender de fetch ni de DOM.
 *
 * Reutilizable para cualquier contexto futuro (Pedido, Inventario, etc.).
 * Cada contexto aporta su propia función "buildContextPayload" que devuelve
 * { cabecera, items } donde:
 *   - cabecera: etiqueta de portada del lote (1 unidad, siempre la primera).
 *   - items: etiquetas de detalle (una por combinación, con su cantidad).
 *
 * El resultado es un array plano listo para la cola de impresión:
 *   [cabecera, ...items.flatMap(i => Array(i.cantidad).fill(i))]
 */

import { obtenerPlantillaSistema9992 } from '/logistica/js/services/printTemplateRenderer.js?k=logistica-2026';

// ────────────────────────────────────────────────────────────
// Contexto: PEDIDO (Picking)
// ────────────────────────────────────────────────────────────

/**
 * Construye el lote de impresión para un pedido de picking.
 *
 * @param {Object} pedido - Datos del pedido (tal cual viene de etiquetas/dia):
 *   { pedido, cliente, clienteFiscal, finca, zona, marcaPedido, etiquetas[] }
 *   Cada etiqueta: { referencia, litraje, sector, cantidad, motivo }
 * @returns {{ cabecera: Object, items: Object[] }}
 */
export function buildPedidoBatch(pedido) {
    const cabecera = buildCabeceraPedido(pedido);
    const items = (pedido.etiquetas || []).map(et => {
        const descripcion = et.descripcion || et.referencia || '';
        const ean = et.ean || '';
        return {
            tipo: 'item-picking',
            titulo: `${et.referencia}${et.litraje ? ' · ' + et.litraje : ''}${et.sector ? ' · ' + et.sector : ''}`,
            referencia: et.referencia || '',
            litraje: et.litraje || '',
            sector: et.sector || '',
            motivo: et.motivo || '',
            cantidad: Math.max(1, Math.round(Number(et.cantidad) || 1)),
            pedido: pedido.pedido || '',
            variables: {
                ID_PEDIDO: pedido.pedido || '',
                NOMBRE_CIENTIFICO: descripcion,
                VARIEDAD_FORMACION: descripcion,
                CONTENEDOR: et.litraje || '',
                UBICACION_SECTOR: et.sector || '',
                TEXTO_LIBRE: et.motivo || '',
                CODIGO_EAN13_BARRAS: ean,
                CODIGO_LOTE: et.referencia || '',
                codigo_lote: et.referencia || '',
                CLIENTE: pedido.cliente || '',
                FINCA_CARGA: pedido.finca || '',
                ZONA_CARGA: pedido.zona || '',
                MARCA_PEDIDO: pedido.marcaPedido || '',
                nombre_comercial: descripcion,
                variedad: descripcion,
                ean13: ean,
                ID_ARTICULO: et.referencia || '',
                REFERENCIA_ARTICULO: et.referencia || '',
                REFERENCIA: et.referencia || '',
                ref: et.referencia || '',
                ref_factusol: et.referencia || '',
                referencia: et.referencia || '',
            },
        };
    });
    return { cabecera, items };
}

/**
 * Cabecera de pedido: etiqueta de portada con los datos identificativos.
 *
 * Reglas de visualización del cliente:
 *   - Si clienteFiscal y cliente (comercial) son distintos → "Fiscal - Comercial"
 *   - Si son iguales o solo existe uno → mostrar ese único nombre
 */
function buildCabeceraPedido(pedido) {
    const clienteComercial = (pedido.cliente || '').trim();
    const clienteFiscal = (pedido.clienteFiscal || '').trim();

    let clienteLinea;
    if (clienteFiscal && clienteComercial && clienteFiscal !== clienteComercial) {
        clienteLinea = `${clienteFiscal} - ${clienteComercial}`;
    } else {
        clienteLinea = clienteComercial || clienteFiscal || '—';
    }

    const partes = [
        `Pedido: ${pedido.pedido || '—'}`,
        `Cliente: ${clienteLinea}`,
        `Finca: ${pedido.finca || '—'}`,
    ];
    if (pedido.zona) partes.push(`Zona: ${pedido.zona}`);
    if (pedido.marcaPedido) partes.push(`Marca: ${pedido.marcaPedido}`);

    return {
        tipo: 'cabecera-picking',
        titulo: `PEDIDO ${pedido.pedido || '—'}`,
        pedido: pedido.pedido || '',
        cliente: clienteLinea,
        clienteFiscal: clienteFiscal,
        clienteComercial: clienteComercial,
        finca: pedido.finca || '',
        zona: pedido.zona || '',
        marcaPedido: pedido.marcaPedido || '',
        lineas: partes,
        variables: {
            ID_PEDIDO: pedido.pedido || '',
            CLIENTE: clienteLinea,
            FINCA_CARGA: pedido.finca || '',
            ZONA_CARGA: pedido.zona || '',
            MARCA_PEDIDO: pedido.marcaPedido || '',
            CODIGO_LOTE: pedido.pedido || '',
            codigo_lote: pedido.pedido || '',
            TEXTO_LIBRE: `Pedido ${pedido.pedido || ''}`,
            NOMBRE_CIENTIFICO: clienteLinea,
            VARIEDAD_FORMACION: `Pedido ${pedido.pedido || ''}`,
            CONTENEDOR: pedido.finca || '',
            UBICACION_SECTOR: pedido.zona || '',
            nombre_comercial: clienteLinea,
            variedad: `Pedido ${pedido.pedido || ''}`,
            ref_factusol: pedido.pedido || '',
        },
    };
}

// ────────────────────────────────────────────────────────────
// Contexto: INVENTARIO (preparado para futuro)
// ────────────────────────────────────────────────────────────

/**
 * Construye el lote de impresión para un lote de inventario.
 * (Preparado como interfaz; se implementará cuando se active el flujo.)
 *
 * @param {Object} inv - Datos del inventario:
 *   { id, finca, sector, operario, fecha, items[] }
 * @returns {{ cabecera: Object, items: Object[] }}
 */
export function buildInventarioBatch(inv) {
    const cabecera = {
        tipo: 'cabecera-inventario',
        titulo: `INVENTARIO ${inv.id || ''}`,
        finca: inv.finca || '',
        sector: inv.sector || '',
        operario: inv.operario || '',
        fecha: inv.fecha || '',
        lineas: [
            `Inventario: ${inv.id || '—'}`,
            `Finca: ${inv.finca || '—'}`,
            `Sector: ${inv.sector || '—'}`,
            `Operario: ${inv.operario || '—'}`,
            `Fecha: ${inv.fecha || '—'}`,
        ],
        variables: {
            ID_PEDIDO: inv.id || '',
            CLIENTE: inv.finca || '',
            FINCA_CARGA: inv.finca || '',
            ZONA_CARGA: inv.sector || '',
            TEXTO_LIBRE: `Inventario ${inv.id || ''}`,
            NOMBRE_CIENTIFICO: inv.finca || '',
            VARIEDAD_FORMACION: inv.sector || '',
            CONTENEDOR: inv.operario || '',
            UBICACION_SECTOR: inv.sector || '',
            ref_factusol: inv.id || '',
        },
    };
    const items = (inv.items || []).map(it => ({
        tipo: 'item-inventario',
        titulo: `${it.referencia || ''}${it.litraje ? ' · ' + it.litraje : ''}${it.sector ? ' · ' + it.sector : ''}`,
        referencia: it.referencia || '',
        litraje: it.litraje || '',
        sector: it.sector || '',
        cantidad: Math.max(1, Math.round(Number(it.cantidad) || 1)),
        variables: {
            NOMBRE_CIENTIFICO: it.referencia || '',
            VARIEDAD_FORMACION: it.referencia || '',
            CONTENEDOR: it.litraje || '',
            UBICACION_SECTOR: it.sector || '',
            CODIGO_EAN13_BARRAS: it.ean || '',
            CODIGO_LOTE: it.referencia || '',
            codigo_lote: it.referencia || '',
            nombre_comercial: it.referencia || '',
            variedad: it.referencia || '',
            ean13: it.ean || '',
            ref_factusol: it.referencia || '',
        },
    }));
    return { cabecera, items };
}

// ────────────────────────────────────────────────────────────
// Utilidad común: aplanar el lote en cola de impresión
// ────────────────────────────────────────────────────────────

/**
 * Devuelve la plantilla SIEMPRE fresca desde la base de datos
 * (/api/etiquetas/plantillas vía dataConnector) durante la generación del lote
 * de impresión (D-273). La base de datos es la fuente de verdad; la plantilla
 * de sistema 9992 (hardcoded en el renderer) solo actúa como fallback si el
 * servidor no responde o no la contiene.
 *
 * @param {Object} dc - dataConnector (expone fetchPlantillas()).
 * @param {string} [plantillaId] - id de la plantilla seleccionada.
 * @returns {Promise<Object>} plantilla fresca (o fallback de sistema).
 */
export async function obtenerPlantillaFresca(dc, plantillaId) {
    let catalogo = [];
    try {
        catalogo = (await dc.fetchPlantillas()) || [];
    } catch (e) {
        console.warn('No se pudo refrescar el catálogo de plantillas:', e);
    }
    const sistema = obtenerPlantillaSistema9992();
    const mapa = new Map([[sistema.id, sistema]]);
    catalogo.forEach(t => mapa.set(t.id, t));
    const todas = Array.from(mapa.values());
    return todas.find(t => t.id === plantillaId) || todas[0] || sistema;
}

/**
 * Convierte un resultado { cabecera, items } en un array plano para la cola.
 * La cabecera va siempre en primer lugar (1 unidad).
 * Los items se expanden por cantidad.
 *
 * @param {{ cabecera: Object, items: Object[] }} batch
 * @returns {Object[]} cola plana
 */
export function flattenBatch(batch) {
    const cola = [batch.cabecera];
    for (const item of batch.items) {
        const copias = Math.max(1, item.cantidad || 1);
        for (let i = 0; i < copias; i++) {
            cola.push(item);
        }
    }
    return cola;
}
