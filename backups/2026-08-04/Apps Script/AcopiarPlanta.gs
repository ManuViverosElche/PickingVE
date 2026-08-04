/**
 * Nombre del archivo "AcopiarPlanta.gs"
 */
/*******************************
 * Función principal para botón
 * Acopia todas las líneas de la hoja activa
 * a los libros correspondientes según operario
 *******************************/
function AcopiarHojaActivaPorOperarios() {
  const hoja = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
  const ultimaFila = hoja.getLastRow();

  // Recorre todas las filas de pedido (fila 13 en adelante)
  for (let fila = 13; fila <= ultimaFila; fila++) {
    const operario = hoja.getRange("W" + fila).getValue();
    if (!operario || !OPERARIOS_IDS[operario]) continue;

    const idLibro = OPERARIOS_IDS[operario];
    const libroOperario = SpreadsheetApp.openById(idLibro);

    const fechaPedido = hoja.getRange("N7").getValue();
    if (!fechaPedido) continue;

    const fecha = Utilities.formatDate(
      new Date(fechaPedido),
      Session.getScriptTimeZone(),
      "dd/MM/yyyy",
    );

    // Crear hoja de fecha si no existe
    let hojaDestino = libroOperario.getSheetByName(fecha);
    if (!hojaDestino) {
      hojaDestino = libroOperario.insertSheet(fecha);
      SetearHoja(hojaDestino);
    }

    // Copiar la línea usando appendRow para no fallar
    const valores = hoja.getRange("C" + fila + ":G" + fila).getValues()[0];
    hojaDestino.appendRow(valores);
  }
}

/**
 * Función principal para acopiar todos los pedidos de diferentes fuentes.
 * Esta función actúa como un contenedor que invoca las funciones individuales
 * para copiar los pedidos desde distintas fuentes, como son:
 *
 * - Pedidos de Jesus
 * - Pedidos de Cheik
 * - Pedidos de Angelillo
 * - Pedidos de Antonio
 * - Pedidos de Bara
 * - Pedidos de Diao
 * - Pedidos de Carlos
 * - Pedidos de Fede
 * - Pedidos de "Don Angel"
 *
 * Cada función encargada de copiar los pedidos realiza tareas específicas de
 * procesamiento y acopio de datos. La ejecución de estas funciones se realiza
 * en secuencia, asegurando que cada fuente de datos sea procesada en el orden
 * en que se definen.
 */
function AcopiarTodosLosPedidos() {
  // Copiar pedidos de Jesus, Cheick
  AcopiarPlantaGrandeFuera();

  // Copiar pedidos del Bara, Antonio e Angelillo
  AcopiarPlantaPequenyaFuera();

  // Copiar pedidos de la Fábrica
  AcopiarPlantaLaFabrica();
}

/**
 * Función principal para acopiar todos los pedidos de diferentes fuentes que
 * lleven planta grande de fuera.
 * Esta función actúa como un contenedor que invoca las funciones individuales
 * para copiar los pedidos desde distintas fuentes, como son:
 *
 * - Pedidos de Jesus
 * - Pedidos de Cheik
 * - Pedidos de "Don Angel"
 *
 * Cada función encargada de copiar los pedidos realiza tareas específicas de
 * procesamiento y acopio de datos. La ejecución de estas funciones se realiza
 * en secuencia, asegurando que cada fuente de datos sea procesada en el orden
 * en que se definen.
 */
function AcopiarPlantaGrandeFuera() {
  // Copiar pedidos de Jesus
  AcopiarPedidosJesus();

  // Copiar pedidos de Cheik
  AcopiarPedidosCheik();
}

/**
 * Función principal para acopiar todos los pedidos de diferentes fuentes que
 * lleven planta pequeña de fuera.
 * Esta función actúa como un contenedor que invoca las funciones individuales
 * para copiar los pedidos desde distintas fuentes, como son:
 *
 * - Pedidos de Angelillo
 * - Pedidos de Bara
 * - Pedidos de Antonio
 *
 * Cada función encargada de copiar los pedidos realiza tareas específicas de
 * procesamiento y acopio de datos. La ejecución de estas funciones se realiza
 * en secuencia, asegurando que cada fuente de datos sea procesada en el orden
 * en que se definen.
 */
function AcopiarPlantaPequenyaFuera() {
  // Copiar pedidos de Angelillo
  AcopiarPedidosAngelillo();

  // Copiar pedidos de Bara
  AcopiarPedidosBara();

  // Se repite la función para Antonio
  AcopiarPedidosAntonio();
}

