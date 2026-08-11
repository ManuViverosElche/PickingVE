// -------------------- MENÚ PERSONALIZADO --------------------
/**
 * Se ejecuta al abrir la hoja
 * Crea un menú "Pedidos" en la barra de herramientas
 */
function onOpen() {
  SpreadsheetApp.getUi()
    .createMenu('Pedidos')
    .addItem('Acopiar hoja activa', 'EjecutarAcopioDesdeBoton')
    .addToUi();
}


/**
 * Función onEdit principal para la hoja de pedidos.
 *
 * Esta función se dispara automáticamente cada vez que se edita una celda en cualquier hoja del spreadsheet.
 * Su propósito es unificar dos comportamientos distintos en la hoja de pedidos:
 *
 * 1️⃣ Casilla-botón de acopio (W6):
 *     - Detecta cuando se marca la casilla W6 en la hoja activa.
 *     - Ejecuta la función AcopiarHojaActivaPorOperarios() para acopiar todas las líneas
 *       de pedido de la hoja activa a los libros correspondientes de cada operario.
 *     - Muestra un mensaje tipo "toast" al iniciar y al terminar, para informar al usuario
 *       de que el acopio está en proceso y cuando ha finalizado.
 *     - Desmarca automáticamente la casilla W6 para permitir futuras ejecuciones sin necesidad
 *       de intervención manual.
 *
 * 2️⃣ Casillas de verificación de líneas de pedido (columnas 20 y 21):
 *     - Column 20 (TBD) → Actualiza la columna U según si el valor ingresado alcanza o supera
 *       el total de la línea (columna S).
 *     - Column 21 → Completa o borra la columna T dependiendo del estado del checkbox.
 *     - Se aplican solo a filas a partir de la fila 13, que corresponden a las líneas de pedido.
 *
 * El diseño unificado garantiza que:
 * - Los checkboxes de línea siguen funcionando exactamente igual que antes.
 * - El botón de acopio funciona tanto en escritorio como en móvil.
 * - Se proporcionan mensajes visuales al usuario para indicar inicio, progreso y finalización.
 * - Se manejan errores en la ejecución de AcopiarHojaActivaPorOperarios() y se registran en Logger.
 *
 * @param {GoogleAppsScript.Events.SheetsOnEdit} e - Evento de edición que proporciona información
 *        sobre la celda modificada (rango, hoja, fila, columna, valor nuevo, etc.)
 */
function onEdit(e) {
  if (!e) return;

  const hoja = e.range.getSheet();      // Hoja donde ocurrió la edición
  const fila = e.range.getRow();        // Fila de la celda editada
  const columna = e.range.getColumn();  // Columna de la celda editada
  const valor = e.value;                // Valor ingresado en la celda
  const ss = e.source;

  // ---------- 1️⃣ Casilla-botón W6 (Tu código actual) ----------
  if (e.range.getA1Notation() === "W6" && valor === "TRUE") {
    EjecutarAcopioDesdeBoton();
    e.range.setValue(false);
    return; // Salimos para no procesar el resto
  }

  // ---------- 2️⃣ Casillas de línea (Sincronización BigQuery) ----------
  if (fila >= 13 && (columna === 20 || columna === 21)) {
    
    const valorTotal = hoja.getRange('S' + fila).getValue();
    const huella = hoja.getRange('Y' + fila).getValue(); // "DNI" de la línea
    
    if (!huella) {
      ss.toast("⚠️ No se puede sincronizar: Falta Huella Digital", "Error", 3);
      return;
    }

    let cantidadAEnviar = 0;

    // Lógica Columna 20 (T): Entrada manual de acopio
    if (columna == 20) {
      cantidadAEnviar = parseInt(valor) || 0;
      hoja.getRange('U' + fila).setValue(cantidadAEnviar >= valorTotal);
    } 
    // Lógica Columna 21 (U): Checkbox de completado
    else if (columna == 21) {
      cantidadAEnviar = (valor === "TRUE") ? valorTotal : 0;
      hoja.getRange('T' + fila).setValue(valor === "TRUE" ? valorTotal : "");
    }

    // --- NOTIFICACIÓN Y ENVÍO ---
    ss.toast("Sincronizando con base de datos...", "BigQuery", 2);
    
    // Ejecución de la subida
    const exito = ejecutarUpdateBigQuery(huella, cantidadAEnviar);
    
    if (exito) {
      ss.toast("✅ Guardado correctamente", "BigQuery", 1);
    } else {
      ss.toast("❌ Error al guardar. Reintente.", "BigQuery", 5);
      // Opcional: Podrías pintar la celda de rojo aquí si falla
    }
  }
}

