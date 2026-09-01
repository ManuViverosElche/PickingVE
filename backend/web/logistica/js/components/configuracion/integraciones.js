/**
 * configuracion/integraciones.js — Submenú "Integraciones y operación".
 *
 * Panel informativo de operación del portal: conexión a BigQuery (región EU),
 * panel de gestión heredado y acceso al diseñador. NO expone secretos, tokens
 * ni credenciales (regla de seguridad): solo enlaces funcionales y estado.
 */
export async function renderIntegraciones(root, dataConnector) {
    if (!root || !dataConnector) return;
    root.innerHTML = `
        <div class="integraciones-section">
            <div class="card">
                <h3>🔌 Integraciones y Operación</h3>
                <p class="text-muted">Enlaces de operación del portal. Las credenciales y tokens se gestionan en el servidor (nunca se exponen en el navegador).</p>
                <div class="grid-2" style="margin-top:16px;">
                    <div class="integration-card">
                        <h4>🗄️ BigQuery (EU)</h4>
                        <p class="text-muted">Todos los datos del portal viven en la región <b>EU</b> (proyecto <code>dashboard-439511</code>, datasets <code>GestionComercialVE</code>, <code>pickingve</code>, <code>conector_test</code>). La app accede a través de la API.</p>
                    </div>
                    <div class="integration-card">
                        <h4>📊 Panel de Control (legacy)</h4>
                        <p class="text-muted">El panel original sigue operativo e intacto durante la migración.</p>
                        <p><a class="btn" href="/manager?k=manager-panel-2026" target="_blank">Abrir /manager</a></p>
                    </div>
                    <div class="integration-card">
                        <h4>🧮 Panel de Inventario (legacy)</h4>
                        <p class="text-muted">El panel de inventario original sigue operativo e intacto.</p>
                        <p><a class="btn" href="/inventario?k=inventario-2026" target="_blank">Abrir /inventario</a></p>
                    </div>
                    <div class="integration-card">
                        <h4>🏷️ Diseñador de Etiquetas</h4>
                        <p class="text-muted">Acceso directo al diseñador industrial de etiquetas (misma funcionalidad que el submenú del portal).</p>
                        <p><a class="btn" href="/logistica/etiquetas?k=logistica-2026" target="_blank">Abrir diseñador</a></p>
                    </div>
                </div>
            </div>
        </div>`;
}