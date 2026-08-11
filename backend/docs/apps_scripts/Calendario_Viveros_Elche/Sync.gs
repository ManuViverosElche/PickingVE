/**
 * Calendario_Viveros_Elche - Reconciliación (D-51, D-52, D-53, D-54, D-59, D-61)
 *
 * `syncAll` es la fuente de verdad del sistema y su ÚNICA lógica de negocio:
 *  - La ejecuta el trigger de calendario en cada cambio (tiempo real, D-53).
 *  - La ejecuta el trigger de tiempo cada 10 min (red de seguridad, D-53).
 *
 * Fase 1 - CREAR / SINCRONIZAR: por cada evento origen se calculan los destinos que le
 *          corresponden y se crean las copias que faltan o se actualizan las existentes.
 * Fase 2 - BORRAR: se eliminan las copias cuyo evento original ya no existe (cascada) o
 *          que ya no corresponden a su par (muelle cambiado, dirección quitada...),
 *          respetando el anclaje permanente de CARGAS y las copias manuales sin tag.
 *
 * Un único pase O(n) con mapas en memoria: una lectura por calendario, sin
 * getEventById dentro de bucles. El enlace origen <-> copia es el propio tag
 * originalEventId, que se lee de cada calendario en cada pase (sin estado extra).
 *
 * D-54 (06/08/2026): un evento de prueba con dirección + muelle generó DOS copias en
 * el mismo calendario (creadas con 3 s de diferencia): dos ejecuciones de syncAll
 * disparadas por el mismo trigger corrieron CONCURRENTEMENTE, ambas leyeron "no hay
 * copia" y ambas la crearon (carrera read-modify-write). Para impedirlo, syncAll se
 * serializa con LockService: la segunda ejecución espera a que la primera termine
 * (o se omite si el lock no se obtiene en 30 s) y entonces ya ve la copia creada.
 *
 * D-56 (06/08/2026): cascada de borrado rota — el trigger onEventUpdated SÍ se
 * disparaba al borrar un evento y la red de seguridad de 10 min también corría, pero
 * la copia nunca se borraba. Causa: Google Calendar NO elimina los eventos al
 * borrarlos en la UI (van a la papelera ~30 días, status "cancelled") y getEventById
 * los sigue devolviendo; por tanto `!!calOrigen.getEventById(tagSrc)` era true para
 * siempre y la fase 2 nunca borraba. Fix: el evento debe existir Y NO estar borrado
 * (isDeleted()): getEvents excluye los cancelados, pero getEventById no, y
 * isDeleted() distingue el borrado real de la papelera.
 *
 * D-59 (06/08/2026): causa raíz DEFINITIVA de la cascada, confirmada con un
 * diagnóstico real sobre un evento borrado: `getEventById` con un evento borrado
 * LANZA la excepción "Invalid argument: iCalId" (no devuelve null ni el evento
 * cancelado). Los catches conservadores de D-53/D-56/D-58 (existe=true ante
 * cualquier error) convertían esa excepción en "origen vivo" para siempre: por eso
 * ningún pase borraba. Ahora el catch distingue el error: "Invalid argument" =
 * borrado; cualquier otro error = se conserva la copia. La API REST quedó
 * descartada como árbitro (403 por scopes de UrlFetchApp). Los eventos de la
 * ventana se siguen excluyendo solos de getEvents (los cancelados no aparecen);
 * isDeleted() no existe en CalendarEvent (solo en CalendarEventSeries).
 *
 * D-61 (06/08/2026): latencia del borrado — pruebas reales: un evento borrado siguió
 * siendo devuelto por getEventById 37 min después de la eliminación (y a los 46 min
 * ya lanzaba "Invalid argument"), mientras que la API REST devuelve status
 * "cancelled" DESDE EL INSTANTE del borrado. La cascada ahora usa un veredicto en
 * dos fases: (1) API REST de Calendar (instantáneo: 404 o status "cancelled" =
 * borrado); (2) si REST no responde 200/404 (403, 5xx...) se usa el fallback
 * getEventById convergente, dejando el cuerpo de la respuesta REST en el log para
 * diagnosticar la causa del 403.
 */

