/**
 * @fileoverview Sistema de Blindaje y Mantenimiento de Hojas de Pedido.
 * @version 2.0
 * @author Gemini Assistant
 */

/**
 * Higieniza, elimina filas sobrantes y aplica reglas de protección ACL.
 * Puede ser llamada directamente tras crear una hoja o mediante un Trigger.
 * * @param {GoogleAppsScript.Spreadsheet.Sheet} [hoja] - Opcional. La hoja específica a proteger.
 */
function limpiarYProtegerHojas(hoja) {
  // Validamos que recibimos una hoja, si no, no hacemos nada.
  if (!hoja) return;
  
  const correoPropietario = obtenerCorreoPropietario();
  const usuarioEjecutor = Session.getEffectiveUser().getEmail();

  const nombreHoja = hoja.getName();

  // 1. CRITERIOS DE EXCLUSIÓN
  // Solo procesamos hojas visibles cuyo nombre empiece por número (Pedidos)
  if (hoja.isSheetHidden() || !/^\d/.test(nombreHoja)) return;

  //console.log(`Iniciando proceso técnico en: ${nombreHoja}...`);

  // 2. OPTIMIZACIÓN DE ESTRUCTURA (BORRADO DE FILAS)
  const ultimaFila = hoja.getLastRow();
  const maxFilas = hoja.getMaxRows();

  // Eliminamos filas por debajo de la última con datos (mínimo fila 13)
  // Esto reduce el peso del archivo y mejora la velocidad de carga.
  if (ultimaFila > 13 && maxFilas > ultimaFila) {
    try {
      hoja.deleteRows(ultimaFila + 1, maxFilas - ultimaFila);
    } catch (e) {
      console.warn(`[Aviso] No se pudieron eliminar filas en ${nombreHoja}: ${e.message}`);
    }
  }

  // 3. RESTRICCIÓN DE ACCESO (PROTECCIÓN)
  // Limpiamos protecciones previas para evitar duplicidad de metadatos (Idempotencia)
  const proteccionesExistentes = hoja.getProtections(SpreadsheetApp.ProtectionType.SHEET);
  proteccionesExistentes.forEach(p => p.remove());

  const proteccion = hoja.protect().setDescription(`Cierre de Seguridad - ${nombreHoja}`);

  // Configuración de Editores: Solo Propietario y el usuario que ejecuta el script
  proteccion.removeEditors(proteccion.getEditors()); // Limpieza total
  proteccion.addEditors([correoPropietario, usuarioEjecutor]);

  // Bloqueo de edición por dominio si aplica (Google Workspace)
  if (proteccion.canDomainEdit()) {
    proteccion.setDomainEdit(false);
  }

  // 4. DEFINICIÓN DE EXCEPCIONES (ZONAS EDITABLES PARA OPERARIOS)
  // Definimos los rangos donde el personal sí puede interactuar
  const rangoUltimaFila = hoja.getLastRow() || 13; // Fallback a 13 si está vacío
  
  const zonasLiberadas = [
    hoja.getRange("P4:R4"),                   // Área de Cabecera 1
    hoja.getRange("B7:J8"),                   // Área de Cabecera 2
    hoja.getRange(`X13:X${rangoUltimaFila}`), // Observaciones (Columna X)
    hoja.getRange(`T13:U${rangoUltimaFila}`)  // Acopio y Check (Columnas T y U)
  ];

  // NOTA: La columna Y (Huella Digital) queda fuera de esta lista, 
  // por lo tanto, queda blindada para el operario.
  proteccion.setUnprotectedRanges(zonasLiberadas);

  //console.log(`✓ Hoja ${nombreHoja} protegida. Rangos editables: P4:R4, B7:J8, X, T, U.`);
}

/**
 * Cachea y retorna el email del propietario del Spreadsheet.
 * @return {string} Correo electrónico del dueño del archivo.
 */
function obtenerCorreoPropietario() {
  const PROPERTY_KEY = "PROPIETARIO_CACHE";
  const scriptProps = PropertiesService.getScriptProperties();
  let email = scriptProps.getProperty(PROPERTY_KEY);

  if (!email) {
    try {
      const fileId = SpreadsheetApp.getActiveSpreadsheet().getId();
      email = DriveApp.getFileById(fileId).getOwner().getEmail();
      scriptProps.setProperty(PROPERTY_KEY, email);
    } catch (e) {
      // Si falla por permisos de DriveApp, usamos el usuario activo
      email = Session.getEffectiveUser().getEmail();
    }
  }
  return email;
}

/**
 * Retorna el correo del propietario almacenado en las propiedades del script.
 * Si no está almacenado, se obtiene, se guarda y se retorna.
 */
/*function obtenerCorreoPropietario() {
  var key = "correoPropietario";
  var props = PropertiesService.getScriptProperties();
  var correo = props.getProperty(key);
  
  if (!correo) {
    try {
      // Se intenta obtener el correo del propietario del archivo.
      correo = DriveApp.getFileById(SpreadsheetApp.getActiveSpreadsheet().getId())
                      .getOwner().getEmail();
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
}*/

/**
 * Limpia las hojas del libro eliminando filas vacías y las protege,
 * permitiendo edición solo en ciertos rangos.
 */
/*function limpiarYProtegerHojas() {
  var ss = SpreadsheetApp.getActiveSpreadsheet(); // Obtiene la hoja de cálculo activa.
  var hojas = ss.getSheets(); // Obtiene todas las hojas del libro.
  
  // Se obtiene una única vez el correo del propietario.
  var propietario = obtenerCorreoPropietario();

  hojas.forEach(function(hoja) {
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
    var proteccion = hoja.protect().setDescription("Hoja protegida, solo ciertos rangos son editables.");

    // Se eliminan todos los editores excepto el propietario.
    proteccion.removeEditors(proteccion.getEditors().filter(function(editorEmail) {
      return editorEmail !== propietario;
    }));
    // Se asegura que el propietario y el usuario actual tengan acceso de edición.
    proteccion.addEditor(propietario);
    proteccion.addEditor(Session.getEffectiveUser().getEmail());

    // Definición de los rangos editables.
    var rangosEditables = [
      hoja.getRange("P4:R4"),                       // Celdas P4 a R4.
      hoja.getRange("B7:J8"),                       // Celdas B7 a J8.
      hoja.getRange("X13:X" + ultimaFila),          // Columna X desde la fila 13 hasta la última con datos.
      hoja.getRange("T13:U" + ultimaFila),           // Columnas T y U desde la fila 13 hasta la última con datos.
      hoja.getRange("Y13:Y" + ultimaFila)           // Columna Y huella digital.
    ];
    proteccion.setUnprotectedRanges(rangosEditables); // Permite editar solo estos rangos.

    //Logger.log("Protección aplicada en la hoja: " + nombreHoja);
  });

  //Logger.log("Proceso completado.");
}*/