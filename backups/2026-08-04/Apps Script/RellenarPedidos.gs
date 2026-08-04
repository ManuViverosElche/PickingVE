/**
 * Nombre del archivo "RellenarPedidos.gs"
 */

/**
 * 📌 Función para eliminar filas vacías en cada hoja del libro de pedidos acopiados.
 * Se recorre cada hoja y se eliminan las filas vacías después de la última fila con datos.
 * @param {Spreadsheet} libroPedidos - Libro donde están los pedidos acopiados.
 */
function eliminarFilasVaciasPorDia(libroPedidos) {
  // 📄 Obtener todas las hojas del libro de pedidos acopiados
  var hojas = libroPedidos.getSheets();

  // 🔄 Recorremos cada hoja para eliminar filas vacías
  hojas.forEach(function (hoja) {
    var nombreHoja = hoja.getName();

    // 🎯 Validamos que la hoja sea una hoja de pedidos válida (no "Indice") y que empiece con un número (fecha)
    if (nombreHoja !== "Indice" && /^[0-9]/.test(nombreHoja)) {
      var ultimaConDatos = hoja.getLastRow(); // 📌 Última fila con datos en la hoja
      var totalFilas = hoja.getMaxRows(); // 📊 Total de filas de la hoja
      var filasVacias = totalFilas - ultimaConDatos; // 🗑️ Filas vacías a eliminar

      // ❌ Si hay filas vacías, las eliminamos
      if (filasVacias > 1) {
        hoja.deleteRows(ultimaConDatos + 1, filasVacias - 1);
      }
      // ✅ Asegurar que haya una fila totalmente en blanco
      if (hoja.getMaxRows() === ultimaConDatos) {
        hoja.insertRowsAfter(ultimaConDatos, 1); // Añadir nueva fila
        var nuevaFila = ultimaConDatos + 1; // Fila recién agregada

        // 🧹 Limpiar contenido y formato completamente
        hoja
          .getRange(nuevaFila, 1, 1, hoja.getMaxColumns())
          .clear({ contentsOnly: false, formatOnly: true });
        hoja.getRange(nuevaFila, 1, 1, hoja.getMaxColumns()).removeCheckboxes(); // Elimina los checkboxes
      }
    }
  });

  // 🔄 Aplicar cambios inmediatamente
  SpreadsheetApp.flush();
}

/**
 * 🎨 Aplica reglas de formato condicional a la hoja del pedido copiado.
 * Resalta las filas pares con número en la columna A en color gris.
 * @param {Sheet} hojaPedido - Hoja a la que se aplicará el formato condicional.
 */
function aplicarReglasFormatoCondicional(hojaPedido) {
  // 📌 Definir el rango de columnas al que se aplicará el formato condicional (C:F)
  var range = hojaPedido.getRange("C:F");

  // 🎨 Crear regla de formato condicional para filas pares con número en la columna A
  var rule = SpreadsheetApp.newConditionalFormatRule()
    .setRanges([range]) // 📌 Aplicar a columnas C:F
    .whenFormulaSatisfied(
      '=Y(RESIDUO(FILA();2)=0; ESNUMERO(INDIRECTO("A"&FILA())))',
    ) // 📊 Condición: fila par y número en A
    .setBackground("#d0cece") // 🎨 Color gris para resaltar filas pares
    .build();

  // 🔄 Obtener las reglas actuales y agregar la nueva
  var rules = hojaPedido.getConditionalFormatRules();
  rules.push(rule);
  hojaPedido.setConditionalFormatRules(rules);

  // 🔄 Aplicar cambios inmediatamente
  SpreadsheetApp.flush();
}

/**
 * Función que verifica si existe maquinaria y planta pendiente de acopiar en una hoja de origen.
 * @param {Sheet} hojaOrigen - La hoja de donde se extraen los datos.
 * @param {string} maquinaria - Tipo de maquinaria que se busca.
 * @return {boolean} - Devuelve true si existe la maquinaria y planta pendiente de acopiar, false en caso contrario.
 */
function existeMaquinaria(hojaOrigen, maquinaria) {
  // Obtener los valores de las columnas W (maquinaria), T (planta acopiada) y S (planta pendiente) desde la fila 13 hasta la última con datos
  Logger.log(`Estamos en la hoja: ${hojaOrigen.getName()}`);
  const datos = hojaOrigen
    .getRange("W13:W" + hojaOrigen.getLastRow())
    .getValues();
  const check = hojaOrigen
    .getRange("U13:U" + hojaOrigen.getLastRow())
    .getValues(); // Acopiado?
  const columnaT = hojaOrigen
    .getRange("T13:T" + hojaOrigen.getLastRow())
    .getValues(); // Total de planta acopiada
  const columnaS = hojaOrigen
    .getRange("S13:S" + hojaOrigen.getLastRow())
    .getValues(); // Total de planta a acopiar

  // Recorrer las filas para verificar si la maquinaria está presente y si hay planta pendiente
  for (var i = 0; i < datos.length; i++) {
    // Verificar si existe la maquinaria
    if (datos[i][0] === maquinaria) {
      //Logger.log(`Estamos en la hoja: ${hojaOrigen.getName()}. Total ${columnaS[i][0]} - Acopiado ${columnaT[i][0]} - ${check[i][0]}`);
      // Verificar si la diferencia entre la columna T (planta acopiada) y S (planta pendiente) es distinta de 0
      if (columnaS[i][0] - columnaT[i][0] > 0 && check[i][0] === false) {
        return true; // Existen tanto la maquinaria como planta pendiente
      }
    }
  }
  return false; // No se encontró la maquinaria o no hay planta pendiente
}

/**
 * Función que verifica si un número de pedido existe en una hoja de destino.
 * @param {Sheet} hojaDestino - La hoja donde se busca el pedido.
 * @param {string} numPedido - Número de pedido a verificar.
 * @return {boolean} - Devuelve true si el pedido está presente, false en caso contrario.
 */
