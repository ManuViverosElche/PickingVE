/**
 * Calendario_Viveros_Elche - Triggers (D-51, D-53)
 *
 * Instalación de la automatización:
 *  - Trigger de tiempo CADA 10 MINUTOS ejecutando syncAll(): red de seguridad que
 *    recupera cualquier desfase (ediciones sin disparo de trigger, caídas, cuotas).
 *    Con D-53 el tiempo real lo aporta el trigger de calendario; los 10 minutos
 *    balancean frescura y cuota de tiempo de ejecución de triggers.
 *  - Trigger Calendar - Modificado en Viveros Elche: ante cualquier cambio (incluido
 *    mover eventos arrastrándolos en la UI, D-53) ejecuta syncAll() completo.
 *  - Trigger Calendar - Modificado en CARGAS: propaga la descripción editada por el
 *    transportista de vuelta a Viveros Elche (arbitraje en syncAll).
 *
 * Uso: ejecutar `removeTriggers()` y después `setupTriggers()` en el editor de Apps
 * Script (setupTriggers crea solo los que faltan; removeTriggers limpia los viejos,
 * p. ej. los que apuntaban a funciones eliminadas como syncCargasToTransportistas).
 */

/**
 * Recrea la automatización completa. Idempotente: ejecutable las veces que se quiera
 * sin duplicar triggers. Auto-repara (D-54): si el trigger de tiempo de syncAll existe
 * con un intervalo distinto de 10 min (p. ej. el horario de versiones anteriores), lo
 * elimina y lo crea de nuevo, de modo que basta ejecutar setupTriggers() para alinear
 * toda la configuración (no hace falta removeTriggers() previo).
 */
function setupTriggers() {
  let hayTiempo = false;
  let hayOrigen = false;
  let hayCargas = false;

  const triggers = ScriptApp.getProjectTriggers();
  for (const t of triggers) {
    if (t.getHandlerFunction() === "syncAll" &&
        t.getEventType() === ScriptApp.EventType.CLOCK) {
      if (t.getIntervalMinutes() === 10) hayTiempo = true;
      else ScriptApp.deleteTrigger(t);
    }
    if (t.getHandlerFunction() === "onEventUpdated" &&
        t.getEventType() === ScriptApp.EventType.ON_EVENT_UPDATED &&
        t.getTriggerSourceId() === CALENDARIO_VE) hayOrigen = true;
    if (t.getHandlerFunction() === "onEventUpdated" &&
        t.getEventType() === ScriptApp.EventType.ON_EVENT_UPDATED &&
        t.getTriggerSourceId() === ID_CARGAS) hayCargas = true;
  }

  if (!hayTiempo) {
    ScriptApp.newTrigger("syncAll")
      .timeBased()
      .everyMinutes(10)
      .create();
  }
  if (!hayOrigen) {
    ScriptApp.newTrigger("onEventUpdated")
      .forUserCalendar(CALENDARIO_VE)
      .onEventUpdated()
      .create();
  }
  if (!hayCargas) {
    ScriptApp.newTrigger("onEventUpdated")
      .forUserCalendar(ID_CARGAS)
      .onEventUpdated()
      .create();
  }
}

/**
 * Elimina todos los triggers del proyecto.
 */
function removeTriggers() {
  ScriptApp.getProjectTriggers().forEach(t => ScriptApp.deleteTrigger(t));
}
