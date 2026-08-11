/**
 * Declaración de constantes globales al proyecto
 */
const TEMPLATE_SHEET_NAME = 'ORIGINAL';  // Nombre de la hoja plantilla
const SPREADSHEET_ID_LA_FABRICA = '1bAQUKkJnKqq8Y85hZTIw_1Iwxr5xBJLTjX-dKPLZ-kc';  // ID de la hoja de cálculo en Google Sheets "Pedidos La Fábrica"
const SPREADSHEET_ID_BORISA = '1Ow1_DFik2Mm3MFl59-DkFSMEzkGMG6CPBs6sEGvTVec';  // ID de la hoja de cálculo en Google Sheets "Pedidos Borisa"
const SPREADSHEET_ID_RESTO_FINCAS = '11KNGdpskBLV3GEmRrZVGF3AeG_T34-yhiVZhPxG9I_k';  // ID de la hoja de cálculo en Google Sheets "Pedidos - Resto Fincas"
const SPREADSHEET_ID_JESUS = '15K03Xx5YAVv4CqKtyu82wy-ABrqoD8VC2olgUKCRXhQ';  // ID de la hoja de cálculo en Google Sheets de "Pedidos - Jesús"
const SPREADSHEET_ID_CHEIK = '1LMer_MCQmyUPJzCam8Sh_k40iOhsGaKKnjuxBP4BN3k';  // ID de la hoja de cálculo en Google Sheets de "Pedidos - Cheik"
const SPREADSHEET_ID_ABDEL = '1rLeTnrYgJM7v_bn4G70E4GuT_HqMISCU749cjIJ6bKM';  // ID de la hoja de cálculo en Google Sheets de "Pedidos - Abdel"
const SPREADSHEET_ID_DIAO = '1zNgvKpEXzGXBpUPchCKhn2R2zvZwDUumMgFGayioN7M';  // ID de la hoja de cálculo en Google Sheets de "Pedidos - Diao"
const SPREADSHEET_ID_BARA = '1w9g3yWs4UGy4-iqujtsSZ-1MIOAk2mb2Ja6gRRN6rBI';  // ID de la hoja de cálculo en Google Sheets de "Pedidos - Bara"
const SPREADSHEET_ID_ANTONIO = '13NE3_KsSxRWuXCK3gKJIEjfz8h2Re8HWU4EW54_VQ7c';  // ID de la hoja de cálculo en Google Sheets de "Pedidos - Antonio"
const SPREADSHEET_ID_ANGELILLO = '1M3m9YKlzyHrcKS-oLdEyVzwEX7wRCUe1M5ihOgXA3io';  // ID de la hoja de cálculo en Google Sheets de "Pedidos - Angelillo"
const SPREADSHEET_ID_ANGEL = '15MhrxORJXNiJGGJJPl_7w9N5h09jOW-hB7gtrujA2tA';  // ID de la hoja de cálculo en Google Sheets de "Pedidos - Don Angel"
const SPREADSHEET_ID_CARLOS = '129gZTuLyTRXDBk49nE1U2qUtb5XwSVsS0AzayZzEsYc';  // ID de la hoja de cálculo en Google Sheets de "Pedidos - Carlos"
const SPREADSHEET_ID_FEDE = '1de7F-qT7pCrO8_CIZwfj01-glfjIzgoNmywytJUwOa8';  // ID de la hoja de cálculo en Google Sheets de "Pedidos - Carlos"

/*******************************
 * Mapa de operarios y IDs de libros
 *******************************/
const OPERARIOS_IDS = {
  'ANGELILLO': SPREADSHEET_ID_ANGELILLO,
  'JESÚS': SPREADSHEET_ID_JESUS,
  'CHEIK': SPREADSHEET_ID_CHEIK,
  'DIAO': SPREADSHEET_ID_DIAO,
  'BARA': SPREADSHEET_ID_BARA,
  'ANTONIO': SPREADSHEET_ID_ANTONIO,
  'CARLOS': SPREADSHEET_ID_CARLOS,
  'FEDE': SPREADSHEET_ID_FEDE,
  'DON ANGEL': SPREADSHEET_ID_ANGEL
};

// -------------------- BOTÓN / MENÚ --------------------
/**
 * Función que ejecuta el acopio completo
 * Se llama desde:
 *   - Menú personalizado
 *   - Checkbox W6
 */
