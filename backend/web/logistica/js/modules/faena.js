/**
 * faena.js — Módulo "Dashboard & Faena" de la SPA de Logística.
 *
 * Orquestador: delega la obtención de datos en dataConnector.js y el
 * renderizado del dashboard en el componente reutilizable dashboardFaena.js.
 * No contiene fetch ni lógica REST propia.
 */
import { renderDashboardFaena } from '/logistica/js/components/dashboardFaena.js?k=logistica-2026';

let dataConnectorPromise = null;
function getDataConnector() {
    if (!dataConnectorPromise) {
        dataConnectorPromise = import('/logistica/js/services/dataConnector.js?k=logistica-2026');
    }
    return dataConnectorPromise;
}

export async function initFaena() {
    const root = document.getElementById('sec-faena');
    if (!root) return;
    const dataConnector = await getDataConnector();
    await renderDashboardFaena(root, dataConnector);
}