---
name: prueba-dispositivo-apk
description: Skill para probar el APK de PickingVE en un móvil/emulador REAL con adb: instalar, navegar por pantallas, capturar screenshots y detectar crashes. Convierte la auditoría de experiencia de usuario en prueba real. Activar con frases como "instala el APK", "prueba la app en el móvil", "haz un smoke test real", "mira si funciona en el dispositivo", o junto a auditoria-experiencia-usuario.
---

# Skill: Prueba Real del APK en Dispositivo (adb)

## Objetivo
Validar PickingVE como lo haría un operario en campo: instalar el APK más reciente, recorrer las pantallas críticas, verificar estados offline y capturar evidencias (screenshots + logcat). Nada se declara "funciona" sin haberlo visto en pantalla.

## Datos fijos
- Package: `com.vivero.pickingve` — Activity principal: `com.vivero.pickingve/.MainActivity`
- adb NO está en PATH. Usar SIEMPRE: `C:\Users\Usuario\AppData\Local\Android\Sdk\platform-tools\adb.exe`
- APK: usar SIEMPRE el más reciente de `apks\PickingVE-debug-<versionName>.apk` (no fijar versión).

## Pasos obligatorios

### 1. Preparación
1. `adb devices` → si no hay dispositivo, pedir al usuario conectar móvil con depuración USB activada (o arrancar emulador) y reintentar.
2. Localizar el APK más reciente de `apks/` por fecha.
3. Instalar: `adb install -r "<ruta del apk>"`.
4. Limpiar logs previos: `adb logcat -c`.

### 2. Smoke test guiado (por pantallas)
Para cada paso: lanzar acción → esperar 2-3 s (`adb shell sleep`) → **screenshot obligatorio**:
```
adb exec-out screencap -p > C:\Users\Usuario\AppData\Local\Temp\opencode\<paso>.png
```
y ABRIR el PNG con la herramienta Read para VERIFICAR visualmente antes de continuar.

Recorrido mínimo:
1. Arrancar app: `adb shell am start -n com.vivero.pickingve/.MainActivity` → pantalla login visible.
2. Login con usuario de prueba (pedir credenciales al usuario si no hay ninguna conocida).
3. Pantalla inicial según rol (OPERARIO→Faena, ENCARGADO→Pedidos).
4. Abrir un pedido → pantalla picking renderiza líneas.
5. Ajustes → abrir y volver atrás (verificar botón atrás).
6. Logout → vuelve a login.

### 3. Prueba offline-first (la crítica)
1. Activar avión: `adb shell cmd connectivity airplane-mode enable`
2. Repetir acciones clave (abrir pedido, marcar línea).
3. Verificar que la app sigue usable y los datos quedan en local (sin spinner infinito ni crash).
4. Desactivar avión: `adb shell cmd connectivity airplane-mode disable`
5. Verificar sincronización posterior (badge de pendientes baja / datos suben).

### 4. Detección de crashes
Tras cada paso y al final:
- `adb logcat -d *:E AndroidRuntime:E | Select-String -Pattern "FATAL|com.vivero.pickingve"`
- Si hay FATAL: capturar stack completo y asociarlo al paso concreto donde ocurrió.

### 5. Interacción táctil (cuando haga falta pulsar algo)
- Coordenadas desde el screenshot analizado: `adb shell input tap <x> <y>`, texto: `adb shell input text "<texto>"`, atrás: `adb shell input keyevent 4`.
- Tras cada interacción, screenshot de verificación.

## Reglas de oro
- Screenshot = evidencia. Cada afirmación del informe ("la pantalla carga bien") debe tener un PNG detrás.
- `pm clear com.vivero.pickingve` borra TODOS los datos locales: solo con permiso explícito del usuario.
- No desinstalar la app del dispositivo del usuario salvo orden expresa.
- Si un paso falla, repetirlo UNA vez tras reiniciar la app antes de reportarlo como bug; anotar si es intermitente.