function EjecutarAcopioDesdeBoton() {
  const hoja = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
  SpreadsheetApp.getActiveSpreadsheet().toast("Iniciando acopio de pedidos...", "Acopio", 3);
  try {
    AcopiarHojaActivaPorOperarios();
    SpreadsheetApp.getActiveSpreadsheet().toast("Acopio completado ✅", "Acopio", 5);
  } catch (error) {
    SpreadsheetApp.getActiveSpreadsheet().toast("Error: " + error.message, "Acopio", 5);
    Logger.log(error);
  }
}

/**
 * Sincronización masiva basada exclusivamente en la existencia de datos en la columna Y.
 */
function sincronizarTodoA_BigQuery() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const hojas = ss.getSheets();
  const projectId = "dashboard-439511";
  
  let actualizaciones = [];

  hojas.forEach(hoja => {
    const nombreHoja = hoja.getName();
    
    // 1. Saltamos las hojas maestras conocidas
    if (["INDICE", "ORIGINAL"].includes(nombreHoja.toUpperCase())) return;

    const ultimaFila = hoja.getLastRow();
    const ultimaColumna = hoja.getLastColumn();

    // 2. Si la hoja no llega a la columna Y (25), es físicamente imposible que tenga huellas
    if (ultimaColumna < 25 || ultimaFila < 13) return;

    try {
      // Leemos el bloque desde la fila 13: Columna S (19) hasta Columna Y (25)
      // getRange(fila_inicio, col_inicio, num_filas, num_cols)
      const numFilasAProcesar = ultimaFila - 12;
      const data = hoja.getRange(13, 19, numFilasAProcesar, 7).getValues(); 

      data.forEach(fila => {
        const unidadesAcopiadas = fila[1]; // Columna T (índice 1 del rango)
        const huella = fila[6];            // Columna Y (índice 6 del rango)

        // 3. Verificamos si existe algo en la columna Y que podamos usar como ID
        if (huella && huella.toString().trim().length > 0) { 
          actualizaciones.push({
            huella: huella.toString(), 
            valor: parseInt(unidadesAcopiadas) || 0
          });
        }
      });
    } catch (err) {
      console.warn(`Hoja "${nombreHoja}" omitida por error técnico o falta de estructura.`);
    }
  });

  // --- ENVÍO A BIGQUERY ---
  if (actualizaciones.length === 0) {
    SpreadsheetApp.getUi().alert("No se han encontrado huellas digitales en ninguna de las hojas procesadas.");
    return;
  }

  const tamanyoLote = 50;
  for (let i = 0; i < actualizaciones.length; i += tamanyoLote) {
    const lote = actualizaciones.slice(i, i + tamanyoLote);
    
    let sql = `UPDATE \`dashboard-439511.GestionComercialVE.LINEA_PEDIDO\` SET TOTAL_ACOPIADO = CASE `;
    let ids = [];
    
    lote.forEach(item => {
      sql += `WHEN HUELLA_DIGITAL = '${item.huella}' THEN ${item.valor} `;
      ids.push(`'${item.huella}'`);
    });
    
    sql += `END WHERE HUELLA_DIGITAL IN (${ids.join(",")})`;

    try {
      BigQuery.Jobs.query({ query: sql, useLegacySql: false }, projectId);
    } catch (e) {
      console.error("Error en lote BigQuery: " + e.message);
    }
  }

  SpreadsheetApp.getUi().alert(`Sincronización finalizada: ${actualizaciones.length} líneas enviadas a BigQuery.`);
}

/**
 * Función auxiliar que extrae solo la parte de la fecha (sin hora).
 * 
 * @param {Date} fecha - Objeto de fecha a procesar.
 * @return {Date} Nueva fecha con la misma información de año, mes y día, pero con la hora establecida en 00:00:00.
 */
function getFechaSinHora(fecha) {
  var fechaCarga = new Date(fecha);
  fechaCarga.setHours(0, 0, 0, 0);
  return fechaCarga;
}

/**
 * Función auxiliar que convierte una fila de BigQuery a un objeto.
 * @param {Object} row - La fila de BigQuery.
 * @param {Object} schema - El esquema con los nombres de campos.
 * @return {Object} Objeto con los nombres de campos como propiedades.
 */