function existePedidoEnHoja(hojaDestino, numPedido) {
  // Obtener todos los valores de la columna C desde la fila 1 hasta la última con datos
  const datos = hojaDestino
    .getRange("C1:C" + hojaDestino.getLastRow())
    .getValues();
  // Recorrer la columna C y comparar con el número de pedido
  for (var i = 0; i < datos.length; i++) {
    if (datos[i][0] === numPedido) {
      return true; // Si se encuentra el pedido, devolver true
    }
  }
  return false; // Si no se encuentra el pedido, devolver false
}

/**
 * Función que establece el formato y configuración inicial de la hoja de destino.
 * @param {Sheet} hojaDestino - La hoja que se va a configurar.
 */
function SetearHoja(hojaDestino) {
  // Definir anchos de columna en un solo paso
  var anchos = [15, 15, 400, 100, 150, 30, 30, 30]; // Anchos para A-H
  // Recorrer los anchos y aplicarlos a las columnas correspondientes
  for (var i = 0; i < anchos.length; i++) {
    hojaDestino.setColumnWidth(i + 1, anchos[i]);
  }

  // Eliminar columnas desde la I en adelante
  hojaDestino.deleteColumns(9, hojaDestino.getMaxColumns() - 8);

  // Eliminar filas desde la 201 en adelante (dejando 200 filas)
  hojaDestino.deleteRows(201, hojaDestino.getMaxRows() - 200);

  // Ocultar líneas de cuadrícula para mejorar la visualización
  hojaDestino.setHiddenGridlines(true);

  // Configuración de la fuente y alineación de todo el rango de la hoja
  var rangoTotal = hojaDestino.getRange(
    1,
    1,
    hojaDestino.getMaxRows(),
    hojaDestino.getMaxColumns(),
  );
  rangoTotal
    .setFontFamily("Calibri")
    .setFontSize(9)
    .setVerticalAlignment("middle")
    .setHorizontalAlignment("left");

  // Cambiar color de texto en las columnas A y B a blanco
  hojaDestino.getRange("A:A").setFontColor("white");
  hojaDestino.getRange("B:B").setFontColor("white");

  // Activar la celda A1 para asegurar que esté seleccionada al abrir la hoja
  hojaDestino.getRange("A1").activate();
}

/**
 * Función que agrega una línea de pedido en la hoja de destino.
 * @param {Sheet} hojaOrigen - La hoja de donde se obtienen los datos del pedido.
 * @param {Sheet} hojaDestino - La hoja donde se agregará la línea de pedido.
 * @param {number} i - La fila en la hoja de destino donde se insertará la línea.
 * @param {number} j - La fila en la hoja de origen desde donde se tomará la información.
 */
/*function AgregarLineaDePedido(hojaOrigen, hojaDestino, i, j) {
  var descripcion = hojaOrigen.getRange('E' + j).getValue();
  var finca = hojaOrigen.getRange('Q' + j).getValue();

  var textoFinal = descripcion;
  var marcadoTexto = " - [MARCADO]";
  var noMarcadoTexto = " - [NO MARCADO]";
  var tieneMarcado = hojaOrigen.getRange('V' + j).getValue();
  var marcadoInicio = -1;

  marcadoInicio = textoFinal.length;
  if (tieneMarcado) {
    textoFinal += marcadoTexto;
  }
  else {
    textoFinal += noMarcadoTexto;
  }

  var fincaTexto = " - " + finca;
  textoFinal += fincaTexto;
  var fincaInicio = textoFinal.length - fincaTexto.length;

  var extra = hojaOrigen.getRange('X' + j).getValue();
  var extraInicio = 0;
  if (extra && extra.toString().trim().length > 0) {
    var extraString = "\n❗ " + extra + " ❗";
    extraInicio = textoFinal.length;
    textoFinal += extraString;
  }

  // Creamos el constructor de RichTextValue con el texto completo
  var builder = SpreadsheetApp.newRichTextValue().setText(textoFinal);

  // Estilo general para la descripción: color negro, sin negrita
  var estiloDescripcion = SpreadsheetApp.newTextStyle()
    .setForegroundColor('#000000')
    .setBold(false)
    .build();
  builder.setTextStyle(0, fincaInicio, estiloDescripcion);

  // Estilo para "[MARCADO]": azul
  if (tieneMarcado) {
    var estiloMarcado = SpreadsheetApp.newTextStyle().setForegroundColor('#0000ff').build();
    builder.setTextStyle(marcadoInicio, marcadoInicio + marcadoTexto.length, estiloMarcado);
  }
  // Estilo para "[NO MARCADO]": verde
  else {
    var estiloNoMarcado = SpreadsheetApp.newTextStyle().setForegroundColor('#2E7D32').build();
    builder.setTextStyle(marcadoInicio, marcadoInicio + noMarcadoTexto.length, estiloNoMarcado);
  }

  // Estilo para la finca: negrita y negro
  var estiloFinca = SpreadsheetApp.newTextStyle().setBold(true).setForegroundColor('#000000').build();
  builder.setTextStyle(fincaInicio, fincaInicio + fincaTexto.length, estiloFinca);

  // Estilo para el extra (si existe): rojo oscuro, subrayado, tamaño 11
  if (extraInicio > 0) {
    var estiloExtra = SpreadsheetApp.newTextStyle()
      .setForegroundColor('#8B0000')
      .setFontSize(11)
      .setUnderline(true)
      .build();
    builder.setTextStyle(extraInicio, textoFinal.length, estiloExtra);
  }

  // Aplicamos el texto enriquecido a la celda y ajustamos línea
  hojaDestino.getRange('C' + i).setRichTextValue(builder.build()).setWrap(true);

  // Transferimos los demás datos
  hojaDestino.getRange('A' + i).setValue(hojaOrigen.getRange('A' + j).getValue());
  hojaDestino.getRange('B' + i).setValue(hojaOrigen.getRange('B' + j).getValue());
  hojaDestino.getRange('D' + i).setValue(hojaOrigen.getRange('M' + j).getValue());
  hojaDestino.getRange('E' + i).setNumberFormat('@').setValue(hojaOrigen.getRange('O' + j).getDisplayValue());
  hojaDestino.getRange('F' + i).setValue(hojaOrigen.getRange('S' + j).getValue() - hojaOrigen.getRange('T' + j).getValue());
  hojaDestino.getRange('G' + i).setValue("");
  hojaDestino.getRange('H' + i).insertCheckboxes();
}*/

