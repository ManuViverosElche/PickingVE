/**
 * Calendario_Viveros_Elche - Configuración (D-51)
 *
 * Sistema de copias multi-destino: los eventos creados en el calendario "Viveros Elche"
 * se copian, enlazados por el tag `originalEventId`, a los calendarios de cada
 * transportista/zona de carga. Cada evento puede tener copias en VARIOS calendarios a la vez.
 *
 * Reglas de enrutamiento (decididas con el usuario, ver docs/SPECS.md D-51):
 *  - La DIRECCIÓN DE DESCARGA (campo Ubicación del evento) decide la zona de carga:
 *      · La Fábrica  -> calendario del muelle/zona indicado en la DESCRIPCIÓN
 *                      (tokens "MUELLE 1".."MUELLE 8", "NAVE LARGA", "PALETS").
 *      · Borisa      -> calendario Borisa.
 *      · Cualquier otra dirección -> Otras Fincas.
 *  - SIN dirección de descarga, el evento espera al disparador de COLOR:
 *      · Azul arándano (9) -> CARGAS (transportista; descripción bidireccional).
 *      · Rojo tomate (11)  -> Otras Fincas (granate más cercano de la paleta de Google).
 *      · Colores de estado (gris/tomate/mandarina/verde musgo/amarillo/rosa/turquesa/morado)
 *        NO afectan al enrutamiento: solo visualizan el estado de la carga.
 *  - La dirección/muelle MANDA sobre el color: un evento azul con dirección de Fábrica
 *    acaba en CARGAS + el muelle indicado a la vez.
 *  - CARGAS es un anclaje PERMANENTE: una vez copiado, el evento puede cambiar de color
 *    (estado) sin que la copia se mueva ni se borre.
 *  - Las copias de zona se RECALCULAN siempre: cambiar el muelle en la descripción mueve
 *    la copia al calendario nuevo; quitar el muelle la borra.
 *  - Borrar el evento original borra TODAS sus copias (cascada).
 *  - Las copias manuales sin tag no se tocan jamás.
 *
 * Para añadir una zona nueva basta con añadir una línea al array `pares`.
 */

const CALENDARIO_VE = "cbefe7cfdbc4894c5e3d7f9ea3719ba8721fc2d87b6acb51500df7f2db6906a1@group.calendar.google.com";
const ID_CARGAS = "c51530a0dca19a4685ee4b1559bc7ab686f447d9025ffbdd7869c489d2f047ba@group.calendar.google.com";

// Direcciones de descarga deducidas de los eventos reales y confirmadas por el usuario.
// El código las compara ignorando mayúsculas y espacios extra (ver `normalizar`).
const DIR_FABRICA = "Viveros Elche - La Fábrica, CV-845, km 3, 5, 03680 Aspe, Alicante, España";
const DIR_BORISA = "Viveros Elche - Borisa, Partida Borisa pol, 22, 03680 Asp, Alicante, España";

// Disparadores de color (códigos de color de evento de Google Calendar):
//   "9"  = azul arándano -> copia en CARGAS (transportista).
//   "11" = rojo tomate   -> disparador de zona para cargas SIN dirección (Otras Fincas).
// El usuario pidió "granate"; la paleta de Google no lo incluye, "11" es el más cercano
// y coincide con el estado "pedido gestionado" (el gesto de gestionar dispara la copia).
const COLOR_CARGAS = "9";
const COLOR_ASIGNACION_ZONA = "11";

// Ventana de eventos considerada en la reconciliación periódica (meses hacia delante).
const VENTANA_MESES = 12;

// Tags de CalendarApp que mantienen el enlace origen <-> copia:
//   originalEventId          -> ID del evento en Viveros Elche.
//   lastSyncedDescription    -> última descripción común sincronizada (arbitraje CARGAS).
const TAG_ORIGEN = "originalEventId";
const TAG_DESCRIPCION = "lastSyncedDescription";

const NOMBRE_CARGAS = "CARGAS";
const NOMBRE_BORISA = "Borisa";
const NOMBRE_OTRAS_FINCAS = "Otras Fincas";