function parseRow(row, schema) {
  var obj = {};
  schema.fields.forEach(function(field, index) {
    obj[field.name] = row.f[index].v;
  });
  return obj;
}

/**
 * Función para configurar un disparador que ejecute crearPedido cada hora.
 */
function configurarTemporizador() {
  ScriptApp.newTrigger('crearPedido')
    .timeBased()
    .everyHours(1)
    .create();
}

/**
 * -------------------------------------------------------------------------
 * PROCESAR DESCRIPCIÓN DE ARTÍCULO
 * -------------------------------------------------------------------------
 * Analiza el texto de la descripción del artículo proveniente del CRM y
 * extrae instrucciones especiales definidas entre corchetes [].
 *
 * Estas instrucciones permiten a los comerciales indicar información
 * adicional que será interpretada automáticamente por el sistema de
 * preparación de pedidos en Google Sheets.
 *
 * SINTAXIS SOPORTADA
 * -------------------------------------------------------------------------
 * [F: texto]                 → Define la FINCA de carga
 * [S: texto]                 → Define el SECTOR de carga
 * [F: finca - S: sector]     → Define FINCA y SECTOR en una sola instrucción
 *
 * [M]                        → Indica que la línea está MARCADA
 * [M: texto]                 → Línea marcada con descripción de la marca
 *
 * [OBS: texto]               → Observaciones para el operario
 *
 * [PRIO]                     → Marca la línea como prioritaria
 * [NO_PRIO]                  → Marca la línea como no prioritaria
 *
 * REGLAS
 * -------------------------------------------------------------------------
 * - Todo lo que esté entre [] se considera instrucción del sistema.
 * - Todo lo que esté fuera de [] forma parte de la descripción del artículo.
 * - Puede haber múltiples instrucciones en cualquier orden.
 * - Las observaciones se acumulan en un array.
 *
 * EJEMPLO DE ENTRADA
 * -------------------------------------------------------------------------
 * "WASHINGTONIA ROBUSTA 135 cm Limpia
 *  [F: LA FABRICA - S: NAVE LARGA]
 *  [M: DOBLE + ROJO/BLANCO]
 *  [OBS: escoger las más rectas]"
 *
 * RESULTADO
 * -------------------------------------------------------------------------
 * {
 *   descripcion: "WASHINGTONIA ROBUSTA 135 cm Limpia",
 *   finca: "LA FABRICA",
 *   sector: "NAVE LARGA",
 *   marcado: true,
 *   observaciones: [
 *     "MARCA: DOBLE + ROJO/BLANCO",
 *     "OBS: escoger las más rectas"
 *   ],
 *   prioridad: false
 * }
 *
 * @param {string} descripcion Texto original de la descripción del artículo
 * @return {Object} Objeto con la información estructurada para el pedido
 */
