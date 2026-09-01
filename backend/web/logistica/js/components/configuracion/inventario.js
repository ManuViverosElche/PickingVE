/**
 * configuracion/inventario.js — Submenú "Configuración de inventario".
 *
 * Réplica COMPLETA de la pestaña Configuración de /inventario:
 *   - Fincas propias (inventariables / ocultas) con checkbox.
 *   - Faena a Inventariar: asignación de operarios de inventario por finca y sector.
 *   - Operarios Autorizados para Inventario (lista, misma fuente que /api/operarios).
 * Conserva todas las acciones y endpoints originales.
 */
let dc = null;
let rootEl = null;
let fincasCache = [];
let operariosInv = [];
let operariosList = [];

function el(id) { return rootEl ? rootEl.querySelector(`#${id}`) : null; }

function cfgToggleInv(id) {
    const sec = el(id);
    if (sec) sec.classList.toggle('open');
}

function toggleFaenaFinca(id, head) {
    const elm = el(id);
    if (!elm) return;
    const abrir = elm.style.display === 'none';
    elm.style.display = abrir ? 'block' : 'none';
    const chev = head ? head.querySelector('.cfg-chevron') : null;
    if (chev) chev.style.transform = abrir ? 'rotate(90deg)' : '';
}

async function cargarConfiguracion() {
    try {
        const d = await dc.fetchFincasConfig();
        const cfFin = el('cfgFincasLista');
        const fincas = (d.fincas || []).filter(f => f.propia);
        if (!fincas.length) {
            cfFin.innerHTML = '<div class="vacio">No hay fincas propias.</div>';
        } else {
            cfFin.innerHTML = '<div class="table-scroll"><table><thead><tr><th style="width:90px;">Inventariar</th><th>Finca</th><th>Estado</th></tr></thead><tbody>' +
                fincas.map(f => {
                    const badge = f.activa
                        ? '<span class="estado-badge badge-ok">Inventariable</span>'
                        : '<span class="estado-badge badge-abierto">Oculta</span>';
                    return `<tr><td><input type="checkbox" class="chkFincaInv" data-finca="${dc.escHtml(f.finca)}" ${f.activa ? 'checked' : ''}></td><td><b>${dc.escHtml(f.finca)}</b></td><td>${badge}</td></tr>`;
                }).join('') + '</tbody></table></div>';
            cfFin.querySelectorAll('.chkFincaInv').forEach(chk => {
                chk.addEventListener('change', async () => {
                    try {
                        await dc.setFincaInventariable({ finca: chk.getAttribute('data-finca'), activa: chk.checked });
                        await cargarConfiguracion();
                        await cargarFincas();
                    } catch (e) { alert('Error al actualizar: ' + e.message); }
                });
            });
        }
    } catch (e) {
        el('cfgFincasLista').innerHTML = '<div class="vacio">No se pudieron cargar las fincas.</div>';
    }

    try {
        operariosInv = await dc.fetchOperariosInventario();
        const cfOp = el('cfgOperariosLista');
        cfOp.innerHTML = '<div class="table-scroll"><table><thead><tr><th>Nombre</th><th>Email</th><th>Rol</th></tr></thead><tbody>' +
            operariosInv.map(op => {
                const rol = (op.rol || op.modo || 'ACOPIO').toUpperCase();
                return `<tr><td><b>${dc.escHtml(op.nombre)}</b></td><td>${dc.escHtml(op.email)}</td><td><span class="estado-badge badge-ok">${dc.escHtml(rol)}</span></td></tr>`;
            }).join('') + '</tbody></table></div>';

        const cfFaena = el('cfgFaenaLista');
        const activas = fincasCache || [];
        if (!activas.length) { cfFaena.innerHTML = '<div class="vacio">Carga las fincas primero.</div>'; return; }
        const inventariables = operariosInv;
        let html = '<div style="margin-bottom:12px; display:flex; justify-content:flex-end;">' +
            '<button class="btn-primary-modern" onclick="window._cfgInvGuardarFaena()">💾 Guardar reparto de inventario</button></div>';
        activas.forEach((f, fi) => {
            const fid = 'faenafinca-' + fi;
            const sects = f.sectores || [];
            html += '<div class="cfg-faena-finca">' +
                `<div class="cfg-faena-head" onclick="window._cfgInvToggleFaenaFinca('${fid}', this)">` +
                `<b>Finca: ${dc.escHtml(f.finca)} <span style="font-weight:400;color:var(--mut);">(${sects.length} sectores)</span></b>` +
                '<span class="cfg-chevron" style="font-size:11px;">▶</span></div>' +
                `<div class="cfg-faena-body" id="${fid}"><div class="cfg-faena-scroll">`;
            if (sects.length) {
                sects.forEach(s => {
                    const opsSel = (s.operarios || []).map(o => o.email);
                    const badge = s.cerrado
                        ? '<span class="estado-badge badge-cerrado">&#10003; Cerrado</span>'
                        : (s.tieneInventario ? '<span class="estado-badge badge-abierto">&#9888; Abierto</span>' : (s.asignado ? '<span class="estado-badge badge-asignado">&#128221; Asignado</span>' : ''));
                    html += '<div class="cfg-faena-sector">' +
                        `<div class="cfg-faena-sec-head"><span class="sec-label">Sector: ${dc.escHtml(s.descripcion || s.id)}</span>${badge}</div>` +
                        '<div class="cfg-faena-checklist">' + inventariables.map(op => {
                            return `<label><input type="checkbox" class="chkFaena" value="${dc.escHtml(op.email)}" data-finca="${dc.escHtml(f.finca)}" data-sector="${dc.escHtml(s.id)}" ${opsSel.indexOf(op.email) >= 0 ? 'checked' : ''}> ${dc.escHtml(op.nombre)}</label>`;
                        }).join('') + '</div></div>';
                });
            } else {
                const opsSelF = (f.operarios || []).map(o => o.email);
                const badgeF = f.tieneInventario ? '<span class="estado-badge badge-abierto">&#9888; Abierto</span>' : '';
                html += '<div class="cfg-faena-sector">' +
                    `<div class="cfg-faena-sec-head"><span class="sec-label">Sin sectores (finca entera)</span>${badgeF}</div>` +
                    '<div class="cfg-faena-checklist">' + inventariables.map(op => {
                        return `<label><input type="checkbox" class="chkFaena" value="${dc.escHtml(op.email)}" data-finca="${dc.escHtml(f.finca)}" data-sector="" ${opsSelF.indexOf(op.email) >= 0 ? 'checked' : ''}> ${dc.escHtml(op.nombre)}</label>`;
                    }).join('') + '</div></div>';
            }
            html += '</div></div></div>';
        });
        cfFaena.innerHTML = html || '<div class="vacio">No hay fincas activas.</div>';
    } catch (e) {
        el('cfgOperariosLista').innerHTML = '<div class="vacio">No se pudieron cargar los operarios.</div>';
        el('cfgFaenaLista').innerHTML = '<div class="vacio">No se pudieron cargar los operarios.</div>';
    }
}

