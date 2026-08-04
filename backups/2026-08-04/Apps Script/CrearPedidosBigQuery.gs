/**
 * Nombre del archivo "CrearPedidosBigQuery.gs"
 */
/**
 * Función principal para crear hojas de pedido a partir de los datos en BigQuery.
 */
function crearPedido() {
  // Consulta SQL con las uniones necesarias y ordenación
  const query = `
    SELECT 
      p.NUMERO_PEDIDO,
      p.NUMERO_CLIENTE AS CODIGO_CLIENTE,
      c.N_FISCAL AS NOMBRE_FISCAL_CLIENTE,
      c.N_COMERCIAL AS NOMBRE_COMERCIAL_CLIENTE,
      a.NOMBRE_AGENTE AS NOMBRE_COMERCIAL_PEDIDO,
      p.REFERENCIA_PEDIDO,
      p.FINCA_CARGA,
      p.SECTOR_CARGA,
      p.FECHA_CARGA,
      p.FECHA_PEDIDO,
      p.MARCA_PEDIDO,
      lp.POSICION_PEDIDO,
      lp.REFERENCIA_ARTICULO,
      lp.DESCRIPCION_ARTICULO,
      lp.UNIDADES,
      lp.UNIDADES_PENDIENTES,
      lp.MARCADO,
      lp.IMPRIMIR_LINEA,
      lp.FINCA_RELEVADA,
      lp.SECTOR_RELEVADO,
      lp.MARCA,
      lp.UBICACION_EXTRA,
      lp.ACCION_LOGISTICA,
      lp.NOTA_LINEA_PEDIDO,
      lit.DESCRIPCION_LITRAJE AS TALLA_PLANTA,
      sec.DESCRIPCION_SECTOR AS SECTOR_DESCRIPCION,
      art.GLOBALGAP,
      CASE 
        WHEN art.FINCA_ARTICULO LIKE '%EXT 1%' THEN 'FERRIOL'
        WHEN art.FINCA_ARTICULO LIKE '%EXT 2%' THEN 'TORREGROSA'
        WHEN art.FINCA_ARTICULO LIKE '%EXT 3%' THEN 'GROWING GREEN'
        WHEN art.FINCA_ARTICULO LIKE '%EXT 4%' THEN 'AMOROS'
        WHEN art.FINCA_ARTICULO LIKE '%EXT 5%' THEN 'LINK & WIN'
        ELSE art.FINCA_ARTICULO
      END AS FINCA_ARTICULO
    FROM 
      \`dashboard-439511.GestionComercialVE.PEDIDOS\` AS p
    JOIN 
      \`dashboard-439511.GestionComercialVE.LINEA_PEDIDO\` AS lp 
        ON p.NUMERO_PEDIDO = lp.NUMERO_PEDIDO
    JOIN 
      \`dashboard-439511.GestionComercialVE.CLIENTE\` AS c 
        ON p.NUMERO_CLIENTE = c.ID_CLIENTE
    JOIN 
      \`dashboard-439511.GestionComercialVE.AGENTE\` AS a 
        ON p.CODIGO_AGENTE = a.ID_AGENTE
    LEFT JOIN 
      \`dashboard-439511.GestionComercialVE.LITRAJES\` AS lit 
        ON lp.CODIGO_LITRAJE = lit.ID_LITRAJE
    LEFT JOIN 
      \`dashboard-439511.GestionComercialVE.SECTORES\` AS sec 
        ON lp.CODIGO_SECTOR = sec.ID_SECTOR
        LEFT JOIN 
      \`dashboard-439511.GestionComercialVE.ARTICULOS\` AS art 
        ON lp.REFERENCIA_ARTICULO = art.ID_ARTICULO
    WHERE 
      p.ESTADO_PEDIDO IN (1, 3)
      AND DATE(p.FECHA_CARGA) >= CURRENT_DATE('Europe/Madrid')
      AND lp.IMPRIMIR_LINEA = 0
      AND lp.UNIDADES_PENDIENTES > 0
      AND p.FINCA_CARGA = 'LA FÁBRICA'
    ORDER BY 
      DATE(p.FECHA_CARGA), 
      p.NUMERO_PEDIDO, 
      lp.POSICION_PEDIDO
  `;

  const request = {
    query: query,
    useLegacySql: false,
  };

  try {
    // Ejecutar la consulta en BigQuery
    const response = BigQuery.Jobs.query(request, "dashboard-439511");
    const rows = response.rows;

    if (!rows || rows.length === 0) {
      //Logger.log('No se encontraron pedidos nuevos');
      return;
    }

    // Convertir las filas en objetos con nombres de campo usando el esquema
    const parsedRows = rows.map((row) => parseRow(row, response.schema));

    // Agrupar filas por NUMERO_PEDIDO para obtener cabecera y líneas
    const pedidosMap = {};
    parsedRows.forEach((row) => {
      const pedidoId = row.NUMERO_PEDIDO;
      if (!pedidosMap[pedidoId]) {
        pedidosMap[pedidoId] = { pedido: row, lineas: [] };
      }
      pedidosMap[pedidoId].lineas.push(row);
    });

    // Abrir la hoja de cálculo
    const spreadsheet = SpreadsheetApp.openById(SPREADSHEET_ID_LA_FABRICA);

    // Procesar cada pedido y crear la hoja correspondiente
    for (let pedidoId in pedidosMap) {
      const group = pedidosMap[pedidoId];
      const pedido = group.pedido;
      const lineas = group.lineas;

      // Crear nombre de hoja: "NUMERO_PEDIDO - NOMBRE_COMERCIAL_CLIENTE" (limitado a 100 caracteres)
      const nombreHoja = `${pedido.NUMERO_PEDIDO} - ${pedido.NOMBRE_COMERCIAL_CLIENTE.substring(0, 100)}`;

      // Comprobar si la hoja ya existe
      let sheet = spreadsheet.getSheetByName(nombreHoja);

      // Si la hoja existe, verificamos si la fecha de carga es la misma
      if (sheet) {
        const fechaCargaHoja = getFechaSinHora(sheet.getRange("N7").getValue());
        const fechaCargaBase = getFechaSinHora(pedido.FECHA_CARGA);

        // Si la fecha de carga es diferente, actualizamos la hoja
        if (fechaCargaHoja.getTime() !== fechaCargaBase.getTime()) {
          Logger.log(
            `La fecha de carga es diferente para el pedido ${pedido.NUMERO_PEDIDO}. Actualizando...`,
          );
          // Actualizar la fecha de carga en la hoja y los demás datos
          //sheet.getRange('N7').setValue(pedido.FECHA_CARGA);
          let r_fecha_carga = sheet.getRange("N7");
          r_fecha_carga.setValue(new Date(pedido.FECHA_CARGA));
          r_fecha_carga.setNumberFormat("dd/mm/yyyy");
        }

        // Continuar con el siguiente pedido
        continue;
      }

      // Copiar la plantilla y renombrarla
      const originalSheet = spreadsheet.getSheetByName(TEMPLATE_SHEET_NAME);
      const newSheet = originalSheet.copyTo(spreadsheet);
      newSheet.setName(nombreHoja);

      // Asegurarse de que la hoja no esté oculta
      newSheet.showSheet();

      // Rellenar la cabecera con comprobaciones:
      if (pedido.CODIGO_CLIENTE)
        newSheet.getRange("A2").setValue(pedido.CODIGO_CLIENTE); // CODIGO CLIENTE (A2)
      if (pedido.NOMBRE_FISCAL_CLIENTE)
        newSheet.getRange("D2").setValue(pedido.NOMBRE_FISCAL_CLIENTE); // NOMBRE FISCAL CLIENTE (D2)
      if (pedido.NOMBRE_COMERCIAL_CLIENTE)
        newSheet.getRange("D3").setValue(pedido.NOMBRE_COMERCIAL_CLIENTE); // NOMBRE COMERCIAL CLIENTE (D3)
      if (pedido.NOMBRE_COMERCIAL_PEDIDO)
        newSheet.getRange("E4").setValue(pedido.NOMBRE_COMERCIAL_PEDIDO); // NOMBRE DEL COMERCIAL DEL PEDIDO (E4)
      if (pedido.REFERENCIA_PEDIDO)
        newSheet.getRange("E5").setValue(pedido.REFERENCIA_PEDIDO); // REFERENCIA DEL PEDIDO (E5)
      if (pedido.FINCA_CARGA)
        newSheet.getRange("P2").setValue(pedido.FINCA_CARGA); // FINCA DONDE SE CARGA (P2)
      if (pedido.SECTOR_CARGA)
        newSheet.getRange("P4").setValue(pedido.SECTOR_CARGA); // ZONA DONDE SE CARGA (P4)
      if (pedido.NUMERO_PEDIDO)
        newSheet.getRange("L7").setValue(pedido.NUMERO_PEDIDO); // NUMERO DE PEDIDO (L7)
      //if (pedido.FECHA_CARGA) newSheet.getRange('N7').setValue(pedido.FECHA_CARGA); // FECHA DE CARGA DEL PEDIDO (N7)
      // FECHA DE CARGA (N7) - CREACIÓN INICIAL
      if (pedido.FECHA_CARGA) {
        let r_fecha_carga = newSheet.getRange("N7");
        r_fecha_carga.setValue(pedido.FECHA_CARGA);
        r_fecha_carga.setNumberFormat("dd/mm/yyyy"); // Aplicamos formato aquí también
      }
      //if (pedido.FECHA_PEDIDO) newSheet.getRange('R7').setValue(pedido.FECHA_PEDIDO); // FECHA DE CREACION DEL PEDIDO (R7)
      // FECHA DE PEDIDO (R7) con formato visual
      if (pedido.FECHA_PEDIDO) {
        let r_fecha_pedido = newSheet.getRange("R7");
        r_fecha_pedido.setValue(pedido.FECHA_PEDIDO);
        r_fecha_pedido.setNumberFormat("dd/mm/yyyy");
      }
      // Solo se establece la marca si existe y no es nula
      if (pedido.MARCA_PEDIDO)
        newSheet.getRange("A9").setValue("Marca: " + pedido.MARCA_PEDIDO); // MARCA DE LA PLANTA (A9)

      // Rellenar el detalle (líneas) a partir de la fila 13:
      let rowIndex = 13;
      lineas.forEach((linea) => {
        // Número de línea
        if (linea.POSICION_PEDIDO)
          newSheet.getRange(`A${rowIndex}`).setValue(linea.POSICION_PEDIDO); // NUMERO DE LINEA (A13 en adelante)

        // Referencia de la planta
        if (linea.REFERENCIA_ARTICULO)
          newSheet.getRange(`B${rowIndex}`).setValue(linea.REFERENCIA_ARTICULO); // REFERENCIA DE LA PLANTA (B13 en adelante)

        // Equivalente de la planta, si es GGN o no
        if (linea.GLOBALGAP)
          newSheet.getRange(`D${rowIndex}`).setValue(linea.GLOBALGAP); // EQUIVALENTE DE LA PLANTA (D13 en adelante)

        // Talla de la planta
        if (linea.TALLA_PLANTA)
          newSheet.getRange(`M${rowIndex}`).setValue(linea.TALLA_PLANTA);

        // --------------------------------------------------
        // 1. PROCESAR DESCRIPCIÓN
        // --------------------------------------------------

        let datosDescripcion = procesarDescripcion(
          linea.DESCRIPCION_ARTICULO || "",
        );

        // --------------------------------------------------
        // 2. VALORES ORIGINALES DEL CRM
        // --------------------------------------------------

        let fincaArticulo = linea.FINCA_ARTICULO || "";
        let sectorDescripcion = linea.SECTOR_DESCRIPCION || "";

        // Corrección del bug del CRM
        if (fincaArticulo.includes("\n")) {
          fincaArticulo = sectorDescripcion;
          sectorDescripcion = "";
        }

        // --------------------------------------------------
        // 3. SOBRESCRIBIR SI EXISTEN COMANDOS EN DESCRIPCIÓN
        // --------------------------------------------------

        if (datosDescripcion.finca) {
          fincaArticulo = datosDescripcion.finca;
        }

        if (datosDescripcion.sector) {
          sectorDescripcion = datosDescripcion.sector;
        }

        // --------------------------------------------------
        // 4. ESCRIBIR RESULTADO FINAL
        // --------------------------------------------------

        if (sectorDescripcion)
          newSheet.getRange(`O${rowIndex}`).setValue(sectorDescripcion); // SECTOR

        if (fincaArticulo)
          newSheet.getRange(`Q${rowIndex}`).setValue(fincaArticulo); // FINCA

        // --------------------------------------------------
        // 5. DESCRIPCIÓN LIMPIA
        // --------------------------------------------------

        newSheet
          .getRange(`E${rowIndex}`)
          .setValue(datosDescripcion.descripcion);

        // --------------------------------------------------
        // 6. MARCADO
        // --------------------------------------------------

        newSheet.getRange(`V${rowIndex}`).setValue(datosDescripcion.marcado);

        // --------------------------------------------------
        // 7. OBSERVACIONES
        // --------------------------------------------------

        if (datosDescripcion.observaciones.length > 0) {
          newSheet
            .getRange(`X${rowIndex}`)
            .setValue(datosDescripcion.observaciones.join("\n"));
        }

        // Total de la planta en S: cantidad pendiente, formateada como entero con separador de miles.
        if (linea.UNIDADES_PENDIENTES != null) {
          let unidadesPendientes = parseInt(linea.UNIDADES_PENDIENTES);
          newSheet
            .getRange(`S${rowIndex}`)
            .setValue(unidadesPendientes.toLocaleString("es-ES")); // TOTAL DE LA PLANTA (S13 en adelante)
        }
        rowIndex++;
      });

      Logger.log(
        `Creando pedido ${pedido.NUMERO_PEDIDO} - ${pedido.NOMBRE_COMERCIAL_CLIENTE}`,
      );

      // Después de agregar todas las líneas de pedido, eliminamos las filas restantes vacías
      let lastRow = newSheet.getLastRow(); // Obtener la última fila con datos
      if (lastRow > rowIndex - 1) {
        // Si hay filas vacías después de las líneas de pedido, eliminarlas
        newSheet.deleteRows(rowIndex, lastRow - rowIndex + 1);
      }

      // Enviar la notificación con más detalles, incluyendo el enlace
      const enlaceHoja = `https://docs.google.com/spreadsheets/d/${SPREADSHEET_ID_LA_FABRICA}/edit#gid=${newSheet.getSheetId()}`;
      enviarNotificacionPedido(pedido, enlaceHoja);
    }

    // Llamar a la función para limpiar y proteger las hojas
    limpiarYProtegerHojas();

    // Llamamos a Indice para recodificar el indice y reordenar las hojasn de pedidos nuevas
    Indice();
  } catch (e) {
    //Logger.log("Error al ejecutar la consulta BigQuery: " + e.message);
  }
}

