/**
 * configuracion/logistica.js — Submenú "Configuración de logística y faena".
 *
 * Configuración de /manager (logística): Fincas (visibles/ocultas, propias/ajenas),
 * Maquinaria de carga (catálogo) y Familias de maquinaria. Conserva todas las
 * acciones originales (alta/edición/eliminar con modales, búsquedas).
 */
let dc = null;
let rootEl = null;
let fincasCfgList = [];
let maquinariasList = [];
let familiasList = [];

const _cfgFiltered = { fincas: [], maquinarias: [], familias: [] };

function el(id) { return rootEl ? rootEl.querySelector(`#${id}`) : null; }

function setCfgCount(elId, n) {
    const e = el(elId);
    if (e) e.textContent = n ? `(${n})` : '';
}

function cfgToggle(id) {
    const sec = el(id);
    if (sec) sec.classList.toggle('open');
}

function cfgGet(tipo, idx) {
    return (_cfgFiltered[tipo] || [])[idx] || null;
}

async function loadAll() {
    try { fincasCfgList = await dc.fetchFincasGestion(); } catch (e) { console.error(e); }
    try { maquinariasList = await dc.fetchMaquinarias(); } catch (e) { console.error(e); }
    try { familiasList = await dc.fetchFamiliasMaquinaria(); } catch (e) { console.error(e); }
}

function renderFincasTable() {
    const cont = el('fincasListConfig');
    if (!cont) return;
    const q = (el('searchFincasCfg')?.value || '').toLowerCase();
    const lista = fincasCfgList.filter(f => !q || `${f.finca} ${f.nombre}`.toLowerCase().includes(q));
    _cfgFiltered.fincas = lista;
    if (!lista.length) { cont.innerHTML = '<div style="text-align:center; color:var(--mut); padding:24px;">Sin fincas que coincidan.</div>'; return; }
    let html = '<table class="cfg-table"><thead><tr><th>Finca</th><th>Nombre mostrado</th><th>Tipo</th><th>Propia</th><th>Estado</th><th style="width:120px;">Acciones</th></tr></thead><tbody>';
    lista.forEach((f, i) => {
        html += `<tr>
            <td><b>${dc.escHtml(f.finca)}</b></td>
            <td style="color:var(--blue);">${dc.escHtml(f.nombre || f.finca)}</td>
            <td><span class="pill">${f.manual ? 'Manual' : 'Auto (pedidos)'}</span></td>
            <td><span class="pill ${f.propia ? 'pill-ok' : 'pill-off'}">${f.propia ? 'Propia' : 'Ajena'}</span></td>
            <td><span class="pill ${f.oculto ? 'pill-off' : 'pill-ok'}">${f.oculto ? 'Oculta' : 'Visible'}</span></td>
            <td style="white-space:nowrap;">
                <button class="icon-btn" onclick="window._logOpenFincaModal(${i})" title="Editar">✏️</button>
                <button class="icon-btn danger" onclick="window._logEliminarFinca(${i})" title="${f.manual ? 'Eliminar' : 'Ocultar'}">${f.manual ? '🗑️' : '🙈'}</button>
            </td>
        </tr>`;
    });
    cont.innerHTML = html + '</tbody></table>';
    setCfgCount('cfgCountFincas', lista.length);
}

function renderMaquinariasTable() {
    const cont = el('maquinariasListConfig');
    if (!cont) return;
    const q = (el('searchMaquinarias')?.value || '').toLowerCase();
    const lista = maquinariasList.filter(m => !q || `${m.nombre} ${m.descripcion} ${m.familia || ''}`.toLowerCase().includes(q));
    _cfgFiltered.maquinarias = lista;
    setCfgCount('cfgCountMaquinarias', lista.length);
    if (!lista.length) { cont.innerHTML = '<div style="text-align:center; color:var(--mut); padding:24px;">Sin maquinaria dada de alta todavía.</div>'; return; }
    let html = '<table class="cfg-table"><thead><tr><th style="width:22%;">Nombre</th><th style="width:18%;">Familia</th><th>Descripción</th><th style="width:12%;">Estado</th><th style="width:110px;">Acciones</th></tr></thead><tbody>';
    lista.forEach((m, i) => {
        html += `<tr>
            <td><b>${dc.escHtml(m.nombre)}</b></td>
            <td>${m.familia ? `<span class="pill">${dc.escHtml(m.familia)}</span>` : '<span style="color:var(--mut);">—</span>'}</td>
            <td style="font-size:0.76rem; color:var(--mut);">${dc.escHtml(m.descripcion || '—')}</td>
            <td><span class="pill ${m.activo !== false ? 'pill-ok' : 'pill-off'}">${m.activo !== false ? 'Activa' : 'Inactiva'}</span></td>
            <td style="white-space:nowrap;">
                <button class="icon-btn" onclick="window._logOpenMaquinariaModal(${i})" title="Editar">✏️</button>
                <button class="icon-btn danger" onclick="window._logEliminarMaquinaria(${i})" title="Eliminar">🗑️</button>
            </td>
        </tr>`;
    });
    cont.innerHTML = html + '</tbody></table>';
}