/**
 * Ejecuta la actualización en BigQuery.
 * @param {string} huella - El ID único de la fila (Columna Y).
 * @param {number} valor - La cantidad acopiada (Columna T).
 */
function ejecutarUpdateBigQuery(huella, valor) {
  const projectId = "dashboard-439511";
  const query = `
    UPDATE \`dashboard-439511.GestionComercialVE.LINEA_PEDIDO\`
    SET TOTAL_ACOPIADO = ${valor}
    WHERE HUELLA_DIGITAL = '${huella}'
  `;

  try {
    BigQuery.Jobs.query({ query: query, useLegacySql: false }, projectId);
    return true;
  } catch (e) {
    console.error("Error en BigQuery: " + e.message);
    return false;
  }
}

/**
 * Función para actualizar el índice de hojas en un libro de Google Sheets.
 * Realiza el siguiente flujo:
 * 1️⃣ Ordena las hojas por fecha de carga y número de pedido.
 * 2️⃣ Guarda y elimina el estado previo de los checkboxes.
 * 3️⃣ Recorre todas las hojas, excluyendo algunas específicas y elimina las que no cumplen con los criterios.
 * 4️⃣ Añade información sobre cada hoja al índice, incluyendo hipervínculos, cliente, fecha de carga, total de plantas, total acopiado, y porcentaje cargado.
 * 5️⃣ Inserta checkboxes nuevos y restaura su estado si es necesario.
 */
