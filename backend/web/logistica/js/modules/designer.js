import { apiService } from '/logistica/js/services/apiService.js?k=logistica-2026';
import { loadLabelTemplates } from '/logistica/js/components/labelSelector.js?k=logistica-2026';

export async function initDesigner() {
    const profile = await apiService.getAuth();
    if (profile) {
        document.getElementById('user-display-name').textContent = profile.user || 'Operario Viveros';
        document.getElementById('user-role-badge').textContent = profile.role || 'ADMIN';
    }
    await loadLabelTemplates(apiService);
}
