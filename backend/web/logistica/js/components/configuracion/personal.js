/**
 * configuracion/personal.js — Submenú "Personal y operarios".
 *
 * Unión de la configuración de personas de /manager:
 *   - Encargados (acceso pistoleo / rol)
 *   - Operarios de Acopio (faena, con maquinaria)
 *   - Operarios de Inventario (modo INVENTARIO/AMBAS)
 *
 * Conserva TODAS las acciones originales: alta/edición/baja con modal completo,
 * búsqueda, selección de fincas y maquinaria por chips, cambio de rol/modo/estado.
 * Regla de unificación: una sola lista de operarios (misma fuente /api/operarios);
 * el filtro por modo separa las vistas de Acopio e Inventario, igual que /manager.
 */
let dc = null;
let rootEl = null;
let encargadosList = [];
let operariosList = [];
let fincasCfgList = [];
let maquinariasList = [];
let familiasList = [];

const _cfgFiltered = { encargados: [], operarios: [], operariosInv: [], fincas: [], maquinarias: [], familias: [] };

function el(id) { return rootEl ? rootEl.querySelector(`#${id}`) : null; }

function initials(nombre, apellidos) {
    const a = (nombre || '?').trim()[0] || '?';
    const b = (apellidos || '').trim()[0] || '';
    return (a + b).toUpperCase();
}

function setCfgCount(elId, n) {
    const e = el(elId);
    if (e) e.textContent = n ? `(${n})` : '';
}

function cfgToggle(id) {
    const sec = el(id);
    if (sec) sec.classList.toggle('open');
}

function cfgGet(tipo, idx) {
    const key = tipo === 'operarioInv' ? 'operariosInv' : tipo;
    return (_cfgFiltered[key] || [])[idx] || null;
}

async function loadAll() {
    try { encargadosList = await dc.fetchEncargados(); } catch (e) { console.error(e); }
    try { operariosList = await dc.fetchOperarios(); } catch (e) { console.error(e); }
    try { fincasCfgList = await dc.fetchFincasGestion(); } catch (e) { console.error(e); }
    try { maquinariasList = await dc.fetchMaquinarias(); } catch (e) { console.error(e); }
    try { familiasList = await dc.fetchFamiliasMaquinaria(); } catch (e) { console.error(e); }
}

function renderEncargadosTable() {
    const cont = el('encargadosListConfig');
    if (!cont) return;
    const q = (el('searchEncargados')?.value || '').toLowerCase();
    const lista = encargadosList.filter(e => !q || `${e.nombre} ${e.apellidos || ''} ${e.email}`.toLowerCase().includes(q));
    _cfgFiltered.encargados = lista;
    setCfgCount('cfgCountEncargados', lista.length);
    if (!lista.length) { cont.innerHTML = '<div style="text-align:center; color:var(--mut); padding:24px;">Sin encargados que coincidan.</div>'; return; }
    let html = '<table class="cfg-table"><thead><tr><th>Persona</th><th>Email</th><th>Rol</th><th>Fincas</th><th>Estado</th><th style="width:90px;">Acciones</th></tr></thead><tbody>';
    lista.forEach((e, i) => {
        const rolBadge = e.modo === 'INVENTARIO' ? 'Inventario' : (e.modo === 'AMBAS' ? 'Ambas' : 'Pistoleo');
        html += `<tr>
            <td><div class="person-cell"><span class="avatar">${dc.escHtml(initials(e.nombre, e.apellidos))}</span><div><b>${dc.escHtml(e.nombre)}</b> <span style="color:var(--mut);">${dc.escHtml(e.apellidos || '')}</span></div></div></td>
            <td style="font-size:0.78rem; color:var(--mut);">${dc.escHtml(e.email)}</td>
            <td><span class="pill ${e.modo === 'AMBAS' ? 'pill-ok' : ''}">${rolBadge}</span></td>
            <td style="font-size:0.72rem; max-width:180px;">${dc.escHtml((e.fincas_carga || '—').split(',').map(s => s.trim()).filter(Boolean).join(', ') || '—')}</td>
            <td><span class="pill ${e.activo !== false ? 'pill-ok' : 'pill-off'}">${e.activo !== false ? 'Activo' : 'Baja'}</span></td>
            <td style="white-space:nowrap;">
                <button class="icon-btn" onclick="window._persOpenPersonModal('encargado', ${i})" title="Editar">✏️</button>
                <button class="icon-btn danger" onclick="window._persEliminarEncargado(${i})" title="Dar de baja / eliminar">🗑️</button>
            </td>
        </tr>`;
    });
    cont.innerHTML = html + '</tbody></table>';
}