/**
 * Función para enviar notificación (por correo y en la hoja de NOTIFICACIONES)
 * @param {Object} pedido - El objeto pedido completo.
 * @param {string} enlaceHoja - El enlace a la hoja del pedido en Google Sheets.
 */
function enviarNotificacionPedido(pedido, enlaceHoja) {
  // Crear el cuerpo del correo con más información del pedido
  let cuerpoCorreo = `<p>Se ha creado un nuevo pedido con número: ${pedido.NUMERO_PEDIDO}</p>`;

  // Verificar si NOMBRE_FISCAL_CLIENTE y NOMBRE_COMERCIAL_CLIENTE son iguales o diferentes
  if (pedido.NOMBRE_FISCAL_CLIENTE && pedido.NOMBRE_COMERCIAL_CLIENTE) {
    if (pedido.NOMBRE_FISCAL_CLIENTE !== pedido.NOMBRE_COMERCIAL_CLIENTE) {
      // Si son diferentes, mostrar ambos
      cuerpoCorreo += `<p><strong>Cliente:</strong> ${pedido.NOMBRE_FISCAL_CLIENTE} - ${pedido.NOMBRE_COMERCIAL_CLIENTE} (${pedido.CODIGO_CLIENTE})</p>`;
    } else {
      // Si son iguales, mostrar solo uno
      cuerpoCorreo += `<p><strong>Cliente:</strong> ${pedido.NOMBRE_FISCAL_CLIENTE} (${pedido.CODIGO_CLIENTE})</p>`;
    }
  } else if (pedido.NOMBRE_FISCAL_CLIENTE) {
    // Si solo existe NOMBRE_FISCAL_CLIENTE
    cuerpoCorreo += `<p><strong>Cliente:</strong> ${pedido.NOMBRE_FISCAL_CLIENTE} (${pedido.CODIGO_CLIENTE})</p>`;
  } else if (pedido.NOMBRE_COMERCIAL_CLIENTE) {
    // Si solo existe NOMBRE_COMERCIAL_CLIENTE
    cuerpoCorreo += `<p><strong>Cliente:</strong> ${pedido.NOMBRE_COMERCIAL_CLIENTE} (${pedido.CODIGO_CLIENTE})</p>`;
  }

  if (pedido.NOMBRE_COMERCIAL_PEDIDO) {
    cuerpoCorreo += `<p><strong>Comercial:</strong> ${pedido.NOMBRE_COMERCIAL_PEDIDO}</p>`;
  }

  if (pedido.FINCA_CARGA) {
    cuerpoCorreo += `<p><strong>Finca de carga:</strong> ${pedido.FINCA_CARGA}</p>`;
  }

  if (pedido.SECTOR_CARGA) {
    cuerpoCorreo += `<p><strong>Sector de carga:</strong> ${pedido.SECTOR_CARGA}</p>`;
  }

  // Formato de la fecha de carga dd/mm/yyyy
  if (pedido.FECHA_CARGA) {
    const fechaCargaFormateada = Utilities.formatDate(
      new Date(pedido.FECHA_CARGA),
      Session.getScriptTimeZone(),
      "dd/MM/yyyy",
    );
    cuerpoCorreo += `<p><strong>Fecha de carga:</strong> ${fechaCargaFormateada}</p>`;
  }

  if (pedido.REFERENCIA_PEDIDO) {
    cuerpoCorreo += `<p><strong>Referencia de pedido:</strong> ${pedido.REFERENCIA_PEDIDO}</p>`;
  }

  // Si no hay marca, agregar "No especificada"
  if (pedido.MARCA_PEDIDO) {
    cuerpoCorreo += `<p><strong>Marca del pedido:</strong> ${pedido.MARCA_PEDIDO}</p>`;
  } else {
    cuerpoCorreo += `<p><strong>Marca del pedido:</strong> No especificada</p>`;
  }

  // Enlace al pedido en Google Sheets
  cuerpoCorreo += `<p><a href="${enlaceHoja}" target="_blank">Ver detalles del pedido en Google Sheets</a></p>`;

  // Enviar notificación por correo electrónico
  MailApp.sendEmail({
    to: "viveroselchesl@gmail.com",
    subject: `Nuevo pedido creado: ${pedido.NUMERO_PEDIDO}`,
    htmlBody: cuerpoCorreo,
  });

  // Agregar notificación en la hoja "NOTIFICACIONES"
  const spreadsheet = SpreadsheetApp.openById(SPREADSHEET_ID_LA_FABRICA);
  const sheet = spreadsheet.getSheetByName("NOTIFICACIONES");
  if (sheet) {
    sheet.appendRow([
      new Date(),
      `Nuevo pedido creado: ${pedido.NUMERO_PEDIDO}`,
    ]);
  }
}

