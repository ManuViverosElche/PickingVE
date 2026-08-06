# MCP Google Drive - Guía de reparación y mantenimiento (opencode)

> Nota: `opencode.json` y `.env` están en `.gitignore` (contienen secretos), por lo que
> esta guía es la única fuente de verdad versionada sobre cómo dejar el MCP operativo.

## Contexto (fallo 05-06/08/2026)

El MCP de Google Drive dejó de listar archivos. Tras depurar se confirmaron tres causas
encadenadas:

1. **Configuración inválida**: el bloque `gdrive` usaba la clave `env` en vez de
   `environment`. opencode valida el schema estrictamente y no recarga config en caliente:
   **toda edición de `opencode.json` requiere reiniciar opencode**.
2. **Refresh token corrupto**: otro agente introdujo un token con una letra cambiada
   (`w` minúscula donde el real lleva `W`, pos. 90) → `invalid_grant`. Se regeneró en
   OAuth Playground.
3. **Bug del paquete**: `mcp-google-drive@1.6.2` (última versión) está roto:
   `ListFilesHandler` llama a `this.formatFileList()` que no existe (issue #4 de
   `Longtran2404/mcp-google-drive`, sin fix) → `list_files`/`search_files` devuelven vacío
   y `get_file`/`get_file_content` errores zod. **No actualizar a 1.6.x sin verificar.**

## Configuración vigente (`opencode.json`)

```jsonc
"gdrive": {
  "type": "local",
  "command": ["npx", "-y", "mcp-google-drive@1.5.0"],
  "environment": {
    "GOOGLE_CLIENT_ID": "<cliente OAuth Web de Viveros Elche>",
    "GOOGLE_CLIENT_SECRET": "<secret>",
    "GOOGLE_REFRESH_TOKEN": "<refresh token con scope calendar>"
  },
  "timeout": 120000,
  "enabled": true
}
```

Las mismas credenciales viven en `.env` como `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`,
`GOOGLE_REFRESH_TOKEN` (además de las `GDRIVE_*` originales, sin tocar).

## Parche necesario en v1.5.0

`mcp-google-drive@1.5.0` devuelve el resultado de `callTool` **sin envoltura MCP válida**
(`content`), por lo que opencode lo muestra vacío. Hay que añadir la envoltura en el
`dist/index.js` de la copia instalada (ruta bajo `%LOCALAPPDATA%\npm-cache\_npx\<hash>\node_modules\mcp-google-drive\dist\index.js`, dentro de `handleCallTool`):

```diff
        log(`Tool ${name} executed successfully`, 'debug');
-       return result;
+       return { content: [{ type: 'text', text: JSON.stringify(result) }] };
```

Si npx purga el cache y se re-descarga el paquete, **re-aplicar el parche** (la ruta
cambia de hash).

## Refresh token: caducidad y regeneración

- La app OAuth está en modo **Testing** en Google Cloud ⇒ los refresh tokens caducan a los
  **7 días** (`refresh_token_expires_in: 604799`). Síntoma: `invalid_grant` en las llamadas.
- Regenerar en https://developers.google.com/oauthplayground (client OAuth Web + redirect
  `https://developers.google.com/oauthplayground`) con estos scopes:
  - `https://www.googleapis.com/auth/drive`
  - `https://www.googleapis.com/auth/spreadsheets`
  - `https://www.googleapis.com/auth/documents`
  - `https://www.googleapis.com/auth/presentations`
  - `https://www.googleapis.com/auth/calendar`
  - `https://www.googleapis.com/auth/script.projects`
  - `https://www.googleapis.com/auth/userinfo.email`
- Actualizar el token en los **tres** sitios: `GOOGLE_REFRESH_TOKEN` (`.env`),
  `GDRIVE_REFRESH_TOKEN` (`.env`) y `GOOGLE_REFRESH_TOKEN` (opencode.json), y reiniciar
  opencode.
- Alternativa definitiva: publicar la app en el consent screen (sin verificación) para que
  los tokens no caduquen.

## Google Calendar (instalado 06/08/2026)

El paquete anterior (`@modelcontextprotocol/server-google-calendar`) **fue retirado de
npm** (404) — esa fue la causa del error -32000. Se instaló **`@cocal/google-calendar-mcp`
(nspady, v2.6.2)**, el más mantenido de la comunidad.

Configuración en `opencode.json`:

```jsonc
"gcalendar": {
  "type": "local",
  "command": ["npx", "-y", "@cocal/google-calendar-mcp"],
  "environment": {
    "GOOGLE_OAUTH_CREDENTIALS": "C:\\Users\\Usuario\\Documents\\mcp-gcal\\gcp-oauth.keys.json"
  },
  "timeout": 120000,
  "enabled": true
}
```

- Usa un **OAuth client tipo Desktop** propio (client id `...-j9hv5f56bp7f1250fv9meg2tmete91na`),
  no el cliente Web de Drive. El JSON vive en `C:\Users\Usuario\Documents\mcp-gcal\gcp-oauth.keys.json`
  (fuera del repo; la copia original está en `Documentacion/`, que es gitignored).
- Autenticación inicial (una vez): `npx -y @cocal/google-calendar-mcp auth` con
  `GOOGLE_OAUTH_CREDENTIALS` apuntando al JSON → login en navegador con
  `viveroselchels@gmail.com`. Tokens persistidos en
  `%USERPROFILE%\.config\google-calendar-mcp\tokens.json`.
- Tools: `list_calendars`, `list_events`, `create_event`, `update_event`, `delete_event`,
  `get_event`, `respond_to_event`, `suggest_time` (convención `calendar_*`).
- Caveat: en modo Testing los tokens caducan a los 7 días → re-ejecutar `auth` o publicar
  la app en el consent screen.