let contadorEscrituras = 0;

/**
 * Reconciliación completa de todos los pares configurados, serializada con un lock
 * para que dos ejecuciones concurrentes (trigger + trigger, o trigger + temporizador)
 * no dupliquen copias ni se pisen entre sí (D-54).
 * Idempotente: puede ejecutarse tantas veces como se quiera sin efectos colaterales.
 */
function syncAll() {
  const lock = LockService.getScriptLock();
  try {
    lock.waitLock(30000);
  } catch (e) {
    Logger.log("syncAll: otro sync en curso, se omite esta ejecución");
    return;
  }

  try {
    const inicio = new Date().getTime();
    const ahora = new Date();
    const fin = new Date();
    fin.setMonth(ahora.getMonth() + VENTANA_MESES);

    const calOrigen = CalendarApp.getCalendarById(CALENDARIO_VE);
    if (!calOrigen) {
      Logger.log("syncAll: calendario origen no accesible");
      return;
    }

    // Lectura única de la ventana en origen y en cada destino; se indexan por ID.
    // Nota D-58: getEvents excluye los eventos cancelados; no se filtra con isDeleted
    // porque ese método no existe en CalendarEvent (solo en CalendarEventSeries).
    const eventosOrigen = calOrigen.getEvents(ahora, fin);
    const sourceById = {};
    for (const ev of eventosOrigen) sourceById[ev.getId()] = ev;

    // mapasCopias[par][tagOrigen] = copia  -> solo copias enlazadas (las manuales sin tag se ignoran).
    const mapasCopias = {};
    for (const par of pares) {
      mapasCopias[par.nombre] = {};
      try {
        const cal = CalendarApp.getCalendarById(par.destino);
        if (!cal) continue;
        for (const ev of cal.getEvents(ahora, fin)) {
          const tag = ev.getTag(TAG_ORIGEN);
          if (tag) mapasCopias[par.nombre][tag] = ev;
        }
      } catch (e) {
        Logger.log(`syncAll: error leyendo par ${par.nombre}: ${e.message}`);
      }
    }

    contadorEscrituras = 0;

    // -------- Fase 1: crear o sincronizar las copias esperadas --------
    for (const src of eventosOrigen) {
      const destinos = calcularDestinos(src);
      // Anclaje permanente de CARGAS: si ya existe copia, se mantiene aunque el color
      // haya cambiado al de estado (decisión de producto confirmada por el usuario).
      if (mapasCopias[NOMBRE_CARGAS][src.getId()]) destinos.push(NOMBRE_CARGAS);
      for (const nombrePar of destinos) {
        const par = parPorNombre(nombrePar);
        if (!par) continue;
        const copia = mapasCopias[par.nombre][src.getId()];
        try {
          if (!copia) crearCopia(src, par);
          else sincronizarCopia(src, copia, par);
        } catch (e) {
          Logger.log(`syncAll: error en ${par.nombre}: ${e.message}`);
        }
      }
    }

    // -------- Fase 2: borrar copias huérfanas o que ya no corresponden --------
    for (const par of pares) {
      const mapa = mapasCopias[par.nombre];
      for (const tagSrc in mapa) {
        if (!mapa.hasOwnProperty(tagSrc)) continue;
        const copia = mapa[tagSrc];
        const src = sourceById[tagSrc];
        if (!src) {
          // El origen no está en la ventana: puede que el evento se haya BORRADO o que
          // esté fuera de los 12 meses. Veredicto en dos fases (D-61):
          //  1) API REST de Calendar: un borrado responde 404 o status "cancelled"
          //     DESDE EL INSTANTE de la eliminación (verificado en pruebas reales).
          //  2) Si REST no responde 200/404 (403, 5xx...), fallback getEventById:
          //     con un evento borrado lanza "Invalid argument: iCalId" (converge
          //     ~40 min tras el borrado). Nunca se borra por un error transitorio.
          if (!/@google\.com$/.test(tagSrc)) {
            Logger.log(`Cascada (${par.nombre}): tag ${tagSrc} no es un ID de evento válido -> se conserva la copia`);
            continue;
          }
          let borrado = false;
          let via = "getEventById";
          try {
            borrado = eventoBorradoPorREST(tagSrc);
            via = "REST";
          } catch (e) {
            Logger.log(`Cascada (${par.nombre}): REST no disponible para ${tagSrc}: ${e.message}`);
            try {
              const origen = calOrigen.getEventById(tagSrc);
              borrado = !origen;
            } catch (e2) {
              borrado = String(e2.message).indexOf("Invalid argument") !== -1;
            }
          }
          if (borrado) {
            try {
              copia.deleteEvent();
              contadorEscrituras++;
              Logger.log(`Cascada (${par.nombre}): copia borrada (origen borrado: ${tagSrc}, veredicto ${via})`);
            } catch (e) {
              Logger.log(`syncAll: no se pudo borrar copia en ${par.nombre}: ${e.message}`);
            }
          } else {
            Logger.log(`Cascada (${par.nombre}): origen ${tagSrc} confirmado vivo (${via}) -> se conserva la copia`);
          }
          continue;
        }
        // CARGAS es permanente: solo cascada, nunca por recalculo (decisión confirmada).
        if (par.permanente) continue;
        // El evento existe pero ya no corresponde a este par (cambio de muelle/dirección):
        // la copia se borra; la fase 1 ya habrá creado la nueva en el par correcto (mover).
        if (calcularDestinos(src).indexOf(par.nombre) === -1) {
          try {
            copia.deleteEvent();
            contadorEscrituras++;
          } catch (e) {
            Logger.log(`syncAll: no se pudo borrar copia en ${par.nombre}: ${e.message}`);
          }
        }
      }
    }

    Logger.log(`syncAll completado en ${((new Date().getTime() - inicio) / 1000).toFixed(1)}s, ${contadorEscrituras} escrituras`);
  } finally {
    lock.releaseLock();
  }
}

