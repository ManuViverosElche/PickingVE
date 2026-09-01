/**
 * sidebar.js — Menú lateral del portal /logistica (secciones + submenús).
 *
 * Define el árbol de navegación del portal unificado:
 *   A) Dashboard y Faena  (1:1 con las pestañas de /manager)
 *   B) Stock e Inventario (1:1 con las pestañas de /inventario)
 *   C) Impresión de Etiquetas (diseñador + impresión masiva)
 *   D) Personal y Configuración (unión de config de /manager + /inventario)
 *
 * El componente NO hace fetch: cada submenú resuelve su componente vía onNavigate.
 */

export const NAV_TREE = [
    {
        id: 'faena',
        icon: 'fa-chart-line',
        title: 'Dashboard y Faena',
        items: [
            { key: 'faena/pedidos', label: '📦 Pedidos del día' },
            { key: 'faena/etiquetas', label: '🏷️ Etiquetas a sacar' },
            { key: 'faena/historico', label: '📜 Histórico de cargas' },
            { key: 'faena/reparto', label: '📋 Reparto de faena' },
            { key: 'faena/carga', label: '⚖️ Carga por operario' },
        ],
    },
    {
        id: 'inventario',
        icon: 'fa-boxes-stacked',
        title: 'Stock e Inventario',
        items: [
            { key: 'inventario/partes', label: '📋 Partes realizados' },
            { key: 'inventario/resumen-sector', label: '📊 Resumen por sector' },
            { key: 'inventario/resumen-referencia', label: '🌿 Resumen por referencia' },
            { key: 'inventario/inventarios', label: '🗑️ Inventarios' },
            { key: 'inventario/mapa', label: '🗺️ Mapa' },
        ],
    },
    {
        id: 'etiquetas',
        icon: 'fa-tags',
        title: 'Impresión de Etiquetas',
        items: [
            { key: 'etiquetas/disenador', label: '🎨 Diseñador de etiquetas' },
            { key: 'etiquetas/impresion', label: '🖨️ Impresión masiva' },
        ],
    },
    {
        id: 'config',
        icon: 'fa-gear',
        title: 'Personal y Configuración',
        items: [
            { key: 'config/personal', label: '👥 Personal y operarios' },
            { key: 'config/logistica', label: '🚜 Configuración de logística y faena' },
            { key: 'config/inventario', label: '📦 Configuración de inventario' },
            { key: 'config/integraciones', label: '🔌 Integraciones y operación' },
        ],
    },
];

const TITLES = {
    'faena/pedidos': 'Pedidos del día',
    'faena/etiquetas': 'Etiquetas a sacar',
    'faena/historico': 'Histórico de cargas',
    'faena/reparto': 'Reparto de faena',
    'faena/carga': 'Carga por operario',
    'inventario/partes': 'Partes realizados',
    'inventario/resumen-sector': 'Resumen por sector',
    'inventario/resumen-referencia': 'Resumen por referencia',
    'inventario/inventarios': 'Inventarios',
    'inventario/mapa': 'Mapa',
    'etiquetas/disenador': 'Diseñador de etiquetas',
    'etiquetas/impresion': 'Impresión masiva de etiquetas',
    'config/personal': 'Personal y operarios',
    'config/logistica': 'Configuración de logística y faena',
    'config/inventario': 'Configuración de inventario',
    'config/integraciones': 'Integraciones y operación',
};

export function pageTitle(key) {
    return TITLES[key] || 'Portal Logístico';
}

/**
 * renderSidebar(root, onNavigate) — pinta el menú lateral.
 * Las secciones son acordeones; los submenús disparan onNavigate(key).
 * Recuerda la sección abierta en localStorage.
 */
export function renderSidebar(root, onNavigate) {
    if (!root) return;
    let openSection = localStorage.getItem('pickingve_sidebar_section') || 'faena';

    function paint() {
        root.innerHTML = NAV_TREE.map(sec => `
            <div class="nav-section" data-section="${sec.id}">
                <div class="nav-section-head ${openSection === sec.id ? 'open' : ''}" data-toggle="${sec.id}">
                    <span><i class="fa-solid ${sec.icon}"></i> ${sec.title}</span>
                    <span class="nav-chevron">▸</span>
                </div>
                <div class="nav-section-body" style="display:${openSection === sec.id ? 'block' : 'none'};">
                    ${sec.items.map(it => `<a class="nav-item" data-key="${it.key}">${it.label}</a>`).join('')}
                </div>
            </div>`).join('');

        root.querySelectorAll('[data-toggle]').forEach(head => {
            head.addEventListener('click', () => {
                const sec = head.dataset.toggle;
                openSection = openSection === sec ? '' : sec;
                localStorage.setItem('pickingve_sidebar_section', openSection);
                paint();
            });
        });

        root.querySelectorAll('.nav-item').forEach(item => {
            item.addEventListener('click', () => {
                root.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
                item.classList.add('active');
                onNavigate(item.dataset.key);
            });
        });
    }

    paint();
}