/**
 * Función que agrega una línea de pedido en la hoja de destino con formato completo.
 * @param {Sheet} hojaOrigen - La hoja de donde se obtienen los datos del pedido.
 * @param {Sheet} hojaDestino - La hoja donde se agregará la línea de pedido.
 * @param {number} i - La fila en la hoja de destino donde se insertará la línea.
 * @param {number} j - La fila en la hoja de origen desde donde se tomará la información.
 */
function AgregarLineaDePedido(hojaOrigen, hojaDestino, i, j) {
  // -----------------------------
  // 1️⃣ Limpiar la fila y aplicar formato base
  // -----------------------------
  var rangoFila = hojaDestino.getRange(i, 1, 1, 8); // Columnas A-H
  rangoFila.clearFormat(); // eliminar herencia
  rangoFila
    .setFontFamily("Calibri")
    .setFontSize(9)
    .setVerticalAlignment("middle")
    .setHorizontalAlignment("left");

  // Columnas A y B en blanco
  hojaDestino.getRange("A" + i).setFontColor("white");
  hojaDestino.getRange("B" + i).setFontColor("white");

  // -----------------------------
  // 2️⃣ Transferir valores básicos
  // -----------------------------
  hojaDestino
    .getRange("A" + i)
    .setValue(hojaOrigen.getRange("A" + j).getValue());
  hojaDestino
    .getRange("B" + i)
    .setValue(hojaOrigen.getRange("B" + j).getValue());
  hojaDestino
    .getRange("D" + i)
    .setValue(hojaOrigen.getRange("M" + j).getValue());
  hojaDestino
    .getRange("E" + i)
    .setNumberFormat("@")
    .setValue(hojaOrigen.getRange("O" + j).getDisplayValue());
  hojaDestino
    .getRange("F" + i)
    .setValue(
      hojaOrigen.getRange("S" + j).getValue() -
        hojaOrigen.getRange("T" + j).getValue(),
    );
  hojaDestino.getRange("G" + i).setValue("");

  // -----------------------------
  // 3️⃣ Insertar checkbox solo si no existe
  // -----------------------------
  var celdaH = hojaDestino.getRange("H" + i);
  if (!celdaH.isChecked()) {
    // solo crea si no existe
    celdaH.insertCheckboxes();
  }

  // -----------------------------
  // 4️⃣ Construir texto enriquecido en columna C
  // -----------------------------
  var descripcion = hojaOrigen.getRange("E" + j).getValue();
  var finca = hojaOrigen.getRange("Q" + j).getValue();

  var textoFinal = descripcion;
  var marcadoTexto = " - [MARCADO]";
  var noMarcadoTexto = " - [NO MARCADO]";
  var tieneMarcado = hojaOrigen.getRange("V" + j).getValue();
  var marcadoInicio = textoFinal.length;

  textoFinal += tieneMarcado ? marcadoTexto : noMarcadoTexto;

  var fincaTexto = " - " + finca;
  var fincaInicio = textoFinal.length;
  textoFinal += fincaTexto;

  var extra = hojaOrigen.getRange("X" + j).getValue();
  var extraInicio = 0;
  if (extra && extra.toString().trim().length > 0) {
    var extraString = "\n❗ " + extra + " ❗";
    extraInicio = textoFinal.length;
    textoFinal += extraString;
  }

  // -----------------------------
  // 5️⃣ Aplicar estilos RichText
  // -----------------------------
  var builder = SpreadsheetApp.newRichTextValue().setText(textoFinal);

  // Descripción
  var estiloDescripcion = SpreadsheetApp.newTextStyle()
    .setForegroundColor("#000000")
    .setBold(false)
    .build();
  builder.setTextStyle(0, fincaInicio, estiloDescripcion);

  // Marcado / No marcado
  if (tieneMarcado) {
    var estiloMarcado = SpreadsheetApp.newTextStyle()
      .setForegroundColor("#0000ff")
      .build();
    builder.setTextStyle(
      marcadoInicio,
      marcadoInicio + marcadoTexto.length,
      estiloMarcado,
    );
  } else {
    var estiloNoMarcado = SpreadsheetApp.newTextStyle()
      .setForegroundColor("#2E7D32")
      .build();
    builder.setTextStyle(
      marcadoInicio,
      marcadoInicio + noMarcadoTexto.length,
      estiloNoMarcado,
    );
  }

  // Finca
  var estiloFinca = SpreadsheetApp.newTextStyle()
    .setBold(true)
    .setForegroundColor("#000000")
    .build();
  builder.setTextStyle(
    fincaInicio,
    fincaInicio + fincaTexto.length,
    estiloFinca,
  );

  // Extra
  if (extraInicio > 0) {
    var estiloExtra = SpreadsheetApp.newTextStyle()
      .setForegroundColor("#8B0000")
      .setFontSize(11)
      .setUnderline(true)
      .build();
    builder.setTextStyle(extraInicio, textoFinal.length, estiloExtra);
  }

  hojaDestino
    .getRange("C" + i)
    .setRichTextValue(builder.build())
    .setWrap(true);

  // -----------------------------
  // 6️⃣ Alineación de columnas específicas
  // -----------------------------
  hojaDestino.getRange("D" + i + ":F" + i).setHorizontalAlignment("center"); // D-F centradas
  hojaDestino.getRange("G" + i).setHorizontalAlignment("center"); // G centrada
  hojaDestino.getRange("H" + i).setHorizontalAlignment("center"); // H centrada
}