function Indice() {
  var ss = SpreadsheetApp.getActiveSpreadsheet(); // Obtiene la hoja activa
  var hojas = ss.getSheets(); // Obtiene todas las hojas
  var hojaIndice = ss.getSheetByName("INDICE"); // Obtiene la hoja "INDICE"

  // 1️⃣ Ordenar hojas por fecha de carga y número de pedido
  ordenarHojasPorPedido();

  // Volvemos a obtener las hojas del libro una vez ordenadas, para cerciorarnos de que se recorren en orden
  var hojas = ss.getSheets();

  if (!hojaIndice) return; // Evita errores si la hoja "INDICE" no existe

  // 1️⃣ Guardamos el estado actual de los checkboxes antes de borrar la tabla
  var valoresCheckbox = {};
  var lastRow = hojaIndice.getLastRow();
  if (lastRow > 1) {
    var pedidosPrevios = hojaIndice.getRange(2, 1, lastRow - 1, 1).getValues(); // Columna A (Pedido)
    var checkboxesPrevios = hojaIndice.getRange(2, 7, lastRow - 1, 1).getValues(); // Columna G (Checkbox)

    pedidosPrevios.forEach((fila, index) => {
      var pedido = fila[0]; // Número de pedido (columna A)
      var estadoCheckbox = checkboxesPrevios[index][0]; // Estado del checkbox (columna G)
      if (pedido) {
        valoresCheckbox[pedido] = estadoCheckbox;
      }
    });
  }

  // 2️⃣ Eliminamos TODA la información, incluidos los checkboxes
  if (lastRow > 1) {
    hojaIndice.getRange(2, 1, lastRow - 1, hojaIndice.getLastColumn()).clearContent(); // Borra los contenidos
    hojaIndice.getRange(2, 7, lastRow - 1, 1).removeCheckboxes(); // Elimina los checkboxes
  }

  var i = 2;

  hojas.forEach(function(hoja) {
    // 3️⃣ Omitir hojas específicas o ocultas
    if (["EXPLANADA", "BLOQUES", "INDICE", "ORIGINAL"].includes(hoja.getName()) || hoja.isSheetHidden()) {
      //Logger.log("Omitida: " + hoja.getName());
      return;
    }

    // Obtener la fecha de carga sin hora
    var fechaCarga = getFechaSinHora(hoja.getRange("N7").getValue());

    // Obtener la fecha de hoy sin hora
    var hoy = getFechaSinHora(Date());

    //Logger.log("Procesando hoja: " + hoja.getName());

    // Eliminar hojas con fecha de carga anterior a la fecha actual
    if (fechaCarga < hoy) {
      //Logger.log("Eliminando hoja: " + hoja.getName());
      ss.deleteSheet(hoja); // Elimina la hoja
      return;
    }

    //Logger.log("Añadiendo hoja al índice: " + hoja.getName());

    var numPedido = hoja.getRange("L7").getValue(); // Número de pedido

    // 4️⃣ Hipervínculo al pedido
    hojaIndice.getRange(i, 1).setFormula('=HYPERLINK("#gid=' + hoja.getSheetId() + '";"' + numPedido + '")');

    // Nombre del cliente
    if (hoja.getRange("D2").getValue() != hoja.getRange("D3").getValue()) {
      hojaIndice.getRange(i, 2).setFormula("= '" + hoja.getName() + "'!A2 & \" - \" & '" + hoja.getName() + "'!D2 & \" - \" & '" + hoja.getName() + "'!D3");
    } else {
      hojaIndice.getRange(i, 2).setFormula("= '" + hoja.getName() + "'!A2 & \" - \" & '" + hoja.getName() + "'!D2");
    }

    // 5️⃣ Fecha de carga
    hojaIndice.getRange(i, 3).setFormula("='" + hoja.getName() + "'!N7");

    // Total de plantas
    hojaIndice.getRange(i, 4).setFormula("=SUM('" + hoja.getName() + "'!S13:S" + hoja.getLastRow() + ")");

    // Total acopiado
    hojaIndice.getRange(i, 5).setFormula("=SUM('" + hoja.getName() + "'!T13:T" + hoja.getLastRow() + ")");

    // Porcentaje cargado
    hojaIndice.getRange(i, 6).setFormula("=E" + i + "/D" + i);

    // 6️⃣ Insertamos checkboxes nuevos y restauramos su estado si es necesario
    var celdaCheckbox = hojaIndice.getRange(i, 7);
    celdaCheckbox.insertCheckboxes(); // Inserta el checkbox
    if (valoresCheckbox[numPedido] !== undefined) {
      celdaCheckbox.setValue(valoresCheckbox[numPedido]); // Restaurar valor del checkbox
    }

    i++;
  });
}

/**
 * Función para ordenar las hojas de un libro de Google Sheets por fecha de carga y número de pedido.
 * Realiza el siguiente flujo:
 * 1️⃣ Clasifica las hojas en orden de importancia, separando las fijas de las ordenables.
 * 2️⃣ Ordena las hojas según la fecha de carga y número de pedido.
 * 3️⃣ Asegura que las hojas fijas permanezcan en sus posiciones iniciales.
 * 4️⃣ Mueve las hojas ordenadas al medio y mantiene la visibilidad de las hojas ocultas.
 * 5️⃣ Mueve la hoja "ORIGINAL" al final y la oculta.
 * 6️⃣ Restaura la hoja activa original.
 */