/**
 * Calcula los destinos a los que pertenece un evento según las reglas de D-51:
 *  - Color azul arándano -> CARGAS (puede coexistir con la zona).
 *  - Dirección de La Fábrica -> par cuyo token aparezca en la descripción
 *    (si el muelle no se reconoce, no hay zona hasta que se escriba).
 *  - Dirección de Borisa -> Borisa.
 *  - Cualquier otra dirección -> Otras Fincas.
 *  - Sin dirección + color de asignación de zona -> Otras Fincas.
 * La dirección manda sobre el color. Los colores de estado no enrutan.
 * @param {GoogleAppsScript.Calendar.CalendarEvent} evento
 * @returns {string[]} Nombres de par destino.
 */
function calcularDestinos(evento) {
  const destinos = [];
  const color = evento.getColor();
  const loc = normalizar(evento.getLocation());
  const desc = normalizar(evento.getDescription());

  if (color === COLOR_CARGAS) destinos.push(NOMBRE_CARGAS);

  if (esFabrica(loc)) {
    for (const par of pares) {
      if (par.token && desc.indexOf(par.token) !== -1) {
        destinos.push(par.nombre);
        break;
      }
    }
  } else if (esBorisa(loc)) {
    destinos.push(NOMBRE_BORISA);
  } else if (loc !== "") {
    destinos.push(NOMBRE_OTRAS_FINCAS);
  } else if (color === COLOR_ASIGNACION_ZONA) {
    destinos.push(NOMBRE_OTRAS_FINCAS);
  }
  return destinos;
}

