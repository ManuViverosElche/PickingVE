export function renderOrderCard(order) {
    const requested = (order.lineas || []).reduce((sum, line) => sum + Number(line.pendientes || 0), 0);
    const picked = (order.lineas || []).reduce((sum, line) => sum + Number(line.acopiado || 0), 0);
    const percent = requested ? Math.min(100, Math.round(picked * 100 / requested)) : 0;
    return `<article class="label-preview-card"><div style="display:flex;justify-content:space-between"><strong>Pedido #${order.numero}</strong><span class="badge">${order.estado || 'activo'}</span></div><div>${order.cliente || 'Sin cliente'}</div><small>📅 ${order.fechaCarga || 'N/D'} · 📍 ${order.finca || 'Sin finca'} · ${order.sector || 'Sin zona'}</small><div>Acopio: ${picked} / ${requested} uds<div style="height:6px;background:#e2e8f0;border-radius:4px"><div style="width:${percent}%;height:100%;background:var(--primary);border-radius:4px"></div></div></div><button class="btn" data-order-detail="${order.numero}">Ver detalle de acopios</button></article>`;
}
