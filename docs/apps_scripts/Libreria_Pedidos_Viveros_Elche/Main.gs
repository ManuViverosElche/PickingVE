/**
 * @fileoverview Punto de entrada principal y menús (LibreriaPedidos - Main).
 * @author PickingVE Expert Engineering
 * @version 3.3.0
 */

/**
 * Evento nativo de apertura de Google Sheets. Inyecta el menú personalizado.
 * @param {Object} e Evento de apertura.
 */
function onOpen(e) {
  try {
    const ui = SpreadsheetApp.getUi();
    ui.createMenu('🌱 Gestión Viveros')
      .addItem('🛡️ Blindar Hoja Activa', 'blindarHojaActiva')
      .addItem('🛡️ Blindar Todas las Hojas', 'blindarTodasLasHojas')
      .addSeparator()
      .addItem('🗑️ Borrar Hoja Activa Actual', 'borrarHojaActivaActual')
      .addToUi();
  } catch (error) {
    console.warn("[LibreriaPedidos.onOpen] Interfaz no disponible: " + error.message);
  }
}
