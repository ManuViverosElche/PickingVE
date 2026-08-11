/**
 * Calendario_Viveros_Elche - Tiempo real (D-51, D-53, D-55)
 *
 * El trigger de calendario de Apps Script avisa de que algo cambió en el calendario,
 * pero Google NO garantiza qué evento fue ni cómo cambió; su documentación oficial
 * recomienda usarlo como señal de "hay cambios; haz un sync".
 *
 * D-53 (06/08/2026): mover un evento ARRASTRÁNDOLO en la UI no propagaba el cambio a
 * su copia: la versión anterior dependía del objeto calendarEvent del trigger, que no
 * es fiable. Decisión: el trigger ejecuta la reconciliación COMPLETA (syncAll), que
 * cubre cualquier tipo de cambio (crear, editar, mover, color, bidireccional CARGAS).
 *
 * D-55 (06/08/2026): los BORRADOS desde la UI no disparan el trigger de forma fiable
 * (pruebas reales: borrar un evento dejó su copia huérfana mientras que otros borrados
 * sí fueron limpiados por el temporizador). Además, el guard anterior que comprobaba
 * e.calendarId podía descartar disparos cuyo payload no incluye ese campo. Los
 * triggers solo están instalados en Viveros Elche y CARGAS (Triggers.gs), por lo que
 * todo disparo es legítimo: se eliminó el guard y onEventUpdated siempre sincroniza.
 * La cascada de borrado queda garantizada por el temporizador de 10 min como máximo,
 * y por el trigger en los borrados que sí lo disparan.
 *
 * Antibucles: syncAll solo escribe en Viveros Elche en la propagación bidireccional de
 * CARGAS (matrículas del transportista); esa escritura refleja el mismo valor del tag
 * lastSyncedDescription, por lo que el siguiente disparo no detecta diferencias y la
 * cadena termina. Las escrituras en calendarios de zona no tienen triggers instalados.
 */

/**
 * Punto de entrada del trigger Calendar - Modificado (instalado en Viveros Elche y en
 * CARGAS, ver Triggers.gs). Ejecuta la reconciliación completa; sin filtros sobre el
 * payload (D-55): los triggers solo existen en los dos calendarios del sistema y el
 * payload no es fiable en todos los casos de edición/borrado.
 */
function onEventUpdated() {
  syncAll();
}
