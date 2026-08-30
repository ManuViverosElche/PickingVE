export async function loadLabelTemplates(apiService) {
    const selects = [...document.querySelectorAll('#etiqueta-formato-select, #etiqueta-formato select')];
    const data = await apiService.getTemplates();
    selects.forEach(select => { select.innerHTML = (data.plantillas || []).map(template => `<option value="${template.id}">${template.nombre}</option>`).join(''); });
    return data.plantillas || [];
}

export async function renderLabelSelector(root, apiService) {
    root.innerHTML = '<select id="etiqueta-formato-select"><option>Cargando plantillas...</option></select>';
    await loadLabelTemplates(apiService);
    window.setInterval(() => loadLabelTemplates(apiService), 5000);
}
