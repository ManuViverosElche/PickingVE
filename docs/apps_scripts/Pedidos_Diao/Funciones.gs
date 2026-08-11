/**
 * @fileoverview Envoltorio (Stub) ultra ligero para Pedidos - Diao.
 * Toda la lógica de negocio, menús y seguridad es delegada a la LibreriaPedidos central.
 */

function onOpen(e) {
  LibreriaPedidos.onOpen(e);
}

function onEdit(e) {
  LibreriaPedidos.onEdit(e);
}

function blindarHojaActiva() {
  LibreriaPedidos.blindarHojaActiva();
}

function blindarTodasLasHojas() {
  LibreriaPedidos.blindarTodasLasHojas();
}

function borrarHojaActivaActual() {
  LibreriaPedidos.borrarHojaActivaActual();
}