/**
 * 📌 Función para actualizar o agregar líneas en un pedido ya acopiado.
 * Se busca el pedido en la hojaDestino a partir del número de pedido (columna C) que
 * coincide con la celda L7 de la hojaOrigen. La primera línea del pedido se encuentra
 * 5 filas después de donde está el número de pedido.
 *
 * Para cada línea en hojaOrigen (desde la fila 13) que cumple:
 *   - Columna W igual a "maquinaria"
 *   - Columna U es false (no acopiada)
 * se extraen:
 *   - Número de línea (columna A)
 *   - Referencia (columna B)
 *   - Talla (columna M, que va a columna D en destino)
 *   - Sector (columna O, que va a columna E en destino)
 *   - Cantidad = (columna S - columna T) que va a columna F en destino
 *
 * Si ya existe en el bloque de pedido en hojaDestino (desde la fila donde empieza el bloque,
 * que es 5 filas después del número de pedido, hasta la última línea contigua) una línea con las mismas claves:
 *   - Si la cantidad es distinta, se actualiza.
 * Si no existe, se inserta una nueva fila en blanco (completamente limpia) en la posición correcta y se llama a
 * AgregarLineaDePedido para copiar la línea.
 *
 * @param {Sheet} hojaOrigen - Hoja de origen con los datos del pedido.
 * @param {Sheet} hojaDestino - Hoja de acopio donde se acumulan los pedidos del día.
 * @param {string} maquinaria - Valor de la maquinaria a filtrar (columna W en hojaOrigen).
 */
/*function ActualizarPedido(hojaOrigen, hojaDestino, maquinaria) {
  // 1. Obtener el identificador del pedido de hojaOrigen (celda L7)
  var pedidoID = hojaOrigen.getRange("L7").getValue();
  
  // Buscar en hojaDestino la fila donde se encuentre el número de pedido en la columna C
  var lastRowDestino = hojaDestino.getLastRow();
  var dataColC = hojaDestino.getRange(1, 3, lastRowDestino, 1).getValues(); // Columna C
  var headerRowDestino = -1;
  
  for (var i = 0; i < dataColC.length; i++) {
    if (dataColC[i][0] === pedidoID) {
      headerRowDestino = i + 1; // filas son 1-indexadas
      break;
    }
  }
  
  if (headerRowDestino === -1) {
    //Logger.log("⚠️ Pedido " + pedidoID + " no encontrado en hojaDestino.");
    return;
  }
  //Logger.log("Pedido " + pedidoID + " encontrado en fila " + headerRowDestino + " de hojaDestino.");
  
  // 2. Definir el bloque de líneas de pedido
  // La primera línea de pedido es 5 filas después de la fila del número de pedido
  var blockStart = headerRowDestino + 5;
  var blockEnd = blockStart - 1;
  
  // Recorrer desde blockStart hasta lastRowDestino para determinar el final del bloque
  for (var r = blockStart; r <= lastRowDestino; r++) {
    var val = hojaDestino.getRange(r, 1).getValue(); // Columna A: número de línea
    if (val === "" || val == null) {
      break;
    }
    blockEnd = r;
  }
  //Logger.log("Bloque de pedido en hojaDestino: filas " + blockStart + " a " + blockEnd);
  
  // 3. Recorrer las líneas de hojaOrigen (desde fila 13)
  var lastRowOrigen = hojaOrigen.getLastRow();
  var indiceDestino = blockStart; // Variable para controlar dónde estamos en hojaDestino
  
  for (var j = 13; j <= lastRowOrigen; j++) {
    var maquinaValor = hojaOrigen.getRange("W" + j).getValue();
    var acopiado = hojaOrigen.getRange("U" + j).getValue();
    
    if (maquinaValor === maquinaria && acopiado === false) {
      // Extraer claves de la línea en hojaOrigen
      var numLinea = hojaOrigen.getRange("A" + j).getValue();
      var referencia = hojaOrigen.getRange("B" + j).getValue();
      var talla = hojaOrigen.getRange("M" + j).getValue(); // va a columna D en destino
      var sector = hojaOrigen.getRange("O" + j).getValue();  // va a columna E en destino
      var cantidadNueva = hojaOrigen.getRange("S" + j).getValue() - hojaOrigen.getRange("T" + j).getValue();
      
      var lineaEncontrada = false;
      var insertRow = blockEnd + 1;
      
      // 4. Buscar si la línea ya existe en hojaDestino
      while (indiceDestino <= blockEnd && !lineaEncontrada) {
        var numLineaDestino = hojaDestino.getRange(indiceDestino, 1).getValue(); // Columna A (Número de línea)
        var refDestino = hojaDestino.getRange(indiceDestino, 2).getValue(); // Columna B (Referencia)
        var tallaDestino = hojaDestino.getRange(indiceDestino, 4).getValue(); // Columna D (Talla)
        var sectorDestino = hojaDestino.getRange(indiceDestino, 5).getValue(); // Columna E (Sector)
        var cantidadDestino = hojaDestino.getRange(indiceDestino, 6).getValue(); // Columna F (Unidades)

        if (numLinea === numLineaDestino && referencia === refDestino && talla === tallaDestino && sector === sectorDestino) {
          // 5. Si ya existe y la cantidad nueva es distinta de la que ya había, actualizar la cantidad en columna F
          if (cantidadNueva !== cantidadDestino) {
            var celdaCantidad = hojaDestino.getRange(indiceDestino, 6);
            celdaCantidad.setValue(cantidadNueva);
            celdaCantidad.setBackground("#FFF2CC"); // Amarillo suave
            celdaCantidad.setComment("Cantidad modificada de " + cantidadDestino + " a " + cantidadNueva + " para acopio de planta.");
            //Logger.log("✅ Línea de pedido actualizada en fila " + indiceDestino);
          }
          indiceDestino++; // Avanzamos para que la siguiente búsqueda empiece desde aquí
          lineaEncontrada = true;
          break;
        } else if (numLinea < numLineaDestino) {
          insertRow = indiceDestino;
          break;
        }
        indiceDestino++; // Avanzamos en cada iteración
      }

      // 6. Si no existe, agregar la línea en el lugar correcto dentro del bloque
      if (!lineaEncontrada) {
        hojaDestino.insertRowBefore(insertRow);
        
        // Llamar a AgregarLineaDePedido para copiar la línea
        AgregarLineaDePedido(hojaOrigen, hojaDestino, insertRow, j);

        // Formato final
        hojaDestino.getRange('C' + insertRow).setFontStyle('italic');
        hojaDestino.getRange('E' + insertRow).setFontStyle('italic').setFontWeight('bold');
        hojaDestino.getRange('G' + insertRow).setBackground('#B0B0B0').setFontWeight('bold');
        hojaDestino.getRange('D' + insertRow + ':H' + insertRow).setHorizontalAlignment('center');

        // Marcar la fila como nueva
        hojaDestino.getRange('C' + insertRow + ':E' + insertRow).setBackground("#FFEB9C"); // Amarillo diferente
        hojaDestino.getRange('C' + insertRow).setComment("Nueva línea de acopio añadida.");
        
        //Logger.log("➕ Nueva línea de pedido agregada y seteada en fila " + insertRow);
        blockEnd++; // Expandir el bloque del pedido
        indiceDestino = insertRow + 1; // Continuamos desde la siguiente línea
      }
    }
  }
}*/