async function cargarFincas() {
    try {
        fincasCache = await dc.fetchInventarioFincas();
    } catch (e) {
        fincasCache = [];
    }
}

async function guardarFaenaGlobal() {
    const grupos = {};
    rootEl.querySelectorAll('#cfgFaenaLista .chkFaena').forEach(chk => {
        const finca = chk.getAttribute('data-finca');
        const sector = chk.getAttribute('data-sector');
        const key = finca + '|' + sector;
        if (!grupos[key]) grupos[key] = { finca, sector, operarios: [] };
        if (chk.checked) grupos[key].operarios.push(chk.value);
    });
    const asignaciones = Object.keys(grupos).map(k => grupos[k]);
    try {
        const res = await dc.guardarFaenaInventario({ asignaciones });
        alert(`Reparto de inventario guardado (${res.asignados || 0} asignaciones en ${res.fincas || 0} fincas).`);
        await cargarConfiguracion();
        await cargarFincas();
    } catch (e) { alert('Error al guardar el reparto: ' + e.message); }
}

export async function renderConfigInventario(root, dataConnector) {
    if (!root || !dataConnector) return;
    dc = dataConnector;
    rootEl = root;
    root.innerHTML = `
        <div class="config-section">
            <div class="cfg-section" id="cfgSecFincas">
                <div class="cfg-section-head" onclick="window._cfgInvToggle('cfgSecFincas')">
                    <h3>Fincas (propias)</h3>
                    <span class="cfg-chevron">▶</span>
                </div>
                <div class="cfg-section-body"><div id="cfgFincasLista"></div></div>
            </div>
            <div class="cfg-section" id="cfgSecFaena">
                <div class="cfg-section-head" onclick="window._cfgInvToggle('cfgSecFaena')">
                    <h3>Faena a Inventariar (por finca y sector)</h3>
                    <span class="cfg-chevron">▶</span>
                </div>
                <div class="cfg-section-body"><div id="cfgFaenaLista"></div></div>
            </div>
            <div class="cfg-section" id="cfgSecOperarios">
                <div class="cfg-section-head" onclick="window._cfgInvToggle('cfgSecOperarios')">
                    <h3>Operarios Autorizados para Inventario</h3>
                    <span class="cfg-chevron">▶</span>
                </div>
                <div class="cfg-section-body"><div id="cfgOperariosLista"></div></div>
            </div>
        </div>`;

    window._cfgInvToggle = cfgToggleInv;
    window._cfgInvToggleFaenaFinca = toggleFaenaFinca;
    window._cfgInvGuardarFaena = guardarFaenaGlobal;

    await cargarFincas();
    await cargarConfiguracion();
}