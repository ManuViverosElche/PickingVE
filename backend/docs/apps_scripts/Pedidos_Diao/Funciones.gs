/**
 * Elimina una hoja del libro si su nombre coincide con el especificado.
 * Si la hoja no existe, no realiza ninguna acción.
 */
function eliminarHojaPorNombre() {
  // 1. Nombre de la hoja a eliminar
  var nombreHojaAEliminar = "15/07/2026";  // ← Cambia este valor por el nombre de la hoja que deseas eliminar.

  // 2. Obtiene la hoja de cálculo activa
  var ss = SpreadsheetApp.getActiveSpreadsheet();

  // 3. Intenta obtener la hoja con el nombre especificado
  var hoja = ss.getSheetByName(nombreHojaAEliminar);

  // 4. Si la hoja existe, la elimina
  if (hoja) {
    ss.deleteSheet(hoja);
    Logger.log("Hoja eliminada: " + nombreHojaAEliminar);
  } else {
    Logger.log("No se encontró la hoja: " + nombreHojaAEliminar);
  }
}

/**
 * Función onEdit para actualizar la columna G automáticamente cuando se marca
 * un checkbox en la columna H. Solo actúa si la celda contiene un checkbox real
 * y se excluye la hoja "Indice".
 * 
 * Flujo general:
 * 1. Detecta la celda editada.
 * 2. Verifica que no sea la hoja "Indice".
 * 3. Verifica que la columna editada sea H (8) y que contenga un checkbox.
 * 4. Si el checkbox se marca y G está vacío, copia el valor de F a G.
 * 5. Si G ya tiene valor o el checkbox se desmarca, no hace nada.
 */
function onEdit(e) {
  // Obtiene la hoja donde se ha producido la edición
  var hoja = e.source.getActiveSheet();
  var nombreHoja = hoja.getName();

  // Obtiene la celda editada, su fila y columna
  var celda = e.range;
  var fila = celda.getRow();
  var columna = celda.getColumn();

  // ❌ Excluir hoja "Indice": no se ejecuta nada aquí
  if (nombreHoja === "Indice") return;

  // ❌ Solo actuar si la columna editada es H (columna 8)
  if (columna !== 8) return;

  // Verificar que la celda contiene un checkbox real
  // getDataValidation() devuelve la validación de datos de la celda
  var validacion = celda.getDataValidation();
  if (!validacion || validacion.getCriteriaType() !== SpreadsheetApp.DataValidationCriteria.CHECKBOX) {
    // Si no hay validación o no es un checkbox, salir sin hacer nada
    return;
  }

  // Valor actual del checkbox: true = marcado, false = desmarcado
  var valor = celda.getValue();

  // Obtener celdas contiguas:
  var celdaG = hoja.getRange(fila, 7); // Columna G (ajuste / valor manual)
  var celdaF = hoja.getRange(fila, 6); // Columna F (total de planta)

  var valorG = celdaG.getValue(); // Valor actual en G
  var valorF = celdaF.getValue(); // Valor en F

  // Lógica principal:
  // Si el checkbox se marca y G está vacío, copiar F a G
  if (valor === true) {
    if (valorG === "" || valorG === null) {
      celdaG.setValue(valorF);
    }
    // Si G ya tiene valor, no se modifica (respeta el ajuste manual)
  }

  // Si el checkbox se desmarca, no se hace ninguna acción
  // Esto evita borrar datos manuales en G
}