/**
 * Actualiza o inserta líneas de un pedido en la hoja de destino (acopio por planta).
 *
 * Lógica de negocio:
 * - Se identifica el pedido en hojaDestino mediante el ID en L7 (columna C).
 * - Se determina el bloque de líneas del pedido (5 filas debajo del header).
 * - Para cada línea de hojaOrigen:
 *      - Si pertenece a la maquinaria indicada
 *      - Y no está marcada como acopiada
 * - Se construye una clave primaria compuesta:
 *      (numLinea, referencia, talla, sector)
 * - Si la línea ya existe en destino:
 *      - Se actualiza la cantidad SOLO si:
 *            • La cantidad es distinta
 *            • El checkbox (col H) NO está marcado
 * - Si no existe:
 *      - Se inserta como nueva línea dentro del bloque del pedido
 *
 * Protección implementada:
 * - Normalización de datos (evita errores por espacios, saltos de línea o tipos distintos)
 * - Uso de clave compuesta estable
 * - Respeto a líneas cerradas manualmente (checkbox TRUE)
 * - Lectura del bloque destino en memoria para evitar inconsistencias
 *
 * @param {Sheet} hojaOrigen  Hoja con el pedido completo
 * @param {Sheet} hojaDestino Hoja de acopio por planta
 * @param {string} maquinaria Valor de maquinaria que debe filtrarse (col W)
 */
function ActualizarPedido(hojaOrigen, hojaDestino, maquinaria) {
  /**
   * Normaliza un valor para comparación:
   * - Convierte a string
   * - Elimina saltos de línea
   * - Elimina espacios al inicio y final
   */
  function normalizar(valor) {
    return String(valor).replace(/\r?\n/g, " ").trim();
  }

  /**
   * Construye la clave primaria compuesta
   * Formato: numLinea|referencia|talla|sector
   * Garantiza comparación robusta y sin ambigüedad
   */
  function construirClave(a, b, c, d) {
    return [normalizar(a), normalizar(b), normalizar(c), normalizar(d)].join(
      "|",
    );
  }

  // ==========================================================
  // 1️⃣ IDENTIFICACIÓN DEL PEDIDO EN HOJA DESTINO
  // ==========================================================

  var pedidoID = hojaOrigen.getRange("L7").getValue();
  var lastRowDestino = hojaDestino.getLastRow();
  if (lastRowDestino === 0) return;

  // Buscar el pedido en columna C
  var dataColC = hojaDestino.getRange(1, 3, lastRowDestino, 1).getValues();
  var headerRowDestino = -1;

  for (var i = 0; i < dataColC.length; i++) {
    if (dataColC[i][0] === pedidoID) {
      headerRowDestino = i + 1; // Conversión a índice real (1-based)
      break;
    }
  }

  // Si no se encuentra el pedido, se aborta
  if (headerRowDestino === -1) return;

  // ==========================================================
  // 2️⃣ DETERMINACIÓN DEL BLOQUE DE LÍNEAS DEL PEDIDO
  // ==========================================================

  var blockStart = headerRowDestino + 5;
  var blockEnd = blockStart - 1;

  // Detectar hasta dónde llegan las líneas contiguas
  for (var r = blockStart; r <= lastRowDestino; r++) {
    var val = hojaDestino.getRange(r, 1).getValue();
    if (val === "" || val == null) break;
    blockEnd = r;
  }

  // Leer bloque completo en memoria (columnas A-H)
  var bloqueDatos = [];
  if (blockEnd >= blockStart) {
    bloqueDatos = hojaDestino
      .getRange(blockStart, 1, blockEnd - blockStart + 1, 8)
      .getValues();
  }

  // ==========================================================
  // 3️⃣ CONSTRUCCIÓN DE MAPA DE LÍNEAS EXISTENTES
  // ==========================================================

  /**
   * Estructura:
   * mapaDestino[clave] = {
   *      row: número de fila real en hojaDestino,
   *      cantidad: valor columna F,
   *      checkbox: valor columna H
   * }
   */
  var mapaDestino = {};

  for (var i = 0; i < bloqueDatos.length; i++) {
    var fila = bloqueDatos[i];

    var clave = construirClave(
      fila[0], // Col A → numLinea
      fila[1], // Col B → referencia
      fila[3], // Col D → talla
      fila[4], // Col E → sector
    );

    mapaDestino[clave] = {
      row: blockStart + i,
      cantidad: fila[5], // Col F
      checkbox: fila[7], // Col H
    };
  }

  // ==========================================================
  // 4️⃣ RECORRIDO DE LÍNEAS EN HOJA ORIGEN
  // ==========================================================

  var lastRowOrigen = hojaOrigen.getLastRow();

  for (var j = 13; j <= lastRowOrigen; j++) {
    var maquinaValor = hojaOrigen.getRange("W" + j).getValue();
    var acopiado = hojaOrigen.getRange("U" + j).getValue();

    // Solo procesar líneas correspondientes a esta planta y no acopiadas
    if (maquinaValor !== maquinaria || acopiado !== false) continue;

    // Extraer datos clave
    var numLinea = hojaOrigen.getRange("A" + j).getValue();
    var referencia = hojaOrigen.getRange("B" + j).getValue();
    var talla = hojaOrigen.getRange("M" + j).getValue();
    var sector = hojaOrigen.getRange("O" + j).getValue();

    var cantidadNueva =
      hojaOrigen.getRange("S" + j).getValue() -
      hojaOrigen.getRange("T" + j).getValue();

    var claveOrigen = construirClave(numLinea, referencia, talla, sector);

    // ======================================================
    // 5️⃣ SI LA LÍNEA YA EXISTE EN DESTINO
    // ======================================================

    if (mapaDestino.hasOwnProperty(claveOrigen)) {
      var info = mapaDestino[claveOrigen];
      var rowDestino = info.row;
      var cantidadDestino = info.cantidad;
      var checkboxMarcado = info.checkbox;

      // Solo actuar si la cantidad difiere
      if (cantidadNueva !== cantidadDestino) {
        // Si el operario ya cerró la línea, no modificar
        if (checkboxMarcado === true) {
          hojaDestino
            .getRange(rowDestino, 6)
            .setComment(
              "No actualizada: línea cerrada manualmente por operario.",
            );
        } else {
          // Actualización autorizada
          hojaDestino
            .getRange(rowDestino, 6)
            .setValue(cantidadNueva)
            .setBackground("#FFF2CC")
            .setComment(
              "Cantidad modificada de " +
                cantidadDestino +
                " a " +
                cantidadNueva +
                " para acopio de planta.",
            );
        }
      }
    } else {
      // ======================================================
      // 6️⃣ SI NO EXISTE → INSERTAR NUEVA LÍNEA
      // ======================================================

      var insertRow = blockEnd + 1; // por defecto al final

      for (var d = blockStart; d <= blockEnd; d++) {
        var numLineaDestino = hojaDestino.getRange(d, 1).getValue();

        if (Number(numLineaDestino) > Number(numLinea)) {
          insertRow = d;
          break;
        }
      }

      hojaDestino.insertRowBefore(insertRow);

      AgregarLineaDePedido(hojaOrigen, hojaDestino, insertRow, j);

      // Aplicación de formato visual estándar
      hojaDestino.getRange("C" + insertRow).setFontStyle("italic");
      hojaDestino
        .getRange("E" + insertRow)
        .setFontStyle("italic")
        .setFontWeight("bold");
      hojaDestino
        .getRange("G" + insertRow)
        .setBackground("#B0B0B0")
        .setFontWeight("bold");
      hojaDestino
        .getRange("D" + insertRow + ":H" + insertRow)
        .setHorizontalAlignment("center");

      // Marca la fila como nueva
      hojaDestino
        .getRange("C" + insertRow + ":E" + insertRow)
        .setBackground("#FFEB9C"); // Amarillo diferente

      hojaDestino
        .getRange("C" + insertRow)
        .setComment("Nueva línea de acopio añadida.");

      blockEnd++;
    }
  }
}