function ordenarHojasPorPedido() {
  var ss = SpreadsheetApp.getActiveSpreadsheet(); // 1️⃣ Obtiene el objeto de la hoja de cálculo activa
  var hojas = ss.getSheets(); // Obtiene todas las hojas del libro
  var hojaActiva = ss.getActiveSheet(); // Guarda la hoja activa al inicio para restaurarla después
  var hojasFijas = ["EXPLANADA", "BLOQUES", "INDICE", "ORIGINAL"]; // Define las hojas fijas que no se moverán
  var hojasOrdenables = []; // Inicializa un arreglo para las hojas que se pueden ordenar

  // 2️⃣ Recorrer todas las hojas y clasificarlas entre fijas y ordenables
  hojas.forEach(function(hoja) {
    var nombre = hoja.getName(); // Obtiene el nombre de la hoja

    // Si la hoja es fija (EXPLANADA, BLOQUES, INDICE, ORIGINAL), no se agrega al arreglo de hojas ordenables
    if (hojasFijas.includes(nombre)) {
      return; // Si es una hoja fija, se omite el procesamiento
    } else {
      var fechaCarga = hoja.getRange("N7").getValue(); // Obtiene la fecha de carga de la hoja
      var numPedido = hoja.getRange("L7").getValue().toString(); // Obtiene el número de pedido y lo convierte a string

      // Si tanto la fecha de carga como el número de pedido existen, agregamos la hoja al arreglo de hojas ordenables
      if (fechaCarga && numPedido) {
        hojasOrdenables.push({
          hoja: hoja, // Hoja a ordenar
          fechaCarga: fechaCarga, // Fecha de carga de la hoja
          numPedido: numPedido, // Número de pedido de la hoja
          estaOculta: hoja.isSheetHidden() // Determina si la hoja está oculta
        });
      }
    }
  });

  // 3️⃣ Ordenar las hojas ordenables por fecha de carga y número de pedido
  hojasOrdenables.sort((a, b) => {
    // Primero ordenamos por fecha de carga (de más antigua a más reciente)
    if (a.fechaCarga < b.fechaCarga) return -1;
    if (a.fechaCarga > b.fechaCarga) return 1;
    // Si las fechas son iguales, ordenamos por número de pedido alfabéticamente
    return a.numPedido.localeCompare(b.numPedido);
  });

  // 4️⃣ Aseguramos que las hojas fijas estén al principio
  var posicion = 1; // Inicializamos la posición para las hojas fijas
  hojasFijas.forEach(function(nombreHoja) {
    var hojaFija = ss.getSheetByName(nombreHoja); // Obtenemos la hoja fija
    hojaFija.activate(); // Activamos la hoja fija
    ss.moveActiveSheet(posicion); // Movemos la hoja fija a la posición deseada
    posicion++; // Avanzamos a la siguiente posición para la siguiente hoja fija
  });

  // 5️⃣ Mover las hojas ordenadas al medio
  hojasOrdenables.forEach(function(item) {
    item.hoja.activate(); // Activamos la hoja antes de moverla
    ss.moveActiveSheet(posicion); // Movemos la hoja a la posición siguiente
    // Si la hoja estaba oculta, la mantenemos oculta
    if (item.estaOculta) {
      item.hoja.hideSheet(); // Aseguramos que la hoja permanezca oculta si estaba originalmente oculta
    }
    posicion++; // Avanzamos a la siguiente posición para la siguiente hoja
  });

  // 6️⃣ Mover la hoja "ORIGINAL" al final y asegurarnos de que esté oculta
  var hojaOriginal = ss.getSheetByName("ORIGINAL"); // Obtenemos la hoja "ORIGINAL"
  hojaOriginal.activate(); // Activamos la hoja "ORIGINAL"
  ss.moveActiveSheet(ss.getSheets().length); // Movemos "ORIGINAL" a la última posición
  hojaOriginal.hideSheet(); // Aseguramos que "ORIGINAL" permanezca oculta

  // 7️⃣ Restauramos la hoja activa al final
  ss.setActiveSheet(hojaActiva); // Restauramos la hoja activa original, para que el usuario quede en la misma hoja que estaba antes

  // 8️⃣ Aplicar los cambios realizados
  SpreadsheetApp.flush(); // Aseguramos que todos los cambios en las hojas se apliquen inmediatamente
}