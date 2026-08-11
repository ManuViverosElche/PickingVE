/**
 * @fileoverview Módulo de Blindaje y Seguridad ACL (LibreriaPedidos - Security).
 * Gestiona la protección de hojas, rangos editables y eliminación segura de pestañas.
 */

/**
 * Aplica protección estricta a la hoja activa, permitiendo solo editar columnas G y H.
 */
function blindarHojaActiva() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const hoja = ss.getActiveSheet();
  const nombreHoja = hoja.getName();

  if (nombreHoja === "Indice") {
    return;
  }

  const ultimaFila = hoja.getLastRow() || 13;

  // Limpiar protecciones anteriores de la hoja
  const protecciones = hoja.getProtections(SpreadsheetApp.ProtectionType.SHEET);
  protecciones.forEach(p => p.remove());

  const proteccion = hoja.protect().setDescription(`Cierre de Seguridad - ${nombreHoja}`);
  const propietario = Session.getEffectiveUser().getEmail();
  
  proteccion.removeEditors(proteccion.getEditors());
  if (propietario) proteccion.addEditor(propietario);

  if (proteccion.canDomainEdit()) {
    proteccion.setDomainEdit(false);
  }

  // Columnas G y H editables para operarios
  const rangosLiberados = [
    hoja.getRange(`G13:H${ultimaFila}`)
  ];
  proteccion.setUnprotectedRanges(rangosLiberados);

  try {
    SpreadsheetApp.getUi().alert(`✅ Hoja "${nombreHoja}" blindada correctamente.\nSolo se permite editar las columnas G y H.`);
  } catch (e) {}
}

/**
 * Aplica protección estricta a TODAS las hojas visibles del libro (excepto Indice, Original, etc.).
 */
function blindarTodasLasHojas() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const hojas = ss.getSheets();
  let count = 0;

  hojas.forEach(hoja => {
    const nombreHoja = hoja.getName();
    if (hoja.isSheetHidden() || ["INDICE", "ORIGINAL", "EXPLANADA", "BLOQUES"].includes(nombreHoja.toUpperCase())) {
      return;
    }

    const ultimaFila = hoja.getLastRow() || 13;

    // Limpiar protecciones anteriores
    const protecciones = hoja.getProtections(SpreadsheetApp.ProtectionType.SHEET);
    protecciones.forEach(p => p.remove());

    const proteccion = hoja.protect().setDescription(`Cierre de Seguridad - ${nombreHoja}`);
    const propietario = Session.getEffectiveUser().getEmail();
    
    proteccion.removeEditors(proteccion.getEditors());
    if (propietario) proteccion.addEditor(propietario);

    if (proteccion.canDomainEdit()) {
      proteccion.setDomainEdit(false);
    }

    // Columnas G y H editables para operarios
    const rangosLiberados = [
      hoja.getRange(`G13:H${ultimaFila}`)
    ];
    proteccion.setUnprotectedRanges(rangosLiberados);
    count++;
  });

  try {
    SpreadsheetApp.getUi().alert(`✅ Blindaje masivo completado.\nSe han protegido ${count} hojas correctamente.`);
  } catch (e) {}
}

/**
 * Elimina la pestaña activa actual previa confirmación por interfaz gráfica.
 */
function borrarHojaActivaActual() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const hoja = ss.getActiveSheet();
  const nombreHoja = hoja.getName();

  if (nombreHoja === "Indice" || nombreHoja === "ORIGINAL") {
    try {
      SpreadsheetApp.getUi().alert("❌ No se puede eliminar la hoja protegida del sistema: " + nombreHoja);
    } catch (e) {}
    return;
  }

  // Comprobar que no sea la única hoja del libro
  if (ss.getSheets().length <= 1) {
    try {
      SpreadsheetApp.getUi().alert("❌ No se puede eliminar la única hoja restante en el documento.");
    } catch (e) {}
    return;
  }

  try {
    const ui = SpreadsheetApp.getUi();
    const respuesta = ui.alert(
      "⚠️ Confirmación de Eliminación",
      `¿Estás seguro de que deseas eliminar permanentemente la hoja activa: "${nombreHoja}"?\nEsta acción no se puede deshacer.`,
      ui.ButtonSet.YES_NO
    );

    if (respuesta === ui.Button.YES) {
      ss.deleteSheet(hoja);
      ui.alert(`🗑️ La hoja "${nombreHoja}" ha sido eliminada con éxito.`);
    }
  } catch (e) {
    console.error("[borrarHojaActivaActual] Error al eliminar hoja: " + e.message);
    try {
      SpreadsheetApp.getUi().alert("❌ Error al eliminar la hoja: " + e.message);
    } catch (err) {}
  }
}