function renderOperariosTable() {
    const cont = el('operariosListConfig');
    if (!cont) return;
    const q = (el('searchOperarios')?.value || '').toLowerCase();
    const lista = operariosList.filter(o => !q || `${o.nombre} ${o.apellidos || ''} ${o.email} ${o.maquinaria || ''}`.toLowerCase().includes(q));
    _cfgFiltered.operarios = lista;
    setCfgCount('cfgCountOperarios', lista.length);
    if (!lista.length) { cont.innerHTML = '<div style="text-align:center; color:var(--mut); padding:24px;">Sin operarios que coincidan.</div>'; return; }
    let html = '<table class="cfg-table"><thead><tr><th>Persona</th><th>Email</th><th>Rol</th><th>Maquinaria</th><th>Estado</th><th style="width:90px;">Acciones</th></tr></thead><tbody>';
    lista.forEach((op, i) => {
        const rolBadge = op.modo === 'INVENTARIO' ? 'Inventario' : (op.modo === 'AMBAS' ? 'Ambas' : 'Acopio');
        html += `<tr>
            <td><div class="person-cell"><span class="avatar">${dc.escHtml(initials(op.nombre, op.apellidos))}</span><div><b>${dc.escHtml(op.nombre)}</b> <span style="color:var(--mut);">${dc.escHtml(op.apellidos || '')}</span></div></div></td>
            <td style="font-size:0.78rem; color:var(--mut);">${dc.escHtml(op.email)}</td>
            <td><span class="pill ${op.modo === 'INVENTARIO' || op.modo === 'AMBAS' ? 'pill-ok' : ''}">${rolBadge}</span></td>
            <td style="font-size:0.72rem; max-width:160px;">${dc.escHtml((op.maquinaria || '—').split(',').map(s => s.trim()).filter(Boolean).join(', ') || '—')}</td>
            <td><span class="pill ${op.activo !== false ? 'pill-ok' : 'pill-off'}">${op.activo !== false ? 'Activo' : 'Baja'}</span></td>
            <td style="white-space:nowrap;">
                <button class="icon-btn" onclick="window._persOpenPersonModal('operario', ${i})" title="Editar">✏️</button>
                <button class="icon-btn danger" onclick="window._persEliminarOperario(${i})" title="Eliminar">🗑️</button>
            </td>
        </tr>`;
    });
    cont.innerHTML = html + '</tbody></table>';
}

function renderOperariosInvTable() {
    const cont = el('operariosInvListConfig');
    if (!cont) return;
    const q = (el('searchOperariosInv')?.value || '').toLowerCase();
    const filtro = el('filtroActivoInv')?.value || 'todos';
    const lista = operariosList.filter(op => {
        const modoInv = (op.modo || 'ACOPIO') === 'INVENTARIO' || (op.modo || 'ACOPIO') === 'AMBAS';
        if (!modoInv) return false;
        if (q && !`${op.nombre} ${op.apellidos || ''} ${op.email}`.toLowerCase().includes(q)) return false;
        if (filtro === 'activos' && op.activo === false) return false;
        if (filtro === 'inactivos' && op.activo !== false) return false;
        return true;
    });
    _cfgFiltered.operariosInv = lista;
    setCfgCount('cfgCountOperariosInv', lista.length);
    if (!lista.length) { cont.innerHTML = '<div style="text-align:center; color:var(--mut); padding:24px;">Sin operarios de inventario que coincidan.</div>'; return; }
    let html = '<table class="cfg-table"><thead><tr><th>Persona</th><th>Email</th><th>Rol</th><th>Fincas</th><th>Estado</th><th style="width:90px;">Acciones</th></tr></thead><tbody>';
    lista.forEach((op, i) => {
        const rolBadge = op.modo === 'AMBAS' ? 'Ambas' : 'Inventario';
        html += `<tr>
            <td><div class="person-cell"><span class="avatar">${dc.escHtml(initials(op.nombre, op.apellidos))}</span><div><b>${dc.escHtml(op.nombre)}</b> <span style="color:var(--mut);">${dc.escHtml(op.apellidos || '')}</span></div></div></td>
            <td style="font-size:0.78rem; color:var(--mut);">${dc.escHtml(op.email)}</td>
            <td><span class="pill pill-ok">${rolBadge}</span></td>
            <td style="font-size:0.72rem; max-width:180px;">${dc.escHtml((op.fincas_carga || '—').split(',').map(s => s.trim()).filter(Boolean).join(', ') || '—')}</td>
            <td><span class="pill ${op.activo !== false ? 'pill-ok' : 'pill-off'}">${op.activo !== false ? 'Activo' : 'Baja'}</span></td>
            <td style="white-space:nowrap;">
                <button class="icon-btn" onclick="window._persOpenPersonModal('operarioInv', ${i})" title="Editar">✏️</button>
                <button class="icon-btn danger" onclick="window._persEliminarOperarioInv(${i})" title="Eliminar">🗑️</button>
            </td>
        </tr>`;
    });
    cont.innerHTML = html + '</tbody></table>';
}

