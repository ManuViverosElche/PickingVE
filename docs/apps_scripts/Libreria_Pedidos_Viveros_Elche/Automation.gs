/**
 * @fileoverview Módulo de Automatización y Eventos (LibreriaPedidos - Automation).
 * Gestiona el comportamiento en tiempo real ante ediciones de usuario (onEdit).
 */

/**
 * Manejador centralizado de eventos onEdit para automatizar el acopio (Columna H -> G).
 * @param {Object} e Evento de edición de Google Sheets.
 */
function onEdit(e) {
  if (!e || !e.source) return;

  var hoja = e.source.getActiveSheet();
  var nombreHoja = hoja.getName();
  var celda = e.range;
  var fila = celda.getRow();
  var columna = celda.getColumn();

  if (nombreHoja === "Indice") return;
  if (columna !== 8) return; // Columna H (Checkbox)

  var validacion = celda.getDataValidation();
  if (!validacion || validacion.getCriteriaType() !== SpreadsheetApp.DataValidationCriteria.CHECKBOX) {
    return;
  }

  var valorCheckbox = celda.getValue();
  var celdaG = hoja.getRange(fila, 7); // Columna G
  var celdaF = hoja.getRange(fila, 6); // Columna F

  var valorG = celdaG.getValue();
  var valorF = celdaF.getValue();

  if (valorCheckbox === true) {
    if (valorG === "" || valorG === null || valorG === undefined) {
      celdaG.setValue(valorF);
    }
  }
}
