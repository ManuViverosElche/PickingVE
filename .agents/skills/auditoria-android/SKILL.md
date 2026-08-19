---
name: auditoria-android
description: Skill para ejecutar auditorías integrales de calidad, seguridad, rendimiento y buenas prácticas en proyectos Android con Kotlin y Jetpack Compose.
---

# Skill: Auditoría y Control de Calidad Android (Clean Architecture)

## Objetivo
Evaluar el estado del proyecto Android, detectando fugas de memoria/recursos, fallos de seguridad, componentes obsoletos y cuellos de botella de rendimiento.

## Instrucciones y Pasos de Verificación

1. **Compilación y Análisis Estático:**
   - Ejecuta `./gradlew test` y `./gradlew lint` (o `./gradlew assembleDebug`).
   - Identifica advertencias (warnings) de compilación, parámetros no utilizados y APIs obsoletas (`@Deprecated`).

2. **Gestión de Ciclo de Vida y Recursos (Batería y Memoria):**
   - Comprueba que la recolección de `Flow`/`StateFlow` en la UI use `collectAsStateWithLifecycle()`.
   - Verifica que los ejecutores de fondo (`Executors`), cámaras, subscripciones, receptores (`BroadcastReceiver`) o temporizadores se cierren/liberen correctamente en `DisposableEffect` / `onDispose`.

3. **Seguridad y Almacenamiento Criptográfico:**
   - Revisa que las credenciales, tokens y datos sensibles usen `EncryptedSharedPreferences` / Android Keystore y no `SharedPreferences` estándar.
   - Confirma la ausencia de claves de API en duro (deben estar en `local.properties` / `BuildConfig`).
   - Verifica que no existan SDKs o analíticas no autorizadas.

4. **Integridad de Datos y Concurrencia:**
   - Asegura que las operaciones de persistencia (Room / Supabase) se ejecuten bajo `Dispatchers.IO`.
   - Revisa el manejo de excepciones en corrutinas (`try-catch` o `CoroutineExceptionHandler`).

5. **Optimización de UI (Jetpack Compose):**
   - Asegura el uso de claves estables (`key = { ... }`) en listas grandes (`LazyColumn` / `LazyRow`).
   - Verifica la migración a componentes modernos (ej. `Icons.AutoMirrored` y lambdas de estado).

## Formato del Informe Final
Entrega el resultado estructurado en:
- 🔴 **Errores Críticos / Bloqueantes** (Fugas de hilo, crash potenciales, fallos de seguridad)
- 🟡 **Advertencias y Deuda Técnica** (Warnings, código deprecado, optimizaciones pendientes)
- 🟢 **Puntos Fuertes y Estado General de la App**
- 📋 **Plan de Acción Recomendado / Prompts de Corrección**
