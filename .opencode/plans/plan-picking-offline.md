# Plan Modular: App Android Offline-First (`plan-picking-offline.md`)

## Objetivo
Desarrollar, mantener y verificar las funcionalidades de la aplicación Android nativa (Kotlin, Jetpack Compose, Room DB) centrada en el picking de campo y viveros con arquitectura offline-first.

## Módulos y Ficheros Clave
- `app/src/main/java/`: Lógica de UI (Jetpack Compose), ViewModels, Repositorios y DAOs de Room.
- `app/src/main/res/drawable/logo_viveros.png`: Logotipo corporativo oficial (D-203).
- `apks/`: Carpeta de artefactos compilados (`PickingVE-debug-<versionName>.apk`).

---

## Fases de Ejecución

### Fase 1: Verificación de Dependencias y Base de Datos Local (Room)
1. Comprobar esquemas de entidades Room y migraciones en el módulo de persistencia local.
2. Garantizar que la UI lee exclusivamente de Room DB (arquitectura offline-first estricta).
3. Validar el correcto mapeo de campos de artículos, líneas de pedido y ubicaciones.

### Fase 2: Escaneo EAN y Validación OCR
1. Verificar la integración de la cámara/escáner para códigos de barras EAN en el flujo de picking.
2. Asegurar la resiliencia ante lecturas offline y almacenamiento temporal en colas locales.

### Fase 3: Marca Corporativa y UI (Material Design 3)
1. Aplicar la identidad visual corporativa usando los recursos oficiales de `Documentacion/Logos/` (copiados a `res/drawable/logo_viveros.png`).
2. Comprobar adaptabilidad en pantallas táctiles de campo y tablets.

### Fase 4: Build y Verificación en Dispositivo
1. Compilar con el JBR de Android Studio mediante `gradlew.bat assembleDebug`.
2. Instalar y probar el APK resultante en un emulador o dispositivo real usando `adb install -r apks\PickingVE-debug-<versionName>.apk`.
3. Invocar la skill `auditoria-experiencia-usuario` y/o `auditoria-android` para verificar ausencia de crashes y fluidez.