// ---------- Modal persona ----------
function chipsSelect(containerId, items, seleccionadas) {
    const cont = el(containerId);
    if (!cont) return;
    const sel = new Set((seleccionadas || []).map(s => String(s).trim()).filter(Boolean));
    cont.innerHTML = items.length
        ? items.map(v => `<span class="chip-sel${sel.has(v) ? ' on' : ''}" data-val="${dc.escHtml(v)}" onclick="this.classList.toggle('on'); this.dataset.on=this.classList.contains('on');">${dc.escHtml(v)}</span>`).join('')
        : '<span style="color:var(--mut); font-size:0.78rem;">No hay elementos dados de alta.</span>';
}

function chipsSelected(containerId) {
    const cont = el(containerId);
    return cont ? [...cont.querySelectorAll('.chip-sel.on')].map(x => x.dataset.val) : [];
}

function togglePwd(inputId, btn) {
    const inp = el(inputId);
    if (!inp) return;
    inp.type = inp.type === 'password' ? 'text' : 'password';
    if (btn) btn.textContent = inp.type === 'password' ? '👁️' : '🙈';
}

async function openPersonModal(tipo, idx) {
    const key = tipo === 'operarioInv' ? 'operariosInv' : (tipo === 'encargado' ? 'encargados' : 'operarios');
    const data = idx === undefined || idx === null ? null : _cfgFiltered[key][idx];
    const esOp = tipo === 'operario' || tipo === 'operarioInv';
    const esEnc = tipo === 'encargado';
    el('pmTipo').value = tipo;
    el('pmOriginalEmail').value = data?.email || '';
    el('pmNombre').value = data?.nombre || '';
    el('pmApellidos').value = data?.apellidos || '';
    el('pmEmail').value = data?.email || '';
    el('pmPass').value = '';
    el('pmPass').type = 'password';
    el('pmRol').value = data?.rol && data.rol !== 'ENCARGADO' ? data.rol : 'ENCARGADO';
    const modoVal = data?.modo || (tipo === 'operarioInv' ? 'INVENTARIO' : (esOp ? 'ACOPIO' : 'PICKING'));
    const pmModo = el('pmModo');
    if (esOp) pmModo.innerHTML = '<option value="ACOPIO">Acopio</option><option value="INVENTARIO">Inventario</option><option value="AMBAS">Ambas</option>';
    else pmModo.innerHTML = '<option value="PICKING">Pistoleo</option><option value="INVENTARIO">Inventario</option><option value="AMBAS">Ambas</option>';
    pmModo.value = ['PICKING', 'INVENTARIO', 'AMBAS', 'ACOPIO'].includes(modoVal) ? modoVal : (esOp ? 'ACOPIO' : 'PICKING');
    el('pmActivo').value = data ? String(data.activo !== false) : 'true';
    const esInventario = tipo === 'operarioInv';
    el('personModalTitle').textContent = esInventario
        ? (data ? `Editar operario inventario — ${data.nombre}` : 'Nuevo operario inventario')
        : (esEnc ? (data ? `Editar encargado — ${data.nombre}` : 'Nuevo encargado') : (data ? `Editar operario — ${data.nombre}` : 'Nuevo operario'));
    el('personModalSub').textContent = esInventario
        ? 'Operario que realiza inventario (sin maquinaria de acopio).'
        : (esEnc ? 'Empleado con acceso a la app de pistoleo.' : 'Trabajador de carga al que se le reparte faena.');
    el('pmPassHint').textContent = data ? '(vacío = no cambia)' : '(vacío = por defecto "1234")';
    el('pmMaquinariaField').style.display = esOp && !esInventario ? '' : 'none';
    el('pmRolField').style.display = esEnc ? '' : 'none';
    el('pmFincasField').style.display = esEnc ? '' : 'none';
    el('pmModoField').style.display = '';
    const nombresFinca = fincasCfgList.filter(f => !f.oculto).map(f => f.nombre || f.finca);
    chipsSelect('pmFincas', nombresFinca, (data?.fincas_carga || '').split(','));
    chipsSelect('pmMaquinaria', maquinariasList.filter(m => m.activo !== false).map(m => m.nombre), (data?.maquinaria || '').split(','));
    el('personModal').classList.add('open');
}

