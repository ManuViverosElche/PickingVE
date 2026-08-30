const KEY = 'logistica-2026';
const request = async (path, options = {}) => {
    const separator = path.includes('?') ? '&' : '?';
    const response = await fetch(`${path}${separator}k=${KEY}`, options);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return response.json();
};

export const apiService = {
    getAuth: () => request('/api/auth/me'),
    getOrderDates: () => request('/api/manager/fechas'),
    getOrders: (date, state) => request(`/api/manager/orders?fecha=${encodeURIComponent(date)}&estado=${encodeURIComponent(state)}`),
    getOrderDetail: number => request(`/api/manager/report/${encodeURIComponent(number)}`),
    getInventoryStock: finca => request(`/api/inventario/stock?finca=${encodeURIComponent(finca)}`),
    getTemplates: () => request('/api/etiquetas/plantillas'),
    generateLabelBatch: body => request('/api/etiquetas/generar-lote', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(body)}),
    getTemplatePreview: () => request('/api/etiquetas/render-ejemplo')
};