function procesarDescripcion(descripcion) {
  // -----------------------------------------------------------------------
  // ESTRUCTURA DEL RESULTADO
  // -----------------------------------------------------------------------
  // Este objeto contendrá todos los datos extraídos de la descripción
  // y será devuelto al código principal para rellenar el Google Sheet.
  let resultado = {
    descripcion: descripcion,  // descripción limpia del artículo
    finca: null,               // finca de carga
    sector: null,              // sector de carga
    marcado: false,            // indica si la línea está marcada
    observaciones: [],         // lista de observaciones para el operario
    prioridad: false           // indicador de prioridad
  };

  // -----------------------------------------------------------------------
  // EXTRACCIÓN DE BLOQUES DE INSTRUCCIONES
  // -----------------------------------------------------------------------
  // Se buscan todos los textos que estén entre corchetes [].
  // Cada coincidencia se procesa individualmente.
  const matches = [...descripcion.matchAll(/\[(.*?)\]/g)];

  // -----------------------------------------------------------------------
  // PROCESAMIENTO DE CADA INSTRUCCIÓN
  // -----------------------------------------------------------------------
  matches.forEach(match => {
    // Se obtiene el contenido interno del bloque sin los corchetes
    let contenido = match[1].trim();

    // ---------------------------------------------------------------
    // FINCA
    // ---------------------------------------------------------------
    // Formatos soportados:
    // [F: LA FABRICA]
    // [F: LA FABRICA - S: NAVE LARGA]
    if (contenido.startsWith("F:")) {
      let valor = contenido.substring(2).trim();
      // Caso combinado: finca + sector
      if (valor.includes("- S:")) {
        let partes = valor.split("- S:");
        resultado.finca = partes[0].trim();
        resultado.sector = partes[1].trim();
      } else {
        resultado.finca = valor;
      }
    }

    // ---------------------------------------------------------------
    // SECTOR
    // ---------------------------------------------------------------
    // Formato:
    // [S: NAVE LARGA]
    else if (contenido.startsWith("S:")) {
      resultado.sector = contenido.substring(2).trim();
    }

    // ---------------------------------------------------------------
    // MARCADO SIMPLE
    // ---------------------------------------------------------------
    // Formato:
    // [M]
    else if (contenido === "M") {
      resultado.marcado = true;
    }

    // ---------------------------------------------------------------
    // MARCADO CON DESCRIPCIÓN
    // ---------------------------------------------------------------
    // Formato:
    // [M: DOBLE + ROJO/BLANCO]
    else if (contenido.startsWith("M:")) {
      resultado.marcado = true;
      let marca = contenido.substring(2).trim();
      if (marca) {
        resultado.observaciones.push("MARCA: " + marca);
      }
    }

    // ---------------------------------------------------------------
    // OBSERVACIONES
    // ---------------------------------------------------------------
    // Formato:
    // [OBS: escoger las más rectas]
    else if (contenido.startsWith("OBS:")) {
      let obs = contenido.substring(4).trim();
      if (obs) {
        resultado.observaciones.push("OBS: " + obs);
      }
    }
    // ---------------------------------------------------------------
    // PRIORIDAD
    // ---------------------------------------------------------------
    // Formato:
    // PRIORIDAD
    else if (contenido === "PRIO") {
      resultado.prioridad = "PRIO";
    }
    // NO PRIORIDAD
    else if (contenido === "NOPRIO") {
      resultado.prioridad = "NOPRIO";
    }
  });
  // -----------------------------------------------------------------------
  // LIMPIEZA DE LA DESCRIPCIÓN
  // -----------------------------------------------------------------------
  // Se eliminan todos los bloques de instrucciones [] dejando únicamente
  // la descripción real del artículo.
  resultado.descripcion = descripcion.replace(/\[.*?\]/g, "").trim();

  // -----------------------------------------------------------------------
  // DEVOLUCIÓN DEL RESULTADO
  // -----------------------------------------------------------------------
  return resultado;
}

/**
 * Elimina todas las hojas del libro que no estén en la lista permitida.
 * Las hojas permitidas son: "EXPLANADA", "BLOQUES", "INDICE" y "ORIGINAL".
 * Este proceso elimina todas las hojas que no coincidan con los nombres especificados.
 * 
 * Importante: Una vez eliminadas, las hojas no podrán recuperarse a menos que haya una copia de seguridad.
 */
function eliminarHojasNoPermitidas() {
  // 1. Obtiene la hoja de cálculo activa
  var ss = SpreadsheetApp.getActiveSpreadsheet();  // Se obtiene el objeto de la hoja de cálculo activa.

  // 2. Obtiene todas las hojas en el libro
  var hojas = ss.getSheets();  // Este método devuelve todas las hojas presentes en la hoja de cálculo.

  // 3. Define la lista de hojas que NO se deben eliminar
  var hojasPermitidas = ["INDICE", "ORIGINAL"];  // Aquí se listan las hojas que NO serán eliminadas.

  // 4. Itera sobre cada hoja del libro
  hojas.forEach(function(hoja) {
    var nombre = hoja.getName();  // Obtiene el nombre de la hoja actual.

    // 5. Verifica si la hoja actual no está en la lista de hojas permitidas
    if (!hojasPermitidas.includes(nombre)) {  // Si la hoja no es una de las permitidas, la elimina.
      ss.deleteSheet(hoja);  // Elimina la hoja no permitida.
      //Logger.log("Hoja eliminada: " + nombre);  // Registra el nombre de la hoja eliminada en el registro.
    }
  });

  // 6. Registra que el proceso ha finalizado
  //Logger.log("Proceso completado. Solo quedan las hojas permitidas.");  // Mensaje que indica que el proceso ha terminado.
}

/**
 * Elimina una hoja del libro si su nombre coincide con el especificado.
 * Si la hoja no existe, no realiza ninguna acción.
 */