/**
 * Copia un pedido desde una hoja de origen a una hoja de destino, aplicando formato y condiciones específicas.
 *
 * @param {GoogleAppsScript.Spreadsheet.Sheet} hojaIndice - Hoja de cálculo donde esta el indice del libro.
 * @param {GoogleAppsScript.Spreadsheet.Sheet} hojaOrigen - Hoja de cálculo desde la cual se copiará el pedido.
 * @param {GoogleAppsScript.Spreadsheet.Sheet} hojaDestino - Hoja de cálculo donde se registrará el pedido copiado.
 * @param {string} maquinaria - Filtro para seleccionar solo las líneas de pedido asociadas a una determinada maquinaria.
 */
function AcopiarPedido(hojaIndice, hojaOrigen, hojaDestino, maquinaria) {
  const ultimo = hojaOrigen.getLastRow(); // Obtiene la última fila con datos en la hoja de origen
  var i = hojaDestino.getLastRow(); // Obtiene la última fila con datos en la hoja de destino

  // Si la hoja de destino ya tiene pedidos, añade un borde doble como separación entre pedidos previos y el nuevo
  if (i != 0) {
    verificarYAgregarFilas(hojaDestino, i + 1, 1); // Asegura que haya espacio para la separación
    hojaDestino
      .getRange("C" + (i + 1) + ":G" + (i + 1))
      .setBorder(
        null,
        null,
        true,
        null,
        null,
        null,
        "#000000",
        SpreadsheetApp.BorderStyle.DOUBLE,
      );
    i = i + 1; // Avanza una fila para continuar con el nuevo pedido
  }

  // Agrega la cabecera del pedido en la hoja de destino
  verificarYAgregarFilas(hojaDestino, i + 1, 3); // Asegura que haya al menos 3 filas disponibles para la cabecera

  // C (i + 1) → Texto en blanco, fuente Calibri
  let celdaC1 = hojaDestino.getRange("C" + (i + 1));
  celdaC1.setValue(hojaOrigen.getRange("L7").getValue());
  celdaC1.setFontColor("white");
  celdaC1.setFontSize(11); // Por consistencia
  celdaC1.setFontStyle("normal");
  celdaC1.setFontWeight("normal");
  celdaC1.setFontFamily("Calibri");

  // C (i + 2) → Fuente 11 / Cursiva / Negro / Calibri
  let celdaC2 = hojaDestino.getRange("C" + (i + 2));
  celdaC2.setValue("FINCA: " + hojaOrigen.getRange("P2").getValue());
  celdaC2.setFontSize(11);
  celdaC2.setFontStyle("italic");
  celdaC2.setFontWeight("normal");
  celdaC2.setFontColor("black");
  celdaC2.setFontFamily("Calibri");

  hojaDestino
    .getRange("D" + (i + 2) + ":G" + (i + 2))
    .mergeAcross()
    .setHorizontalAlignment("center");

  // D (i + 3) → Fuente 11 / Cursiva / Negrita / Azul / Calibri
  var zona = hojaOrigen.getRange("P4").getValue();
  if (zona != "") {
    let celdaD2 = hojaDestino.getRange("D" + (i + 2));
    celdaD2.setValue("ZONA: " + zona);
    celdaD2.setFontSize(11);
    celdaD2.setFontStyle("italic");
    celdaD2.setFontWeight("bold");
    celdaD2.setFontColor("blue");
    celdaD2.setFontFamily("Calibri");
  }

  // C (i + 3) → Fuente 11 / Cursiva / Negrita / Negro / Calibri
  let celdaC3 = hojaDestino.getRange("C" + (i + 3));
  celdaC3.setValue(hojaOrigen.getRange("D2").getValue());
  celdaC3.setFontSize(11);
  celdaC3.setFontStyle("italic");
  celdaC3.setFontWeight("bold");
  celdaC3.setFontColor("black");
  celdaC3.setFontFamily("Calibri");

  hojaDestino
    .getRange("D" + (i + 3) + ":G" + (i + 3))
    .mergeAcross()
    .setHorizontalAlignment("center");

  // D (i + 3) → Fuente 11 / Cursiva / Negrita / Negro / Calibri
  let celdaD3 = hojaDestino.getRange("D" + (i + 3));
  celdaD3.setValue("COMERCIAL: " + hojaOrigen.getRange("E4").getValue());
  celdaD3.setFontSize(11);
  celdaD3.setFontStyle("italic");
  celdaD3.setFontWeight("bold");
  celdaD3.setFontColor("black");
  celdaD3.setFontFamily("Calibri");

  // Si el pedido tiene una marca específica en la celda 'A9', la resalta en rojo
  var marca = hojaOrigen.getRange("A9").getValue();
  if (marca != "") {
    // C (i + 4) → Fuente 11 / Negrita / Rojo / Calibri
    let celdaC4 = hojaDestino.getRange("C" + (i + 4));
    celdaC4.setValue(marca);
    celdaC4.setFontSize(11);
    celdaC4.setFontStyle("normal");
    celdaC4.setFontWeight("bold");
    celdaC4.setFontColor("red");
    celdaC4.setFontFamily("Calibri");
    /*hojaDestino.getRange('C' + (i + 4)).setValue(marca)
      .setFontWeight('bold').setFontSize(11).setFontColor('#ff0000');*/
  }

  // Agrega cabeceras para las líneas de pedido
  verificarYAgregarFilas(hojaDestino, i + 5, 1);
  // Aplicar formato general al rango C(i+5):G(i+5)
  let rangoFila = hojaDestino.getRange("C" + (i + 5) + ":G" + (i + 5));
  rangoFila
    .setFontWeight("bold")
    .setFontStyle("normal")
    .setFontSize(10)
    .setFontColor("black")
    .setHorizontalAlignment("center") // Se ajusta por defecto a todas las celdas
    .setBorder(
      true,
      true,
      true,
      true,
      null,
      null,
      "#000000",
      SpreadsheetApp.BorderStyle.SOLID_THICK,
    );

  // Establecer valores y alineaciones específicas
  hojaDestino
    .getRange("C" + (i + 5))
    .setValue("DESCRIPCIÓN")
    .setHorizontalAlignment("left");
  hojaDestino.getRange("D" + (i + 5)).setValue("TALLA");
  hojaDestino.getRange("E" + (i + 5)).setValue("SECTOR");
  hojaDestino.getRange("F" + (i + 5)).setValue("TOTAL");

  // Fusionar F y G para mejorar la presentación
  hojaDestino.getRange("F" + (i + 5) + ":G" + (i + 5)).mergeAcross();

  i = i + 6; // Avanza después de las cabeceras de las líneas de pedido
  var f = i; // Guarda la posición inicial de las líneas de pedido

  // Copia las líneas de pedido desde la hoja de origen a la hoja de destino
  for (var j = 13; j <= ultimo; j++) {
    if (
      hojaOrigen.getRange("W" + j).getValue() == maquinaria &&
      hojaOrigen.getRange("U" + j).getValue() === false
    ) {
      verificarYAgregarFilas(hojaDestino, i, 1); // Asegura que haya espacio para la nueva línea
      AgregarLineaDePedido(hojaOrigen, hojaDestino, i, j); // Llama a la función que agrega la línea de pedido
      //rellenarIndice(hojaIndice, hojaOrigen.getRange("N7").getValue(), hojaOrigen.getRange("Q" + j).getValue())
      i++; // Avanza a la siguiente fila en la hoja de destino
    }
  }

  i--; // Ajusta el índice al último elemento agregado

  // Aplica formato a las líneas de pedido copiadas
  let rangoPedido = hojaDestino.getRange("C" + f + ":H" + i);
  rangoPedido.setFontFamily("Calibri").setFontSize(9);

  // C(f:i) → Cursiva
  hojaDestino.getRange("C" + f + ":C" + i).setFontStyle("italic");

  // D(f:i) - G(f:i) → Alineado al centro, color negro
  hojaDestino
    .getRange("D" + f + ":G" + i)
    .setHorizontalAlignment("center")
    .setFontColor("black");

  // D(f:i) → Sin negrita ni cursiva
  hojaDestino
    .getRange("D" + f + ":D" + i)
    .setFontWeight("normal")
    .setFontStyle("normal");

  // E(f:i) → Cursiva y Negrita
  hojaDestino
    .getRange("E" + f + ":E" + i)
    .setFontStyle("italic")
    .setFontWeight("bold");

  // F(f:i) → Sin negrita ni cursiva
  hojaDestino
    .getRange("F" + f + ":F" + i)
    .setFontWeight("normal")
    .setFontStyle("normal");

  // G(f:i) → Fondo gris (#B0B0B0), Negrita
  hojaDestino
    .getRange("G" + f + ":G" + i)
    .setBackground("#B0B0B0")
    .setFontWeight("bold");

  // H(f:i) → Alineado al centro
  hojaDestino.getRange("H" + f + ":H" + i).setHorizontalAlignment("center");

  // Protege la hoja permitiendo la edición solo en los rangos especificados con checkbox de validación
  protegerHojaConCheckBox(hojaDestino, f, i);
}

