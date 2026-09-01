/**
 * cargaOperarios.js — Componente "Carga por Operario" (subpestaña de Faena).
 *
 * Replica la tabla de carga global de /manager (renderTablaCargaOperarios) usando
 * dataConnector.fetchCarga(). Muestra asignado/recogido/total a acopiar y equilibrio.
 */
let dc = null;

export async function renderCargaOperarios(root, dataConnector) {
    if (!root || !dataConnector) return;
    dc = dataConnector;
    root.innerHTML = `
        <div id="cargaContainer"><p class="text-muted">Cargando tabla de carga por operario...</p></div>
        <button class="btn-sec" id="carga-refresh">🔄 Actualizar</button>
    `;
    root.querySelector('#carga-refresh').addEventListener('click', () => loadCarga());
    await loadCarga();
}

async function loadCarga() {
    const container = document.getElementById('cargaContainer');
    try {
        const filas = await dc.fetchCarga();
        if (!filas.length) {
            container.innerHTML = '<div class="carga-box" style="background:var(--card); border:1px solid var(--line); border-radius:14px; padding:16px 18px;"><h3>⚖️ Carga por Operario</h3><p style="color:var(--mut); font-size:0.82rem;">No hay operarios activos dados de alta en Configuración.</p></div>';
            return;
        }
        const maxTot = Math.max(1, ...filas.map(f => f.asignado || 0));
        let html = `<div class="carga-box" style="background:var(--card); border:1px solid var(--line); border-radius:14px; padding:16px 18px;">
            <h3>⚖️ Carga por Operario <span style="font-weight:400; color:var(--mut); font-size:0.78rem;">— faena prevista total (todas las fechas de carga)</span></h3>
            <table class="carga-table" style="width:100%; border-collapse:collapse; font-size:0.8rem;">
                <thead><tr>
                    <th style="text-align:left; padding:7px 8px; text-transform:uppercase; font-size:0.64rem; color:var(--mut); border-bottom:1px solid var(--line);">Operario</th>
                    <th style="text-align:left; padding:7px 8px; text-transform:uppercase; font-size:0.64rem; color:var(--mut); border-bottom:1px solid var(--line);">Maquinaria</th>
                    <th style="text-align:right; padding:7px 8px; text-transform:uppercase; font-size:0.64rem; color:var(--mut); border-bottom:1px solid var(--line);">Asignado</th>
                    <th style="text-align:right; padding:7px 8px; text-transform:uppercase; font-size:0.64rem; color:var(--mut); border-bottom:1px solid var(--line);">Recogido</th>
                    <th style="text-align:right; padding:7px 8px; text-transform:uppercase; font-size:0.64rem; color:var(--mut); border-bottom:1px solid var(--line);"><b>Total a acopiar</b></th>
                    <th style="text-align:left; padding:7px 8px; text-transform:uppercase; font-size:0.64rem; color:var(--mut); border-bottom:1px solid var(--line);">Equilibrio</th>
                </tr></thead><tbody>`;
        filas.forEach(f => {
            const total = f.asignado || 0;
            const sobrecargado = total >= maxTot * 0.75 && total > 0;
            html += `<tr style="${sobrecargado ? 'background:var(--bad-bg);' : ''}">
                <td style="padding:8px; border-bottom:1px solid var(--line);"><b>${dc.escHtml(f.nombre)}</b></td>
                <td style="padding:8px; border-bottom:1px solid var(--line); font-size:0.74rem; color:var(--mut);">${dc.escHtml(f.maquinaria || '—')}</td>
                <td style="padding:8px; border-bottom:1px solid var(--line); text-align:right;">${f.asignado || 0}</td>
                <td style="padding:8px; border-bottom:1px solid var(--line); text-align:right; color:var(--lime);">${f.recogido || 0}</td>
                <td style="padding:8px; border-bottom:1px solid var(--line); text-align:right; font-weight:700; font-size:0.95rem; ${sobrecargado ? 'color:var(--bad);' : ''}">${total}${sobrecargado ? ' ⚠' : ''}</td>
                <td style="padding:8px; border-bottom:1px solid var(--line);"><div style="background:var(--card2); border-radius:4px; height:8px; overflow:hidden;"><div style="height:100%; width:${Math.round(Math.max(0, total) / maxTot * 100)}%; background:${sobrecargado ? 'var(--bad)' : 'var(--lime)'};"></div></div></td>
            </tr>`;
        });
        html += `</tbody></table>
            <p style="font-size:0.72rem; color:var(--mut); margin-top:8px;">
                <b>Asignado</b>: faena guardada en el servidor (todas las fechas). <b>Recogido</b>: unidades ya pistoleadas (todas las fechas).
                <b>Total a acopiar</b> = asignado. Filas en rojo concentran más faena: valora reasignar.</p></div>`;
        container.innerHTML = html;
    } catch (e) {
        console.error(e);
        container.innerHTML = '<div style="color:var(--bad); text-align:center; padding:20px;">Error al cargar la carga por operario.</div>';
    }
}