function eliminarHojaPorNombre() {
  // 1. Nombre de la hoja a eliminar
  var nombreHojaAEliminar = "260418 - IF PLANTS";  // ← Cambia este valor por el nombre de la hoja que deseas eliminar.

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
 * Función para revisar las fechas en la hoja "Indice" y eliminar las filas con fechas pasadas.
 * @param {Spreadsheet} libro - El libro de trabajo en el que se busca la hoja "Indice".
 */
function eliminarFechasPasadas(libro) {
  var hojaIndice = libro.getSheetByName("Indice"); // Obtener la hoja "Indice"
  
  if (!hojaIndice) {
    //Logger.log("No se encontró la hoja 'Indice'.");
    return;
  }

  var ultimaFila = hojaIndice.getLastRow(); // Obtener la última fila con datos en la hoja
  if (ultimaFila <= 1) {
    //Logger.log("No hay filas de datos para revisar.");
    return; // Si no hay filas con datos (solo cabecera), salimos
  }

  var rangoFechas = hojaIndice.getRange("A2:A" + ultimaFila); // Rango de fechas en la columna A
  var fechas = rangoFechas.getValues(); // Obtener todas las fechas en el rango
  
  // Obtener la fecha de hoy sin hora
  var hoy = getFechaSinHora(new Date()); 

  // Recorrer todas las fechas desde la fila 2 hacia abajo
  for (var i = 0; i < fechas.length; i++) {
    var fechaFila = getFechaSinHora(fechas[i][0]); // Obtener la fecha de la fila sin la parte de la hora
    
    if (fechaFila < hoy) { // Si la fecha es anterior al día de hoy
      // Eliminar la fila correspondiente
      hojaIndice.deleteRow(i + 2); // +2 porque el rango empieza en A2
      //Logger.log("Fila " + (i + 2) + " eliminada, fecha: " + fechaFila);
      i--; // Ajustar el índice para continuar revisando correctamente después de eliminar una fila
    } else {
      // Si encontramos una fecha igual o mayor a hoy, terminamos la revisión
      //Logger.log("Se encontró una fecha igual o posterior a hoy. Terminando la revisión.");
      break;
    }
  }
}

/**
 * Función para rellenar el índice con la combinación de fecha y finca.
 * Si la combinación (fecha, finca) no existe, se añade en la posición correcta:
 *   - Las filas se ordenan por fecha (ascendente)
 *   - Para la misma fecha, se ordenan alfabéticamente por finca.
 * También se insertan las fórmulas en las columnas C, D y E.
 * 
 * @param {Sheet} hojaIndice - La hoja "Indice" donde se actualizará el índice.
 * @param {string} fecha - La fecha a insertar (formato "dd/MM/yyyy").
 * @param {string} finca - El nombre de la finca a insertar.
 */
function rellenarIndice(hojaIndice, fecha, finca) {
  // Obtener la última fila con datos de la hoja (suponemos que la fila 1 son cabeceras)
  var lastRow = hojaIndice.getLastRow();
  
  // Convertir la fecha pasada a un objeto Date sin hora usando getFechaSinHora
  var fechaObjetivo = getFechaSinHora(fecha);
  
  // Caso 1: No hay filas de datos (solo cabeceras)
  if (lastRow <= 1) {
    // Insertar una nueva fila en la posición 2 (inmediatamente debajo de la cabecera)
    hojaIndice.insertRowAfter(1);
    var newRow = 2;
    hojaIndice.getRange(newRow, 1).setValue(fechaObjetivo);
    hojaIndice.getRange(newRow, 2).setValue(finca);
    
    // Fórmulas construidas con la variable newRow para la inserción en la hoja Indice

    // Fórmula para Columna C
    var formulaC = `=SUMIFS(INDIRECT("'" & TEXT(A${newRow},"dd/mm/yyyy") & "'!F:F"), INDIRECT("'" & TEXT(A${newRow},"dd/mm/yyyy") & "'!C:C"), "*" & B${newRow} & "*", INDIRECT("'" & TEXT(A${newRow},"dd/mm/yyyy") & "'!F:F"), ">0")`;

    // Fórmula para Columna D
    var formulaD = `=SUMIFS(INDIRECT("'" & TEXT(A${newRow},"dd/mm/yyyy") & "'!G:G"), INDIRECT("'" & TEXT(A${newRow},"dd/mm/yyyy") & "'!C:C"), "*" & B${newRow} & "*", INDIRECT("'" & TEXT(A${newRow},"dd/mm/yyyy") & "'!G:G"), ">0")`;

    // Fórmula para Columna E
    var formulaE = `=IF(C${newRow}<>0, D${newRow}/C${newRow}, 0)`;

    hojaIndice.getRange(newRow, 3).setFormula(formulaC);
    hojaIndice.getRange(newRow, 4).setFormula(formulaD);
    hojaIndice.getRange(newRow, 5).setFormula(formulaE);
    
    //Logger.log("Insertada nueva fila en la posición " + newRow + " porque solo había cabeceras.");
    return;
  }
  
  // Caso 2: Ya existen filas de datos (más allá de la cabecera)
  // Obtener el rango de datos en las columnas A y B (desde la fila 2 hasta la última)
  var data = hojaIndice.getRange(2, 1, lastRow - 1, 2).getValues();
  
  // Buscar el lugar adecuado para insertar la nueva fila:
  // Recorrer el array (cada elemento corresponde a una fila: [fecha, finca])
  var insertIndex = -1; // Este índice (0-based) indicará dónde insertar.
  for (var i = 0; i < data.length; i++) {
    var fechaFila = getFechaSinHora(data[i][0]);  // Convertir la fecha existente en la fila
    var fincaFila = data[i][1];
    
    // Si la fecha en la fila es mayor que la fecha objetivo, debemos insertar antes
    if (fechaFila > fechaObjetivo) {
      insertIndex = i;
      break;
    }
    // Si las fechas son iguales, compararemos las fincas alfabéticamente.
    if (fechaFila.getTime() === fechaObjetivo.getTime()) {
      // Si la finca en la fila es mayor (alfabéticamente) que la finca objetivo, insertamos antes
      if (fincaFila > finca) {
        insertIndex = i;
        break;
      }
    }
  }
  
  // Calcular la fila de inserción en la hoja:
  // Si insertIndex es -1, no se encontró ninguna fila con fecha posterior (o finca mayor), así que se inserta al final.
  var newRowNumber;
  if (insertIndex === -1) {
    newRowNumber = lastRow + 1;
  } else {
    // Como el array 'data' comienza en la fila 2, el índice 0 corresponde a la fila 2 en la hoja
    newRowNumber = insertIndex + 2;
  }
  
  // Insertar la nueva fila en la posición calculada
  hojaIndice.insertRowBefore(newRowNumber);
  hojaIndice.getRange(newRowNumber, 1).setValue(fechaObjetivo);
  hojaIndice.getRange(newRowNumber, 2).setValue(finca);
  
  // Insertar las fórmulas en las columnas C, D y E
  hojaIndice.getRange(newRowNumber, 3).setFormula(
    '=SUMAR.SI.CONJUNTO(INDIRECTO("\'" & TEXTO(A' + newRowNumber + ';"dd/mm/yyyy") & "\'!F:F"); ' +
    'INDIRECTO("\'" & TEXTO(A' + newRowNumber + ';"dd/mm/yyyy") & "\'!C:C"); "*" & B' + newRowNumber + ' & "*"; ' +
    'INDIRECTO("\'" & TEXTO(A' + newRowNumber + ';"dd/mm/yyyy") & "\'!F:F"); ">0")'
  );
  hojaIndice.getRange(newRowNumber, 4).setFormula(
    '=SUMAR.SI.CONJUNTO(INDIRECTO("\'" & TEXTO(A' + newRowNumber + ';"dd/mm/yyyy") & "\'!G:G"); ' +
    'INDIRECTO("\'" & TEXTO(A' + newRowNumber + ';"dd/mm/yyyy") & "\'!C:C"); "*" & B' + newRowNumber + ' & "*"; ' +
    'INDIRECTO("\'" & TEXTO(A' + newRowNumber + ';"dd/mm/yyyy") & "\'!G:G"); ">0")'
  );
  hojaIndice.getRange(newRowNumber, 5).setFormula('=SI(C' + newRowNumber + '<>0; D' + newRowNumber + '/C' + newRowNumber + '; 0)');
  
  //Logger.log("Insertada nueva fila en la posición " + newRowNumber + " con fecha " + fecha + " y finca " + finca);
}