/**
 * Verifica si la hoja tiene suficientes filas disponibles y agrega más si es necesario.
 * @param {GoogleAppsScript.Spreadsheet.Sheet} hojaDestino - La hoja donde se agregan las líneas.
 * @param {number} fila - Número de fila donde se quiere escribir.
 * @param {number} cantidad - Cantidad de filas que se necesitan.
 */
function verificarYAgregarFilas(hojaDestino, fila, cantidad) {
  var totalFilas = hojaDestino.getMaxRows();
  if (fila + cantidad - 1 > totalFilas) {
    var filasFaltantes = fila + cantidad - 1 - totalFilas;
    hojaDestino.insertRowsAfter(totalFilas, filasFaltantes);

    // Limpiar formato de las nuevas filas insertadas
    var rangoNuevas = hojaDestino.getRange(
      totalFilas + 1,
      1,
      filasFaltantes,
      hojaDestino.getMaxColumns(),
    );
    rangoNuevas.clearFormat();

    // Aplicar formato base
    rangoNuevas
      .setFontFamily("Calibri")
      .setFontSize(9)
      .setVerticalAlignment("middle")
      .setHorizontalAlignment("left");

    // 🔹 Columnas A y B en blanco para las nuevas filas
    hojaDestino
      .getRange(totalFilas + 1, 1, filasFaltantes, 1)
      .setFontColor("white"); // Columna A
    hojaDestino
      .getRange(totalFilas + 1, 2, filasFaltantes, 1)
      .setFontColor("white"); // Columna B
  }
}