async function guardarPersonModal() {
    const tipo = el('pmTipo').value;
    const nombre = el('pmNombre').value.trim();
    const apellidos = el('pmApellidos').value.trim();
    const emailOriginal = el('pmOriginalEmail').value.trim();
    const email = el('pmEmail').value.trim();
    const password = el('pmPass').value.trim();
    const activo = el('pmActivo').value === 'true';
    const fincas = chipsSelected('pmFincas').join(', ');
    if (!nombre || !email) { alert('Nombre y Email son obligatorios'); return; }
    try {
        if (tipo === 'encargado') {
            await dc.guardarEncargado({
                id: emailOriginal || undefined, nombre, apellidos, email, password,
                fincas_carga: fincas, rol: el('pmRol').value, modo: el('pmModo').value, activo,
            });
        } else {
            await dc.guardarOperario({
                email, nombre, apellidos, password, fincas_carga: fincas,
                maquinaria: chipsSelected('pmMaquinaria').join(', '), modo: el('pmModo').value, activo,
            });
        }
        el('personModal').classList.remove('open');
        await refresh();
    } catch (e) {
        console.error(e);
        alert('Error de red al guardar.');
    }
}

async function eliminarOperarioGeneric(idx, tipo) {
    const key = tipo === 'operarioInv' ? 'operariosInv' : 'operarios';
    const op = _cfgFiltered[key][idx];
    if (!op) return;
    if (!confirm(`¿Eliminar definitivamente al operario ${op.nombre} (${op.email})?`)) return;
    try {
        await dc.eliminarOperario({ email: op.email });
        await refresh();
    } catch (e) { console.error(e); alert('Error de red al eliminar el operario'); }
}

async function eliminarEncargado(idx) {
    const e = _cfgFiltered.encargados[idx];
    if (!e) return;
    if (!confirm(`¿Qué hacer con el encargado ${e.nombre}?\n\nAceptar = dar de baja (conserva histórico). Para reactivarlo usa Editar → Activo.`)) return;
    try {
        await dc.guardarEncargado({ nombre: e.nombre, apellidos: e.apellidos || '', email: e.email, activo: false });
        await refresh();
    } catch (e) { console.error(e); alert('Error de red al dar de baja al encargado'); }
}

async function refresh() {
    await loadAll();
    renderEncargadosTable();
    renderOperariosTable();
    renderOperariosInvTable();
}

