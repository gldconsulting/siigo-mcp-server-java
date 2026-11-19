# Siigo MCP Server Java

- Java 21, Maven; coordenadas `co.gldconsulting:siigo-mcp-server`. Licencia MIT de GLD Consulting S.A.S.
- Package `co.gldconsulting.siigo.mcp`; usar exclusivamente `co.gldconsulting:siigo-api-sdk:1.0.0` para acceder a Siigo. No duplicar endpoints, autenticación ni gestión de tokens.
- Al arrancar, leer `SIIGO_PARTNER_ID`, `SIIGO_USERNAME` y `SIIGO_ACCESS_KEY` del entorno y configurar el builder del SDK. El SDK obtiene, guarda en memoria y renueva el token. No aceptar el token Siigo por herramientas ni argumentos de línea de comandos. No guardar credenciales ni tokens en disco.
- Las únicas interfaces elegibles como herramientas son las 14 de `SiigoToolRegistry`. Mantener campos snake_case, parámetros de cada operación, tipos anidados, BigDecimal y LocalDate. No exponer autenticación ni HTTP arbitrario.
- `SIIGO_MCP_HTTP_TOKEN` autentica al cliente MCP en modo HTTP; es independiente del token de Siigo y también se recibe por entorno. Mantener validación de Host y Origin, máximo de petición y logs sin payloads ni credenciales.
- STDOUT pertenece exclusivamente al protocolo STDIO; ayuda y errores de inicio van a STDERR sin secretos. Terminar limpiamente al cerrar stdin.
- Mantener anotaciones MCP de lectura/escritura/destrucción. `SIIGO_MCP_READ_ONLY=true` elimina las escrituras; no confundir hints con autorizaciones del host. Una instancia usa una sola empresa; no servir empresas diferentes con credenciales globales.
- Usar el SDK para paginación completa y filtro local por fecha contable de compras/journals. No inventar filtros remotos ni convertir paginación incompleta en listas vacías. Documentar que Siigo ha ignorado `journals.document_id` y filtros remotos de fecha.
- Validación: `./mvnw clean verify`; las pruebas usan HTTP local simulado y prueban el JAR STDIO empaquetado y Streamable HTTP. No hacer escrituras reales para probar. Actualizar `docs/tools.md` al cambiar la dependencia o registro.
- No publicar, desplegar, crear releases ni subir cambios a GitHub sin autorización del usuario. Conservar cambios ajenos.