/**
 * Función principal para acopiar todos los pedidos de diferentes fuentes que
 * lleven planta de La Fábrica.
 * Esta función actúa como un contenedor que invoca las funciones individuales
 * para copiar los pedidos desde distintas fuentes, como son:
 *
 * - Pedidos de Diao
 * - Pedidos de Carlos
 * - Pedidos de Fede
 *
 * Cada función encargada de copiar los pedidos realiza tareas específicas de
 * procesamiento y acopio de datos. La ejecución de estas funciones se realiza
 * en secuencia, asegurando que cada fuente de datos sea procesada en el orden
 * en que se definen.
 */
function AcopiarPlantaLaFabrica() {
  // Copiar pedidos de Diao
  AcopiarPedidosDiao();
  // Copiar pedidos de Carlos
  AcopiarPedidosCarlos();
  // Copiar pedidos de Fede
  AcopiarPedidosFede();
}

/**
 * 📌 Función principal para acopiar pedidos en el libro de "Pedidos".
 * Esta función recorre todas las hojas del libro de pedidos activo, verifica cuáles
 * contienen pedidos válidos y los copia al archivo de acopio correspondiente según
 * la fecha del pedido. Si la hoja para una fecha ya existe en el archivo de acopio,
 * se actualizan los datos, si no, se crea una nueva hoja y se copian los datos.
 *
 * @param {string} nombre - Nombre de la maquinaria que se debe considerar al copiar pedidos.
 * @param {Spreadsheet} LibroPedidos - El libro de trabajo de destino donde se acopian los pedidos.
 * @param {string} maquinaria - Identificador de la maquinaria para filtrar pedidos específicos.
 */
function AcopiarPedidos(nombre, LibroPedidos, maquinaria) {
  // 📄 Obtener todas las hojas del libro de pedidos activo
  var hojas = SpreadsheetApp.getActiveSpreadsheet().getSheets();

  // 📄 Obtener todas las hojas del libro de acopio
  var HojasPedidos = LibroPedidos.getSheets();

  // 🗂️ Obtener la hoja "Indice" dentro del libro de acopio
  var HojaIndice = LibroPedidos.getSheetByName("Indice");

  // 🔄 Recorremos todas las hojas de pedidos para determinar cuáles deben acopiarse
  hojas.forEach(function (hoja) {
    // ✅ Verificamos que la hoja no sea una hoja especial y que no esté oculta
    if (
      hoja.getName() != "FABRICA" &&
      hoja.getName() != "INDICE" &&
      hoja.getName() != "ORIGINAL" &&
      !hoja.isSheetHidden() &&
      existeMaquinaria(hoja, maquinaria)
    ) {
      // 📅 Obtener la fecha de carga del pedido (ubicada en la celda N7)
      var fechaPedido = hoja.getRange("N7").getValue();

      // ✅ Formateamos la fecha en el formato dd/MM/yyyy
      var fechaFormateada = Utilities.formatDate(
        new Date(fechaPedido),
        Session.getScriptTimeZone(),
        "dd/MM/yyyy",
      );

      // 🔍 Buscar si ya existe una hoja con esa fecha en el libro de acopio
      var hojaPedido = LibroPedidos.getSheetByName(fechaFormateada);

      // 📌 Si la hoja con esa fecha ya existe, verificamos si el pedido ya está copiado
      if (hojaPedido) {
        if (!existePedidoEnHoja(hojaPedido, hoja.getRange("L7").getValue())) {
          AcopiarPedido(HojaIndice, hoja, hojaPedido, maquinaria);
        } else {
          ActualizarPedido(hoja, hojaPedido, maquinaria);
        }
      }
      // 📌 Si la hoja no existe, la creamos y configuramos su formato
      else {
        hojaPedido = LibroPedidos.insertSheet(fechaFormateada); // 🆕 Crear nueva hoja
        SetearHoja(hojaPedido); // 🎨 Ajustar formato inicial
        aplicarReglasFormatoCondicional(hojaPedido); // ✅ Aplicar formato condicional
        AcopiarPedido(HojaIndice, hoja, hojaPedido, maquinaria); // 🔄 Copiar el pedido
      }
    }
  });

  // 🧹 Eliminar filas vacías en cada hoja del libro de pedidos tras el acopio
  eliminarFilasVaciasPorDia(LibroPedidos);

  // Ordenamos las hojas por fecha manteniendo "Indice" como primera hoja del libro
  ordenarHojasPorFecha(LibroPedidos);
}

/**
 * Ordena las hojas de un libro según la fecha en su nombre, manteniendo "Indice" en la primera posición.
 *
 * @param {GoogleAppsScript.Spreadsheet.Spreadsheet} libro - El libro de Google Sheets que se desea ordenar.
 */