function renderFamiliasTable() {
    const cont = el('familiasListConfig');
    if (!cont) return;
    const q = (el('searchFamilias')?.value || '').toLowerCase();
    const lista = familiasList.filter(f => !q || `${f.nombre} ${f.descripcion}`.toLowerCase().includes(q));
    _cfgFiltered.familias = lista;
    setCfgCount('cfgCountFamilias', lista.length);
    if (!lista.length) { cont.innerHTML = '<div style="text-align:center; color:var(--mut); padding:24px;">Sin familias dadas de alta todavía.</div>'; return; }
    let html = '<table class="cfg-table"><thead><tr><th style="width:26%;">Nombre</th><th>Descripción</th><th style="width:16%;">Maquinarias</th><th style="width:12%;">Estado</th><th style="width:110px;">Acciones</th></tr></thead><tbody>';
    lista.forEach((f, i) => {
        const nMq = maquinariasList.filter(m => m.familia === f.nombre).length;
        html += `<tr>
            <td><b>${dc.escHtml(f.nombre)}</b></td>
            <td style="font-size:0.76rem; color:var(--mut);">${dc.escHtml(f.descripcion || '—')}</td>
            <td><span class="pill">${nMq} maq.</span></td>
            <td><span class="pill ${f.activo !== false ? 'pill-ok' : 'pill-off'}">${f.activo !== false ? 'Activa' : 'Inactiva'}</span></td>
            <td style="white-space:nowrap;">
                <button class="icon-btn" onclick="window._logOpenFamiliaModal(${i})" title="Editar">✏️</button>
                <button class="icon-btn danger" onclick="window._logEliminarFamilia(${i})" title="Eliminar">🗑️</button>
            </td>
        </tr>`;
    });
    cont.innerHTML = html + '</tbody></table>';
}

// ---------- Modales ----------
function openFincaModal(idx) {
    const f = idx === undefined || idx === null ? null : cfgGet('fincas', idx);
    el('fmOriginal').value = f?.finca || '';
    el('fmNombreReal').value = f?.finca || '';
    el('fmNombreReal').disabled = !!f;
    el('fmNombreMostrar').value = (f && f.nombre !== f.finca) ? (f.nombre || '') : '';
    el('fmPropia').value = f ? String(f.propia !== false) : 'true';
    el('fmActivo').value = f ? String(!f.oculto) : 'true';
    el('fincaModalTitle').textContent = f ? `Editar finca — ${f.finca}` : 'Nueva finca';
    el('fincaModal').classList.add('open');
}

async function guardarFincaModal() {
    const original = el('fmOriginal').value.trim();
    const finca = (el('fmNombreReal').value || original).trim();
    const nombreMostrar = el('fmNombreMostrar').value.trim();
    const activo = el('fmActivo').value === 'true';
    const propia = el('fmPropia').value === 'true';
    if (!finca) { alert('Indica el nombre de la finca'); return; }
    try {
        await dc.guardarFinca({ finca, nombre: nombreMostrar || null, activo });
        try { await dc.setFincaPropia({ finca, propia }); } catch (e) { console.error('Error al guardar tipo de finca', e); }
        el('fincaModal').classList.remove('open');
        await refresh();
    } catch (e) { console.error(e); alert('Error de red'); }
}

