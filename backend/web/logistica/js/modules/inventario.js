import { apiService } from '/logistica/js/services/apiService.js?k=logistica-2026';

export function initInventario() {
    const root = document.getElementById('inventory-module');
    if (!root) return;
    root.innerHTML = `<div class="filters"><input id="inventory-finca" placeholder="Finca o lote"><button id="inventory-load">Consultar existencias</button></div><div id="inventory-results"><p class="text-muted">Selecciona una finca para consultar existencias.</p></div>`;
    document.getElementById('inventory-load').addEventListener('click', async () => {
        const finca = document.getElementById('inventory-finca').value.trim();
        if (!finca) return;
        const data = await apiService.getInventoryStock(finca);
        document.getElementById('inventory-results').innerHTML = `<p><strong>${data.finca}</strong>: ${(data.filas || []).length} referencias, ${(data.filas || []).reduce((sum, item) => sum + Number(item.stock || 0), 0)} unidades esperadas.</p>`;
    });
}
