# Plan Modular: Reportes XLSX y Automatización Telegram (`plan-reportes-excel-telegram.md`)

## Objetivo
Mantener y supervisar la generación de reportes profesionales en formato `.xlsx` replicando el modelo oficial (`Documentacion/picking_260833_I.xlsx`), junto con las notificaciones automatizadas.

## Módulos y Ficheros Clave
- Generador de reportes (`XlsxReportGenerator` / scripts en backend).
- Módulos de notificación y bot de Telegram.
- Archivos de muestra en `Documentacion/`.

---

## Fases de Ejecución

### Fase 1: Validación del Generador XLSX
1. Verificar que los reportes generados utilicen estrictamente formato `.xlsx` (prohibido CSV para informes finales, reservando CSV solo para etiquetas pendientes).
2. Comprobar la fidelidad de las plantillas y fórmulas frente a `Documentacion/picking_260833_I.xlsx`.

### Fase 2: Automatización de Notificaciones (Telegram)
1. Comprobar la correcta entrega de avisos y resúmenes de turno mediante el bot de Telegram.
2. Validar manejo de excepciones ante fallos de conectividad de red de forma asíncrona.

### Fase 3: Pruebas y Verificación Integral
1. Ejecutar pruebas unitarias e integrales del generador de reportes.
2. Registrar cualquier decisión de formato o estructura en `docs/SPECS.md` siguiendo la nomenclatura `D-XX`.