async function eliminarFincaCfg(idx) {
    const f = cfgGet('fincas', idx);
    if (!f) return;
    const finca = f.finca;
    const msg = f.manual
        ? `¿Eliminar definitivamente la finca manual "${finca}"?`
        : `¿Ocultar la finca "${finca}" para los empleados? Las cargas siguen entrando; podrás volver a mostrarla.`;
    if (!confirm(msg)) return;
    try {
        await dc.eliminarFinca({ finca });
        await refresh();
    } catch (e) { console.error(e); alert('Error de red al eliminar la finca'); }
}

function openMaquinariaModal(idx) {
    const m = idx === undefined || idx === null ? null : cfgGet('maquinarias', idx);
    el('mmId').value = m?.id || '';
    el('mmNombre').value = m?.nombre || '';
    const selFam = el('mmFamilia');
    selFam.innerHTML = '<option value="">— Sin familia —</option>' +
        familiasList.filter(f => f.activo !== false).map(f => `<option value="${dc.escHtml(f.nombre)}">${dc.escHtml(f.nombre)}</option>`).join('');
    selFam.value = m?.familia || '';
    if (m?.familia && !selFam.value) {
        const opt = document.createElement('option');
        opt.value = m.familia; opt.textContent = m.familia + ' (inactiva)';
        selFam.appendChild(opt); selFam.value = m.familia;
    }
    el('mmDescripcion').value = m?.descripcion || '';
    el('mmActivo').value = m ? String(m.activo !== false) : 'true';
    el('maquinariaModalTitle').textContent = m ? `Editar maquinaria — ${m.nombre}` : 'Nueva maquinaria';
    el('maquinariaModal').classList.add('open');
}

async function guardarMaquinariaModal() {
    const id = el('mmId').value.trim();
    const nombre = el('mmNombre').value.trim();
    const descripcion = el('mmDescripcion').value.trim();
    const familia = el('mmFamilia').value;
    const activo = el('mmActivo').value === 'true';
    if (!nombre) { alert('Indica el nombre de la maquinaria'); return; }
    try {
        await dc.guardarMaquinaria({ id: id || null, nombre, descripcion, familia, activo });
        el('maquinariaModal').classList.remove('open');
        await refresh();
    } catch (e) { console.error(e); alert('Error de red'); }
}

async function eliminarMaquinaria(idx) {
    const m = cfgGet('maquinarias', idx);
    if (!m || !m.id) return;
    if (!confirm(`¿Eliminar la maquinaria "${m.nombre}"?`)) return;
    try {
        await dc.eliminarMaquinaria({ id: m.id });
        await refresh();
    } catch (e) { console.error(e); alert('Error de red al eliminar la maquinaria'); }
}

function openFamiliaModal(idx) {
    const f = idx === undefined || idx === null ? null : cfgGet('familias', idx);
    el('famId').value = f?.id || '';
    el('famNombre').value = f?.nombre || '';
    el('famDescripcion').value = f?.descripcion || '';
    el('famActivo').value = f ? String(f.activo !== false) : 'true';
    el('familiaModalTitle').textContent = f ? `Editar familia — ${f.nombre}` : 'Nueva familia';
    el('familiaModal').classList.add('open');
}

async function guardarFamiliaModal() {
    const id = el('famId').value.trim();
    const nombre = el('famNombre').value.trim();
    const descripcion = el('famDescripcion').value.trim();
    const activo = el('famActivo').value === 'true';
    if (!nombre) { alert('Indica el nombre de la familia'); return; }
    try {
        await dc.guardarFamiliaMaquinaria({ id: id || null, nombre, descripcion, activo });
        el('familiaModal').classList.remove('open');
        await refresh();
    } catch (e) { console.error(e); alert('Error de red'); }
}

async function eliminarFamilia(idx) {
    const f = cfgGet('familias', idx);
    if (!f || !f.id) return;
    const nMq = maquinariasList.filter(m => m.familia === f.nombre).length;
    const extra = nMq ? `\n\nHay ${nMq} maquinaria(s) asignada(s) a esta familia; quedarán sin familia.` : '';
    if (!confirm(`¿Eliminar la familia "${f.nombre}"?${extra}`)) return;
    try {
        await dc.eliminarFamiliaMaquinaria({ id: f.id });
        await refresh();
    } catch (e) { console.error(e); alert('Error de red al eliminar la familia'); }
}