/**
 * Protege una hoja de cálculo permitiendo la edición solo en ciertos rangos específicos.
 * También aplica validación de datos en un rango de checkboxes.
 *
 * @param {GoogleAppsScript.Spreadsheet.Sheet} hojaDestino - La hoja donde se aplicará la protección y validación.
 * @param {number} f - Número de fila donde comienza el rango de checkboxes.
 * @param {number} i - Número de fila donde termina el rango de checkboxes.
 */
/*function protegerHojaConCheckBox(hojaDestino, f, i) {
    // 1️⃣ Aplicar validación de datos para permitir solo TRUE o FALSE en los checkboxes
    var rangoCheckboxes = hojaDestino.getRange('H' + f + ':H' + i);
    var reglaValidacion = SpreadsheetApp.newDataValidation()
        .requireCheckbox() // Establece que la celda solo permita checkbox (TRUE o FALSE)
        .build();
    rangoCheckboxes.setDataValidation(reglaValidacion);
  
    // 2️⃣ Proteger la hoja y permitir edición solo en los rangos especificados
    var proteccion = hojaDestino.protect().setDescription("Hoja protegida, solo ciertos rangos son editables.");
    proteccion.removeEditors(proteccion.getEditors()); // Elimina editores anteriores
    proteccion.addEditor(Session.getEffectiveUser().getEmail()); // Mantiene permiso para el propietario

    // Obtener los rangos que ya están desprotegidos
    var rangosDesprotegidos = proteccion.getUnprotectedRanges();

    // Agregar los nuevos rangos de edición sin eliminar los anteriores
    var rangosEditables = [
        hojaDestino.getRange('G' + f + ':H' + i) // G: número a acopiar, H: checkbox
    ];

    // Combina los rangos anteriores con los nuevos
    var todosLosRangos = rangosDesprotegidos.concat(rangosEditables);

    // Establecer los nuevos rangos desprotegidos
    proteccion.setUnprotectedRanges(todosLosRangos);

    //Logger.log("Protección aplicada al rango G" + f + ":H" + i);
}*/

/**
 * Aplica validación tipo checkbox en columna H y protege la hoja,
 * permitiendo edición únicamente en columnas G y H del rango indicado.
 *
 * Características:
 * - No elimina editores existentes.
 * - No restringe al propietario del documento.
 * - Reutiliza protección si ya existe.
 * - Añade rangos desprotegidos sin sobrescribir los anteriores.
 * - Evita duplicidad de rangos.
 *
 * @param {GoogleAppsScript.Spreadsheet.Sheet} hojaDestino Hoja sobre la que se aplica la protección.
 * @param {number} f Fila inicial del bloque de líneas del pedido.
 * @param {number} i Fila final del bloque de líneas del pedido.
 */
function protegerHojaConCheckBox(hojaDestino, f, i) {
  // ==========================
  // 1️⃣ Aplicar validación checkbox en columna H
  // ==========================
  var rangoCheckboxes = hojaDestino.getRange("H" + f + ":H" + i);
  var regla = SpreadsheetApp.newDataValidation().requireCheckbox().build();

  rangoCheckboxes.setDataValidation(regla);

  // ==========================
  // 2️⃣ Obtener o crear protección de hoja
  // ==========================
  var protecciones = hojaDestino.getProtections(
    SpreadsheetApp.ProtectionType.SHEET,
  );
  var proteccion;

  if (protecciones.length > 0) {
    proteccion = protecciones[0];
  } else {
    proteccion = hojaDestino.protect();
    proteccion.setDescription("Protección automática del sistema.");
  }

  // ==========================
  // 3️⃣ Añadir rango editable G:H
  // ==========================
  var nuevoRango = hojaDestino.getRange("G" + f + ":H" + i);
  var rangosDesprotegidos = proteccion.getUnprotectedRanges();

  // Evitar duplicar el mismo rango
  var existe = rangosDesprotegidos.some(function (r) {
    return r.getA1Notation() === nuevoRango.getA1Notation();
  });

  if (!existe) {
    rangosDesprotegidos.push(nuevoRango);
    proteccion.setUnprotectedRanges(rangosDesprotegidos);
  }
}