/**
 * Crea la copia de un evento en el calendario destino y la enlaza con los tags
 * originalEventId y (si el par es bidireccional) lastSyncedDescription.
 * @param {GoogleAppsScript.Calendar.CalendarEvent} src
 * @param {Object} par
 */
function crearCopia(src, par) {
  const cal = CalendarApp.getCalendarById(par.destino);
  const nueva = cal.createEvent(
    src.getTitle(),
    src.getStartTime(),
    src.getEndTime(),
    { location: src.getLocation() || "", description: src.getDescription() || "" }
  );
  nueva.setTag(TAG_ORIGEN, src.getId());
  if (par.bidireccional) nueva.setTag(TAG_DESCRIPCION, src.getDescription() || "");
  registrarEscritura();
}

/**
 * Sincroniza una copia con su origen. Los campos título/hora/ubicación SIEMPRE van
 * del origen a la copia (unidireccional). La descripción sigue la regla del par:
 * unidireccional en zonas, bidireccional con arbitraje solo en CARGAS.
 * Solo escribe cuando hay diferencias reales (evita ruido y bucles de triggers).
 * @param {GoogleAppsScript.Calendar.CalendarEvent} src
 * @param {GoogleAppsScript.Calendar.CalendarEvent} copia
 * @param {Object} par
 */
function sincronizarCopia(src, copia, par) {
  if (copia.getTitle() !== src.getTitle()) {
    copia.setTitle(src.getTitle());
    registrarEscritura();
  }
  const si = src.getStartTime();
  const ei = src.getEndTime();
  if (copia.getStartTime().getTime() !== si.getTime() || copia.getEndTime().getTime() !== ei.getTime()) {
    copia.setTime(si, ei);
    registrarEscritura();
  }
  if ((copia.getLocation() || "") !== (src.getLocation() || "")) {
    copia.setLocation(src.getLocation() || "");
    registrarEscritura();
  }
  if (par.bidireccional) {
    sincronizarDescripcion(src, copia);
  } else if ((copia.getDescription() || "") !== (src.getDescription() || "")) {
    copia.setDescription(src.getDescription() || "");
    registrarEscritura();
  }
  if (!copia.getTag(TAG_ORIGEN)) copia.setTag(TAG_ORIGEN, src.getId());
}

/**
 * Arbitraje bidireccional de la descripción (SOLO CARGAS): el transportista puede
 * añadir matrículas o notas del camión y ese cambio debe propagarse a Viveros Elche.
 * El tag lastSyncedDescription guarda la última versión común (T); reglas:
 *  - T == origen y T != copia   -> editó la copia (transportista): gana la copia.
 *  - T == copia y T != origen   -> editó el origen: gana el origen.
 *  - T != origen y T != copia   -> ambos editaron sin sincronizar: gana el ORIGEN.
 *  - T == origen == copia       -> sin cambios.
 * Al finalizar se actualiza T para que el siguiente trigger no realimente.
 * @param {GoogleAppsScript.Calendar.CalendarEvent} src
 * @param {GoogleAppsScript.Calendar.CalendarEvent} copia
 */
function sincronizarDescripcion(src, copia) {
  const o = src.getDescription() || "";
  const d = copia.getDescription() || "";
  const t = copia.getTag(TAG_DESCRIPCION) || "";
  let nuevoComun = t;
  if (t === o && t !== d) {
    if (src.getDescription() !== d) {
      src.setDescription(d);
      registrarEscritura();
    }
    nuevoComun = d;
  } else if (t === d && t !== o) {
    if (copia.getDescription() !== o) {
      copia.setDescription(o);
      registrarEscritura();
    }
    nuevoComun = o;
  } else if (t !== o && t !== d) {
    if (copia.getDescription() !== o) {
      copia.setDescription(o);
      registrarEscritura();
    }
    nuevoComun = o;
  }
  if (nuevoComun !== t) {
    copia.setTag(TAG_DESCRIPCION, nuevoComun);
    registrarEscritura();
  }
}