/**
 * Ejecuta una consulta en BigQuery y muestra los primeros 10 registros en los logs.
 */
function consultaBigQuery() {
  var projectId = "dashboard-439511"; // ID del proyecto en Google Cloud.
  var query =
    "SELECT * FROM `dashboard-439511.GestionComercialVE.PEDIDOS` LIMIT 10"; // Consulta SQL para obtener 10 registros de la tabla "PEDIDOS".

  var request = {
    query: query,
    useLegacySql: false, // Indica que se usa SQL estándar, no el SQL heredado de BigQuery.
  };

  // Ejecuta la consulta en BigQuery
  var queryResults = BigQuery.Jobs.query(request, projectId);

  // Extrae las filas obtenidas
  var rows = queryResults.rows;
  if (rows) {
    // Recorre cada fila de los resultados
    for (var i = 0; i < rows.length; i++) {
      var row = rows[i].f; // Accede a los valores de cada fila
      //Logger.log(row);  // Muestra el contenido en los logs de Apps Script
    }
  } else {
    //Logger.log('No se encontraron resultados.');
  }
}

/**
 * Obtiene datos de BigQuery y los inserta en la hoja de cálculo activa.
 */
function getBigQueryData() {
  var projectId = "dashboard-439511"; // ID del proyecto en Google Cloud.
  var datasetId = "GestionComercialVE"; // Nombre del dataset en BigQuery.
  var tableId = "PEDIDOS"; // Nombre de la tabla en BigQuery.

  var request = {
    query:
      "SELECT * FROM `" +
      projectId +
      "." +
      datasetId +
      "." +
      tableId +
      "` LIMIT 10", // Consulta SQL para obtener 10 registros.
    useLegacySql: false, // Usa SQL estándar.
  };

  // Ejecuta la consulta en BigQuery
  var queryResults = BigQuery.Jobs.query(request, projectId);

  // Extrae las filas obtenidas
  var rows = queryResults.rows;
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet(); // Obtiene la hoja activa.

  // Inserta los resultados en la hoja de cálculo
  rows.forEach(function (row, index) {
    var values = row.f.map(function (field) {
      return field.v;
    }); // Convierte cada campo en un valor.
    sheet.appendRow(values); // Agrega una nueva fila con los valores obtenidos.
  });
}