function ordenarHojasPorFecha(libro) {
  var hojas = libro.getSheets(); // 1️⃣ Obtiene el objeto de la hoja de cálculo activa

  // Depuración: Ver qué hojas existen
  //Logger.log("Hojas disponibles: " + hojas.map(h => h.getName()).join(", "));

  // Separar la hoja "Indice" del resto
  var hojaIndice = hojas.find((hoja) => hoja.getName() === "Indice");

  // Depuración: Ver si encontró la hoja "Indice"
  //Logger.log("Hoja 'Indice' encontrada: " + (hojaIndice ? "Sí" : "No"));

  var hojasRestantes = hojas.filter((hoja) => hoja.getName() !== "Indice");
  // Depuración: Ver qué hojas son ordenables
  //Logger.log("Hojas ordenables: " + hojasRestantes.map(h => h.getName()).join(", "));

  // Ordenar las hojas restantes por fecha
  hojasRestantes.sort((a, b) => {
    var fechaA = extraerFechaDeNombre(a.getName());
    var fechaB = extraerFechaDeNombre(b.getName());
    return fechaA - fechaB;
  });
  // Depuración: Ver qué hojas son ordenables ordenadas
  //Logger.log("Hojas ordenables ordenadas: " + hojasRestantes.map(h => h.getName()).join(", "));

  // Reordenar las hojas en el libro
  var posicion = 1; // Inicializamos la posición para la hoja "Indice"

  if (hojaIndice) {
    // Si existe una hoja "Indice" en el libro
    hojaIndice.activate(); // Activamos la hoja "Indice"
    libro.moveActiveSheet(posicion); // Movemos la hoja "Indice" a la posición 1, la primera del libro
    posicion++; // Avanzamos a la siguiente posición para reordenar el resto de las hojas del libro
  }

  hojasRestantes.forEach(function (item) {
    item.activate(); // Activamos la hoja antes de moverla
    libro.moveActiveSheet(posicion); // Movemos la hoja a la posición siguiente
    posicion++; // Avanzamos a la siguiente posición para la siguiente hoja
  });

  // Aplicar los cambios realizados
  SpreadsheetApp.flush(); // Aseguramos que todos los cambios en las hojas se apliquen inmediatamente
}

/**
 * Extrae una fecha de un nombre de hoja en formato "YYYY-MM-DD".
 * Si el nombre no tiene una fecha válida, devuelve una fecha muy antigua.
 *
 * @param {string} nombreHoja - Nombre de la hoja
 * @returns {Date} Fecha extraída o una fecha antigua en caso de error
 */
function extraerFechaDeNombre(nombreHoja) {
  var match = nombreHoja.match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/);
  if (match) {
    return new Date(
      parseInt(match[3]),
      parseInt(match[2]) - 1,
      parseInt(match[1]),
    );
  }
  return new Date(1900, 0, 1); // Fecha antigua si no es válida
}

/**
 * 📌 Función principal para acopiar pedidos en el libro de "Pedidos - Jesús".
 * Recorre todas las hojas del libro de pedidos, verifica cuáles contienen pedidos válidos
 * y los copia en el archivo de acopio correspondiente según la fecha del pedido.
 */
function AcopiarPedidosJesus() {
  // 🏷️ Nombre del libro donde se acopian los pedidos
  const nombre = "Pedidos - Jesús";

  // 📚 Abrir el libro donde se guardarán los pedidos acopiados
  var LibroPedidos = SpreadsheetApp.openById(SPREADSHEET_ID_JESUS);

  // 🚜 Tipo de maquinaria asociada a los pedidos a acopiar
  var maquinaria = "JESÚS";

  // 🗑️ Revisamos el Indice para elimnar información de acopio de planta de fechas pasadas
  eliminarFechasPasadas(LibroPedidos);

  // 📦 Acopiamos los pedidos en el libro correspondiente
  AcopiarPedidos(nombre, LibroPedidos, maquinaria);
}

/**
 * 📌 Función principal para acopiar pedidos en el libro de "Pedidos - Cheik".
 * Recorre todas las hojas del libro de pedidos, verifica cuáles contienen pedidos válidos
 * y los copia en el archivo de acopio correspondiente según la fecha del pedido.
 */
function AcopiarPedidosCheik() {
  // 🏷️ Nombre del libro donde se acopian los pedidos
  const nombre = "Pedidos - Cheik";

  // 📚 Abrir el libro donde se guardarán los pedidos acopiados
  var LibroPedidos = SpreadsheetApp.openById(SPREADSHEET_ID_CHEIK);

  // 🚜 Tipo de maquinaria asociada a los pedidos a acopiar
  var maquinaria = "CHEIK";

  AcopiarPedidos(nombre, LibroPedidos, maquinaria);
}

/**
 * 📌 Función principal para acopiar pedidos en el libro de "Pedidos - Diao".
 * Recorre todas las hojas del libro de pedidos, verifica cuáles contienen pedidos válidos
 * y los copia en el archivo de acopio correspondiente según la fecha del pedido.
 */
