export async function openOrderDetail(number, {apiService, onPrint}) {
    const data = await apiService.getOrderDetail(number);
    let modal = document.getElementById('faena-order-modal');
    if (!modal) { modal = document.createElement('dialog'); modal.id = 'faena-order-modal'; document.body.appendChild(modal); }
    modal.innerHTML = `<form method="dialog" class="card"><h3>Detalle de acopios · Pedido #${data.numero}</h3><p>${data.cliente || ''} · ${data.finca || ''} · ${data.sector || ''}</p><table><thead><tr><th>Referencia</th><th>Descripción</th><th>Pedido</th><th>Acopiado</th><th>Sustituido</th></tr></thead><tbody>${(data.lineas || []).map(line => `<tr><td>${line.referencia}</td><td>${line.descripcion || ''}</td><td>${line.pedido}</td><td>${line.acopiado}</td><td>${line.sustituido ? 'Sí' : 'No'}</td></tr>`).join('')}</tbody></table><div><button value="cancel">Cerrar</button><button type="button" id="modal-print-labels">Imprimir Etiquetas de este Pedido</button></div></form>`;
    modal.querySelector('#modal-print-labels').addEventListener('click', () => onPrint?.(data.numero));
    modal.showModal();
}