async function refresh() {
    await loadAll();
    renderFincasTable();
    renderMaquinariasTable();
    renderFamiliasTable();
}

export async function renderConfigLogistica(root, dataConnector) {
    if (!root || !dataConnector) return;
    dc = dataConnector;
    rootEl = root;
    root.innerHTML = `
        <div class="config-section">
            <div class="cfg-section" id="cfgSecFincas">
                <div class="cfg-section-head" onclick="window._logCfgToggle('cfgSecFincas')">
                    <h3>📍 Fincas <span class="cfg-count" id="cfgCountFincas"></span></h3>
                    <div style="display:flex; gap:10px; align-items:center;">
                        <button class="btn-primary-modern" onclick="event.stopPropagation(); window._logOpenFincaModal()">＋ Nueva finca</button>
                        <span class="cfg-chevron">▶</span>
                    </div>
                </div>
                <div class="cfg-section-body">
                    <div class="cfg-toolbar"><input type="text" id="searchFincasCfg" class="cfg-search" placeholder="🔍 Buscar finca..." oninput="window._logRenderTables()"></div>
                    <div id="fincasListConfig" class="cfg-table-wrap"></div>
                </div>
            </div>

            <div class="cfg-section" id="cfgSecMaquinarias">
                <div class="cfg-section-head" onclick="window._logCfgToggle('cfgSecMaquinarias')">
                    <h3>🚜 Maquinaria <span class="cfg-count" id="cfgCountMaquinarias"></span></h3>
                    <div style="display:flex; gap:10px; align-items:center;">
                        <button class="btn-primary-modern" onclick="event.stopPropagation(); window._logOpenMaquinariaModal()">＋ Nueva maquinaria</button>
                        <span class="cfg-chevron">▶</span>
                    </div>
                </div>
                <div class="cfg-section-body">
                    <p style="font-size:0.76rem; color:var(--mut); margin-bottom:10px;">Catálogo de tipos de maquinaria que puede llevar un operario de carga. Se usará para activar el <b>modo ayuda</b> en el acopio.</p>
                    <div class="cfg-toolbar"><input type="text" id="searchMaquinarias" class="cfg-search" placeholder="🔍 Buscar por nombre, familia o descripción..." oninput="window._logRenderTables()"></div>
                    <div id="maquinariasListConfig" class="cfg-table-wrap"></div>
                </div>
            </div>

            <div class="cfg-section" id="cfgSecFamilias">
                <div class="cfg-section-head" onclick="window._logCfgToggle('cfgSecFamilias')">
                    <h3>🧩 Familias de Maquinaria <span class="cfg-count" id="cfgCountFamilias"></span></h3>
                    <div style="display:flex; gap:10px; align-items:center;">
                        <button class="btn-primary-modern" onclick="event.stopPropagation(); window._logOpenFamiliaModal()">＋ Nueva familia</button>
                        <span class="cfg-chevron">▶</span>
                    </div>
                </div>
                <div class="cfg-section-body">
                    <p style="font-size:0.76rem; color:var(--mut); margin-bottom:10px;">Agrupación de la maquinaria por familia (p. ej.: Tractores, Transpaletas, Remolques). Cada maquinaria pertenece a una familia.</p>
                    <div class="cfg-toolbar"><input type="text" id="searchFamilias" class="cfg-search" placeholder="🔍 Buscar familia..." oninput="window._logRenderTables()"></div>
                    <div id="familiasListConfig" class="cfg-table-wrap"></div>
                </div>
            </div>
        </div>

        <div class="modal" id="fincaModal">
            <div class="modal-content modern" style="max-width:460px;">
                <div class="mform-head">
                    <div><h2 id="fincaModalTitle">Nueva finca</h2><p>Las fincas automáticas provienen de PEDIDOS y solo pueden ocultarse.</p></div>
                    <button class="close-btn" onclick="this.closest('.modal').classList.remove('open')">✕</button>
                </div>
                <div class="mform-body">
                    <input type="hidden" id="fmOriginal" value="">
                    <div class="field"><label for="fmNombreReal">Finca (valor real del ERP) *</label><input type="text" id="fmNombreReal" placeholder="Ej.: LA FABRICA"></div>
                    <div class="field"><label for="fmNombreMostrar">Nombre mostrado</label><input type="text" id="fmNombreMostrar" placeholder="Opcional: nombre amigable"></div>
                    <div class="field"><label>Tipo de finca</label>
                        <select id="fmPropia"><option value="true">Propia (inventariable)</option><option value="false">Ajena (cliente)</option></select>
                    </div>
                    <div class="field"><label>Estado</label>
                        <select id="fmActivo"><option value="true">Visible</option><option value="false">Oculta</option></select>
                    </div>
                </div>
                <div class="mform-foot">
                    <button class="btn-sec" onclick="this.closest('.modal').classList.remove('open')">Cancelar</button>
                    <button class="btn-primary-modern" onclick="window._logGuardarFinca()">💾 Guardar</button>
                </div>
            </div>
        </div>

        <div class="modal" id="maquinariaModal">
            <div class="modal-content modern" style="max-width:460px;">
                <div class="mform-head">
                    <div><h2 id="maquinariaModalTitle">Nueva maquinaria</h2><p>Tipo de maquinaria de carga del operario.</p></div>
                    <button class="close-btn" onclick="this.closest('.modal').classList.remove('open')">✕</button>
                </div>
                <div class="mform-body">
                    <input type="hidden" id="mmId" value="">
                    <div class="field"><label for="mmNombre">Nombre *</label><input type="text" id="mmNombre" placeholder="Ej.: Tractor con jaula"></div>
                    <div class="field"><label for="mmFamilia">Familia</label><select id="mmFamilia"><option value="">— Sin familia —</option></select></div>
                    <div class="field"><label for="mmDescripcion">Descripción</label><input type="text" id="mmDescripcion" placeholder="Opcional"></div>
                    <div class="field"><label>Estado</label>
                        <select id="mmActivo"><option value="true">Activa</option><option value="false">Inactiva</option></select>
                    </div>
                </div>
                <div class="mform-foot">
                    <button class="btn-sec" onclick="this.closest('.modal').classList.remove('open')">Cancelar</button>
                    <button class="btn-primary-modern" onclick="window._logGuardarMaquinaria()">💾 Guardar</button>
                </div>
            </div>
        </div>

        <div class="modal" id="familiaModal">
            <div class="modal-content modern" style="max-width:460px;">
                <div class="mform-head">
                    <div><h2 id="familiaModalTitle">Nueva familia</h2><p>Agrupa tipos de maquinaria (p. ej.: Tractores, Transpaletas).</p></div>
                    <button class="close-btn" onclick="this.closest('.modal').classList.remove('open')">✕</button>
                </div>
                <div class="mform-body">
                    <input type="hidden" id="famId" value="">
                    <div class="field"><label for="famNombre">Nombre *</label><input type="text" id="famNombre" placeholder="Ej.: Tractores"></div>
                    <div class="field"><label for="famDescripcion">Descripción</label><input type="text" id="famDescripcion" placeholder="Opcional"></div>
                    <div class="field"><label>Estado</label>
                        <select id="famActivo"><option value="true">Activa</option><option value="false">Inactiva</option></select>
                    </div>
                </div>
                <div class="mform-foot">
                    <button class="btn-sec" onclick="this.closest('.modal').classList.remove('open')">Cancelar</button>
                    <button class="btn-primary-modern" onclick="window._logGuardarFamilia()">💾 Guardar</button>
                </div>
            </div>
        </div>`;

    window._logCfgToggle = cfgToggle;
    window._logRenderTables = () => { renderFincasTable(); renderMaquinariasTable(); renderFamiliasTable(); };
    window._logOpenFincaModal = openFincaModal;
    window._logGuardarFinca = guardarFincaModal;
    window._logEliminarFinca = eliminarFincaCfg;
    window._logOpenMaquinariaModal = openMaquinariaModal;
    window._logGuardarMaquinaria = guardarMaquinariaModal;
    window._logEliminarMaquinaria = eliminarMaquinaria;
    window._logOpenFamiliaModal = openFamiliaModal;
    window._logGuardarFamilia = guardarFamiliaModal;
    window._logEliminarFamilia = eliminarFamilia;

    rootEl.querySelectorAll('.modal').forEach(m => m.addEventListener('click', e => { if (e.target === m) m.classList.remove('open'); }));

    await refresh();
}