// Pares origen->destino. Campos por tipo de par:
//   color         dispara copia cuando el evento tiene ese color (CARGAS).
//   permanente    una vez copiada, no se borra por recalculo (solo en cascada).
//   bidireccional la descripción se sincroniza en ambos sentidos (matrículas del camión).
//   token         texto que se busca en la descripción cuando la dirección es La Fábrica.
//   general       destino de cualquier dirección distinta de Fábrica/Borisa
//                 y de las cargas sin dirección con COLOR_ASIGNACION_ZONA.
const pares = [
  { nombre: NOMBRE_CARGAS, destino: ID_CARGAS, color: COLOR_CARGAS, permanente: true, bidireccional: true },
  { nombre: "Muelle 1", destino: "2d439789dc4353393a5b9480a7612c4bd0076bc92ab03039e8e9f58f0fb55324@group.calendar.google.com", token: "MUELLE 1" },
  { nombre: "Muelle 2", destino: "26a4e493f372a5d88b35c4d3be655c286b1e281ded0d3723b7b1d779b23d7dfa@group.calendar.google.com", token: "MUELLE 2" },
  { nombre: "Muelle 3", destino: "44a61c26303e04158befc1caf4d8f96244366586dcc0e72e1b730d646efab9e6@group.calendar.google.com", token: "MUELLE 3" },
  { nombre: "Muelle 4", destino: "eabf93575759c872299a8402cc8710af2b6dcec3deb02375979fb8cba79df69c@group.calendar.google.com", token: "MUELLE 4" },
  { nombre: "Muelle 5", destino: "ea6c7127168a1b216b945f99bf057d4f424dbc56fd9a488211cfd61e14b14763@group.calendar.google.com", token: "MUELLE 5" },
  { nombre: "Muelle 6", destino: "8f9d1960997de545f7193ef88c19a6da7b2ad57c6c5cef90b6c263d2c2999e31@group.calendar.google.com", token: "MUELLE 6" },
  { nombre: "Muelle 7", destino: "944a969ad58d56b6e0bee8c992db25fa787d425c63c5768b62f4d20a3edabc15@group.calendar.google.com", token: "MUELLE 7" },
  { nombre: "Muelle 8", destino: "552e25a7d1dc27e31f57b99873656fc7263860d82512ff6c2dc4c63b0e91966f@group.calendar.google.com", token: "MUELLE 8" },
  { nombre: "Nave Larga", destino: "ba05e4ff1559201b0ce448660245c25d46d432db8052285beb22359a2fd3347b@group.calendar.google.com", token: "NAVE LARGA" },
  { nombre: "Palets", destino: "a6241a235d5562ac1bc58425faf5b1f033b1cc16cb857be3f969d8aac8215acd@group.calendar.google.com", token: "PALETS" },
  { nombre: NOMBRE_BORISA, destino: "e0027ca720e119ca2e31269a201959c4d245207a418049b61a093c18e1af0e81@group.calendar.google.com" },
  { nombre: NOMBRE_OTRAS_FINCAS, destino: "cdd0b57ab568cea6bc3d376f74cef951050a6cf8ac0c4e67cf8c40d577a116ea@group.calendar.google.com", general: true }
];

/**
 * Devuelve el par de config cuyo nombre coincide (o null si no existe).
 * @param {string} nombre Nombre del par (p. ej. "Muelle 3").
 * @returns {Object|null}
 */
function parPorNombre(nombre) {
  for (var i = 0; i < pares.length; i++) {
    if (pares[i].nombre === nombre) return pares[i];
  }
  return null;
}

/**
 * Normaliza texto para comparaciones robustas: a mayúsculas, espacios colapsados y sin bordes.
 * @param {string} texto
 * @returns {string}
 */
function normalizar(texto) {
  return (texto || "").toString().toUpperCase().replace(/\s+/g, " ").trim();
}

/**
 * True si la ubicación normalizada corresponde a la dirección de descarga de La Fábrica.
 * @param {string} loc Ubicación ya normalizada.
 */
function esFabrica(loc) {
  return loc !== "" && loc.indexOf(normalizar(DIR_FABRICA)) !== -1;
}

/**
 * True si la ubicación normalizada corresponde a la dirección de descarga de Borisa.
 * @param {string} loc Ubicación ya normalizada.
 */
function esBorisa(loc) {
  return loc !== "" && loc.indexOf(normalizar(DIR_BORISA)) !== -1;
}
