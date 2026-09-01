/**
 * portal.js — Orquestador del portal /logistica (SPA).
 *
 * - Renderiza el menú lateral (navigation/sidebar.js).
 * - Resuelve cada ruta a su componente (import dinámico) y lo monta en el
 *   contenedor principal, actualizando el título de la topbar.
 * - La capa de datos es SIEMPRE dataConnector (import único, memoizado).
 * - Al cambiar de submenú, no se destruye el sidebar: solo se reemplaza el
 *   contenido del panel.
 */

import { renderSidebar, pageTitle } from '/logistica/js/components/navigation/sidebar.js?k=logistica-2026';

// Cache-buster: se añade a los imports dinámicos para que cada carga de página
// descargue la versión ACTUAL de los módulos (evita quedarse con una versión
// antigua cacheada por el navegador tras un deploy).
const V = `&v=${Date.now()}`;

let dcPromise = null;
function getDataConnector() {
    if (!dcPromise) {
        dcPromise = import(`/logistica/js/services/dataConnector.js?k=logistica-2026${V}`);
    }
    return dcPromise;
}

/** Resolución de ruta -> módulo/componente. Cada componente es la funcionalidad REAL. */
const ROUTES = {
    // A) Dashboard y Faena
    'faena/pedidos': () => import(`/logistica/js/components/faena/pedidosDia.js?k=logistica-2026${V}`),
    'faena/etiquetas': () => import(`/logistica/js/components/faena/etiquetasDia.js?k=logistica-2026${V}`),
    'faena/historico': () => import(`/logistica/js/components/faena/historicoCargas.js?k=logistica-2026${V}`),
    'faena/reparto': () => import(`/logistica/js/components/faena/repartoFaena.js?k=logistica-2026${V}`),
    'faena/carga': () => import(`/logistica/js/components/faena/cargaOperarios.js?k=logistica-2026${V}`),

    // B) Stock e Inventario
    'inventario/partes': () => import(`/logistica/js/components/inventario/partes.js?k=logistica-2026${V}`),
    'inventario/resumen-sector': () => import(`/logistica/js/components/inventario/resumenSector.js?k=logistica-2026${V}`),
    'inventario/resumen-referencia': () => import(`/logistica/js/components/inventario/resumenReferencia.js?k=logistica-2026${V}`),
    'inventario/inventarios': () => import(`/logistica/js/components/inventario/inventarios.js?k=logistica-2026${V}`),
    'inventario/mapa': () => import(`/logistica/js/components/inventario/mapa.js?k=logistica-2026${V}`),

    // C) Impresión de etiquetas
    'etiquetas/disenador': () => import(`/logistica/js/components/etiquetas/disenador.js?k=logistica-2026${V}`),
    'etiquetas/impresion': () => import(`/logistica/js/components/etiquetas/impresion.js?k=logistica-2026${V}`),

    // D) Personal y Configuración
    'config/personal': () => import(`/logistica/js/components/configuracion/personal.js?k=logistica-2026${V}`),
    'config/logistica': () => import(`/logistica/js/components/configuracion/logistica.js?k=logistica-2026${V}`),
    'config/inventario': () => import(`/logistica/js/components/configuracion/inventario.js?k=logistica-2026${V}`),
    'config/integraciones': () => import(`/logistica/js/components/configuracion/integraciones.js?k=logistica-2026${V}`),
};

/** Nombre del componente que expone cada módulo (convención render<Nombre>). */
const ENTRY_FN = {
    'faena/pedidos': 'renderPedidosDia',
    'faena/etiquetas': 'renderEtiquetasDia',
    'faena/historico': 'renderHistoricoCargas',
    'faena/reparto': 'renderRepartoFaena',
    'faena/carga': 'renderCargaOperarios',
    'inventario/partes': 'renderPartes',
    'inventario/resumen-sector': 'renderResumenSector',
    'inventario/resumen-referencia': 'renderResumenReferencia',
    'inventario/inventarios': 'renderInventarios',
    'inventario/mapa': 'renderMapa',
    'etiquetas/disenador': 'renderDisenador',
    'etiquetas/impresion': 'renderImpresion',
    'config/personal': 'renderPersonal',
    'config/logistica': 'renderConfigLogistica',
    'config/inventario': 'renderConfigInventario',
    'config/integraciones': 'renderIntegraciones',
};

const mainEl = () => document.getElementById('portal-main');
const titleEl = () => document.getElementById('current-page-title');

async function navigate(key) {
    if (!ROUTES[key]) return;
    const main = mainEl();
    if (!main) return;
    const title = titleEl();
    if (title) title.textContent = pageTitle(key);

    // Estado de carga: el menú lateral queda intacto (no se re-renderiza).
    main.innerHTML = '<div style="text-align:center; color:var(--mut); padding:48px;"><span class="spinner" style="display:inline-block; width:22px; height:22px; border:3px solid var(--line); border-top-color:var(--primary); border-radius:50%; animation:spinner .8s linear infinite;"></span><p style="margin-top:12px;">Cargando…</p></div>';

    try {
        const dc = await getDataConnector();
        const mod = await ROUTES[key]();
        const fn = mod[ENTRY_FN[key]];
        if (typeof fn !== 'function') throw new Error(`Componente ${key} no exporta ${ENTRY_FN[key]}`);
        main.innerHTML = '';
        await fn(main, dc);
    } catch (e) {
        console.error(`Error cargando ${key}`, e);
        main.innerHTML = `<div style="text-align:center; color:var(--bad); padding:48px;">
            <p>No se pudo cargar la sección.</p>
            <button class="btn-sec" onclick="location.reload()">Recargar</button></div>`;
    }
}

export async function initPortal() {
    const nav = document.getElementById('sidebar-nav');
    const main = mainEl();
    if (!nav || !main) return;

    renderSidebar(nav, navigate);

    // Ruta inicial: Faena / Pedidos del día.
    const saved = localStorage.getItem('pickingve_route') || 'faena/pedidos';
    const savedItem = nav.querySelector(`.nav-item[data-key="${saved}"]`);
    if (savedItem) savedItem.classList.add('active');
    await navigate(saved);
}

// Persistir la ruta activa para no perder la sección al recargar.
window.addEventListener('beforeunload', () => {
    const active = document.querySelector('#sidebar-nav .nav-item.active');
    if (active) localStorage.setItem('pickingve_route', active.dataset.key);
});

// Estilos del spinner (global).
if (!document.getElementById('portal-spinner-style')) {
    const st = document.createElement('style');
    st.id = 'portal-spinner-style';
    st.textContent = '@keyframes spinner{to{transform:rotate(360deg)}}';
    document.head.appendChild(st);
}