/**
 * Veredicto de borrado por API REST de Calendar (D-61). Un evento borrado responde
 * 404 (purga) o HTTP 200 con status "cancelled" (papelera ~30 días) DESDE EL INSTANTE
 * de la eliminación, al contrario que getEventById que sigue devolviéndolo ~40 min.
 * Cualquier otra respuesta (403, 5xx...) lanza para que la cascada use el fallback
 * getEventById; el cuerpo de la respuesta queda en el log para diagnosticar el
 * problema de autorización.
 * @param {string} eventId ID del evento en el calendario origen.
 * @returns {boolean} true si el evento está borrado (404 o status "cancelled").
 * @throws {Error} si la API REST no es usable (HTTP distinto de 200/404).
 */
function eventoBorradoPorREST(eventId) {
  const url = "https://www.googleapis.com/calendar/v3/calendars/"
    + encodeURIComponent(CALENDARIO_VE)
    + "/events/" + encodeURIComponent(eventId);
  const res = UrlFetchApp.fetch(url, {
    headers: { Authorization: "Bearer " + ScriptApp.getOAuthToken() },
    muteHttpExceptions: true
  });
  const code = res.getResponseCode();
  if (code === 404) return true;
  if (code === 200) {
    const body = JSON.parse(res.getContentText());
    if (body.status === "cancelled") return true;
    return false;
  }
  Logger.log(`Cascada REST: HTTP ${code} para ${eventId}: ${res.getContentText().substring(0, 200)}`);
  throw new Error("REST no disponible (HTTP " + code + ")");
}

/**
 * DIAGNÓSTICO (temporal, se puede eliminar cuando se cierre el problema): muestra
 * cómo ve la API un evento origen concreto, para validar la cascada de borrado sin
 * tocar el flujo normal.
 * @param {string} eventId ID del evento a comprobar (p. ej. "1bssbj0h0uf2snjgnij67vas81@google.com").
 */
function diagnosticoEvento(eventId) {
  const cal = CalendarApp.getCalendarById(CALENDARIO_VE);
  let viaId = "null";
  try {
    const ev = cal.getEventById(eventId);
    if (!ev) {
      viaId = "null";
    } else {
      try {
        viaId = "EVENTO: \"" + ev.getTitle() + "\" start=" + ev.getStartTime().toString();
      } catch (e) {
        viaId = "EVENTO OBTENIDO pero sus métodos fallan: " + e.message;
      }
    }
  } catch (e) {
    viaId = "ERROR: " + e.message;
  }
  Logger.log("getEventById -> " + viaId);
  Logger.log("Veredicto cascada: " + (viaId.indexOf("Invalid argument") !== -1 ? "BORRADO" : "vivo/existe"));
  try {
    const res = UrlFetchApp.fetch(
      "https://www.googleapis.com/calendar/v3/calendars/" + encodeURIComponent(CALENDARIO_VE)
        + "/events/" + encodeURIComponent(eventId),
      { headers: { Authorization: "Bearer " + ScriptApp.getOAuthToken() }, muteHttpExceptions: true }
    );
    const code = res.getResponseCode();
    let status = "";
    if (code === 200) {
      const body = JSON.parse(res.getContentText());
      status = " status=" + body.status;
    }
    Logger.log("REST -> HTTP " + code + status + (code !== 200 ? " cuerpo: " + res.getContentText().substring(0, 200) : ""));
  } catch (e) {
    Logger.log("REST -> no ejecutable: " + e.message);
  }
}

/**
 * Contador de escrituras con throttle suave: cada 25 operaciones se espera 100 ms
 * para no saturar las cuotas de Calendar API en sincronizaciones masivas.
 */
function registrarEscritura() {
  contadorEscrituras++;
  if (contadorEscrituras % 25 === 0) Utilities.sleep(100);
}
