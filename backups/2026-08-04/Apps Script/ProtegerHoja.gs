/**
 * Nombre del archivo "ProtegerHojas.gs"
 */

/**
 * Retorna el correo del propietario almacenado en las propiedades del script.
 * Si no está almacenado, se obtiene, se guarda y se retorna.
 */
function obtenerCorreoPropietario() {
  var key = "correoPropietario";
  var props = PropertiesService.getScriptProperties();
  var correo = props.getProperty(key);

  if (!correo) {
    try {
      // Se intenta obtener el correo del propietario del archivo.
      correo = DriveApp.getFileById(
        SpreadsheetApp.getActiveSpreadsheet().getId(),
      )
        .getOwner()
        .getEmail();
    } catch (e) {
      //Logger.log("Error al obtener el propietario mediante DriveApp: " + e.message);
      // Si falla, se usa el correo del usuario actual.
      correo = Session.getEffectiveUser().getEmail();
      //Logger.log("Se usará el usuario actual como propietario: " + correo);
    }
    // Se guarda el correo en las propiedades del script para próximas ejecuciones.
    props.setProperty(key, correo);
  }
  return correo;
}

/**
 * Limpia las hojas del libro eliminando filas vacías y las protege,
 * permitiendo edición solo en ciertos rangos.
 */
function limpiarYProtegerHojas() {
  var ss = SpreadsheetApp.getActiveSpreadsheet(); // Obtiene la hoja de cálculo activa.
  var hojas = ss.getSheets(); // Obtiene todas las hojas del libro.

  // Se obtiene una única vez el correo del propietario.
  var propietario = obtenerCorreoPropietario();

  hojas.forEach(function (hoja) {
    var nombreHoja = hoja.getName(); // Obtiene el nombre de la hoja.

    // Se omite la hoja si está oculta o si su nombre no empieza por un dígito.
    if (hoja.isSheetHidden() || !/^\d/.test(nombreHoja)) {
      return;
    }

    var ultimaFila = hoja.getLastRow(); // Obtiene la última fila con datos.

    // 🔹 1️⃣ Eliminar filas vacías debajo de la última fila con datos (manteniendo al menos 13 filas)
    if (ultimaFila > 13 && ultimaFila < hoja.getMaxRows()) {
      hoja.deleteRows(ultimaFila + 1, hoja.getMaxRows() - ultimaFila); // Elimina filas sobrantes.
    }

    // 🔹 2️⃣ Proteger la hoja y restringir la edición solo a ciertos rangos
    var proteccion = hoja
      .protect()
      .setDescription("Hoja protegida, solo ciertos rangos son editables.");

    // Se eliminan todos los editores excepto el propietario.
    proteccion.removeEditors(
      proteccion.getEditors().filter(function (editorEmail) {
        return editorEmail !== propietario;
      }),
    );
    // Se asegura que el propietario y el usuario actual tengan acceso de edición.
    proteccion.addEditor(propietario);
    proteccion.addEditor(Session.getEffectiveUser().getEmail());

    // Definición de los rangos editables.
    var rangosEditables = [
      hoja.getRange("P4:R4"), // Celdas P4 a R4.
      hoja.getRange("B7:J8"), // Celdas B7 a J8.
      hoja.getRange("X13:X" + ultimaFila), // Columna X desde la fila 13 hasta la última con datos.
      hoja.getRange("T13:U" + ultimaFila), // Columnas T y U desde la fila 13 hasta la última con datos.
    ];
    proteccion.setUnprotectedRanges(rangosEditables); // Permite editar solo estos rangos.

    //Logger.log("Protección aplicada en la hoja: " + nombreHoja);
  });

  //Logger.log("Proceso completado.");
}