export async function renderPersonal(root, dataConnector) {
    if (!root || !dataConnector) return;
    dc = dataConnector;
    rootEl = root;
    root.innerHTML = `
        <div class="config-section">
            <div class="cfg-section" id="cfgSecEncargados">
                <div class="cfg-section-head" onclick="window._persCfgToggle('cfgSecEncargados')">
                    <h3>👥 Encargados <span class="cfg-count" id="cfgCountEncargados"></span></h3>
                    <div style="display:flex; gap:10px; align-items:center;">
                        <button class="btn-primary-modern" onclick="event.stopPropagation(); window._persOpenPersonModal('encargado')">＋ Nuevo encargado</button>
                        <span class="cfg-chevron">▶</span>
                    </div>
                </div>
                <div class="cfg-section-body">
                    <div class="cfg-toolbar"><input type="text" id="searchEncargados" class="cfg-search" placeholder="🔍 Buscar por nombre o email..." oninput="window._persRenderTables()"></div>
                    <div id="encargadosListConfig" class="cfg-table-wrap"></div>
                </div>
            </div>

            <div class="cfg-section" id="cfgSecOperarios">
                <div class="cfg-section-head" onclick="window._persCfgToggle('cfgSecOperarios')">
                    <h3>👷 Operarios (Acopio) <span class="cfg-count" id="cfgCountOperarios"></span></h3>
                    <div style="display:flex; gap:10px; align-items:center;">
                        <button class="btn-primary-modern" onclick="event.stopPropagation(); window._persOpenPersonModal('operario')">＋ Nuevo operario</button>
                        <span class="cfg-chevron">▶</span>
                    </div>
                </div>
                <div class="cfg-section-body">
                    <div class="cfg-toolbar"><input type="text" id="searchOperarios" class="cfg-search" placeholder="🔍 Buscar por nombre, email o maquinaria..." oninput="window._persRenderTables()"></div>
                    <div id="operariosListConfig" class="cfg-table-wrap"></div>
                </div>
            </div>

            <div class="cfg-section" id="cfgSecOperariosInv">
                <div class="cfg-section-head" onclick="window._persCfgToggle('cfgSecOperariosInv')">
                    <h3>👥 Operarios (Inventario) <span class="cfg-count" id="cfgCountOperariosInv"></span></h3>
                    <div style="display:flex; gap:10px; align-items:center;">
                        <button class="btn-primary-modern" onclick="event.stopPropagation(); window._persOpenPersonModal('operarioInv')">＋ Nuevo operario inventario</button>
                        <span class="cfg-chevron">▶</span>
                    </div>
                </div>
                <div class="cfg-section-body">
                    <div class="cfg-toolbar">
                        <input type="text" id="searchOperariosInv" class="cfg-search" placeholder="🔍 Buscar por nombre, email..." oninput="window._persRenderTables()">
                        <select id="filtroActivoInv" onchange="window._persRenderTables()" style="min-width:150px;">
                            <option value="todos">Todos (activos e inactivos)</option>
                            <option value="activos">Solo activos</option>
                            <option value="inactivos">Solo inactivos</option>
                        </select>
                    </div>
                    <div id="operariosInvListConfig" class="cfg-table-wrap"></div>
                </div>
            </div>
        </div>

        <div class="modal" id="personModal">
            <div class="modal-content modern">
                <div class="mform-head">
                    <div><h2 id="personModalTitle">Nuevo</h2><p id="personModalSub"></p></div>
                    <button class="close-btn" onclick="this.closest('.modal').classList.remove('open')">✕</button>
                </div>
                <div class="mform-body">
                    <input type="hidden" id="pmTipo" value="">
                    <input type="hidden" id="pmOriginalEmail" value="">
                    <div class="field-row">
                        <div class="field"><label for="pmNombre">Nombre *</label><input type="text" id="pmNombre" placeholder="Ej.: Juan"></div>
                        <div class="field"><label for="pmApellidos">Apellidos</label><input type="text" id="pmApellidos" placeholder="Ej.: García Pérez"></div>
                    </div>
                    <div class="field"><label for="pmEmail">Email / Usuario *</label><input type="email" id="pmEmail" placeholder="nombre@viveros.com" autocomplete="off"></div>
                    <div class="field"><label for="pmPass">Contraseña <span id="pmPassHint" style="text-transform:none; letter-spacing:0; color:var(--mut); font-weight:400;"></span></label>
                        <div class="pwd-wrap"><input type="password" id="pmPass" placeholder="••••••••" autocomplete="new-password"><button type="button" class="eye-btn" onclick="window._persTogglePwd('pmPass', this)" title="Mostrar / ocultar contraseña">👁️</button></div>
                    </div>
                    <div class="field" id="pmFincasField"><label>Fincas asignadas</label><div class="chips-select" id="pmFincas"></div></div>
                    <div class="field" id="pmMaquinariaField"><label>Maquinaria que lleva</label><div class="chips-select" id="pmMaquinaria"></div></div>
                    <div class="field-row">
                        <div class="field" id="pmRolField"><label for="pmRol">Nivel de acceso</label>
                            <select id="pmRol"><option value="ENCARGADO">Encargado</option><option value="SUPERUSUARIO">Superusuario</option></select>
                        </div>
                        <div class="field" id="pmModoField"><label for="pmModo">Rol de trabajo</label><select id="pmModo"></select></div>
                        <div class="field"><label>Estado</label>
                            <select id="pmActivo"><option value="true">Activo</option><option value="false">Dado de baja</option></select>
                        </div>
                    </div>
                </div>
                <div class="mform-foot">
                    <button class="btn-sec" onclick="this.closest('.modal').classList.remove('open')">Cancelar</button>
                    <button class="btn-primary-modern" onclick="window._persGuardarPersona()">💾 Guardar</button>
                </div>
            </div>
        </div>`;

    // Handlers globales (scoped al portal).
    window._persCfgToggle = cfgToggle;
    window._persOpenPersonModal = openPersonModal;
    window._persGuardarPersona = guardarPersonModal;
    window._persRenderTables = () => { renderEncargadosTable(); renderOperariosTable(); renderOperariosInvTable(); };
    window._persTogglePwd = togglePwd;
    window._persEliminarEncargado = eliminarEncargado;
    window._persEliminarOperario = (i) => eliminarOperarioGeneric(i, 'operario');
    window._persEliminarOperarioInv = (i) => eliminarOperarioGeneric(i, 'operarioInv');

    // Cerrar modales al hacer clic fuera.
    rootEl.querySelectorAll('.modal').forEach(m => m.addEventListener('click', e => { if (e.target === m) m.classList.remove('open'); }));

    await refresh();
}