function AcopiarPedidosDiao() {
  // 🏷️ Nombre del libro donde se acopian los pedidos
  const nombre = "Pedidos - Diao";

  // 📚 Abrir el libro donde se guardarán los pedidos acopiados
  var LibroPedidos = SpreadsheetApp.openById(SPREADSHEET_ID_DIAO);

  // 🚜 Tipo de maquinaria asociada a los pedidos a acopiar
  var maquinaria = "DIAO";

  AcopiarPedidos(nombre, LibroPedidos, maquinaria);
}

/**
 * 📌 Función principal para acopiar pedidos en el libro de "Pedidos - Carlos".
 * Recorre todas las hojas del libro de pedidos, verifica cuáles contienen pedidos válidos
 * y los copia en el archivo de acopio correspondiente según la fecha del pedido.
 */
function AcopiarPedidosCarlos() {
  // 🏷️ Nombre del libro donde se acopian los pedidos
  const nombre = "Pedidos - Carlos";

  // 📚 Abrir el libro donde se guardarán los pedidos acopiados
  var LibroPedidos = SpreadsheetApp.openById(SPREADSHEET_ID_CARLOS);

  // 🚜 Tipo de maquinaria asociada a los pedidos a acopiar
  var maquinaria = "CARLOS";

  AcopiarPedidos(nombre, LibroPedidos, maquinaria);
}

/**
 * 📌 Función principal para acopiar pedidos en el libro de "Pedidos - Fede".
 * Recorre todas las hojas del libro de pedidos, verifica cuáles contienen pedidos válidos
 * y los copia en el archivo de acopio correspondiente según la fecha del pedido.
 */
function AcopiarPedidosFede() {
  // 🏷️ Nombre del libro donde se acopian los pedidos
  const nombre = "Pedidos - Fede";

  // 📚 Abrir el libro donde se guardarán los pedidos acopiados
  var LibroPedidos = SpreadsheetApp.openById(SPREADSHEET_ID_FEDE);

  // 🚜 Tipo de maquinaria asociada a los pedidos a acopiar
  var maquinaria = "FEDE";

  AcopiarPedidos(nombre, LibroPedidos, maquinaria);
}

/**
 * 📌 Función principal para acopiar pedidos en el libro de "Pedidos - Antonio".
 * Recorre todas las hojas del libro de pedidos, verifica cuáles contienen pedidos válidos
 * y los copia en el archivo de acopio correspondiente según la fecha del pedido.
 */
function AcopiarPedidosAntonio() {
  // 🏷️ Nombre del libro donde se acopian los pedidos
  const nombre = "Pedidos - Antonio";

  // 📚 Abrir el libro donde se guardarán los pedidos acopiados
  var LibroPedidos = SpreadsheetApp.openById(SPREADSHEET_ID_ANTONIO);

  // 🚜 Tipo de maquinaria asociada a los pedidos a acopiar
  var maquinaria = "ANTONIO";

  AcopiarPedidos(nombre, LibroPedidos, maquinaria);
}

/**
 * 📌 Función principal para acopiar pedidos en el libro de "Pedidos - Bara".
 * Recorre todas las hojas del libro de pedidos, verifica cuáles contienen pedidos válidos
 * y los copia en el archivo de acopio correspondiente según la fecha del pedido.
 */
function AcopiarPedidosBara() {
  // 🏷️ Nombre del libro donde se acopian los pedidos
  const nombre = "Pedidos - Bara";

  // 📚 Abrir el libro donde se guardarán los pedidos acopiados
  var LibroPedidos = SpreadsheetApp.openById(SPREADSHEET_ID_BARA);

  // 🚜 Tipo de maquinaria asociada a los pedidos a acopiar
  var maquinaria = "BARA";

  AcopiarPedidos(nombre, LibroPedidos, maquinaria);
}

/**
 * 📌 Función principal para acopiar pedidos en el libro de "Pedidos - Angelillo".
 * Recorre todas las hojas del libro de pedidos, verifica cuáles contienen pedidos válidos
 * y los copia en el archivo de acopio correspondiente según la fecha del pedido.
 */
function AcopiarPedidosAngelillo() {
  // 🏷️ Nombre del libro donde se acopian los pedidos
  const nombre = "Pedidos - Angelillo";

  // 📚 Abrir el libro donde se guardarán los pedidos acopiados
  var LibroPedidos = SpreadsheetApp.openById(SPREADSHEET_ID_ANGELILLO);

  // 🚜 Tipo de maquinaria asociada a los pedidos a acopiar
  var maquinaria = "ANGELILLO";

  AcopiarPedidos(nombre, LibroPedidos, maquinaria);
}
