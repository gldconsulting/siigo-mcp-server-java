# Siigo MCP Server para Java

[![Java 21](https://img.shields.io/badge/Java-21-007396)](pom.xml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Servidor de **Model Context Protocol (MCP)** de GLD Consulting S.A.S. para conectar asistentes de IA con **Siigo Colombia**, usando [nuestro SDK Java](https://github.com/gldconsulting/siigo-api-sdk-java), publicado como `co.gldconsulting:siigo-api-sdk:1.0.0`.

Java 21, Maven, SDK oficial MCP Java, STDIO y Streamable HTTP. Independiente del proveedor del modelo: el asistente o aplicación debe actuar como cliente MCP compatible; un modelo por sí solo no abre conexiones MCP.

> [!WARNING]
> **Uso interno: este MCP no está diseñado para exponerse públicamente a Internet.**
>
> Debe utilizarse como herramienta interna o de apoyo para otros procesos, con acceso limitado a usuarios y sistemas autorizados. Exponerlo públicamente puede introducir una brecha de seguridad en tu arquitectura tecnológica y permitir el acceso no autorizado a datos contables de tu empresa y operaciones de Siigo.
>
> El bearer del transporte HTTP, la validación de Host/Origin y HTTPS no convierten esta implementación en un servicio preparado para exposición pública. Mantener el proceso en el equipo local o en una red privada; para ChatGPT, usar la conexión privada mediante Secure MCP Tunnel descrita más adelante.

## Compilar el JAR

Requiere un JDK 21 y acceso a Maven Central. El wrapper descarga Maven; no se requiere una instalación global.

```sh
./mvnw clean verify
```

Este comando genera y prueba el JAR; no deja un MCP operativo esperando conexiones. Quién ejecuta después el JAR depende del transporte elegido, como se explica en la tabla de arranque más adelante.

El resultado es un JAR ejecutable con sus dependencias. La versión del servidor sigue siendo de desarrollo; este repositorio no implica que el servidor MCP ya esté publicado en Maven Central.

## Credenciales: entorno → SDK al iniciar

El proceso recibe estas tres variables **antes de arrancar**:

| Variable | Uso |
|---|---|
| `SIIGO_PARTNER_ID` | Partner-Id de la integración |
| `SIIGO_USERNAME` | Usuario API de Siigo |
| `SIIGO_ACCESS_KEY` | Access key de Siigo |

El MCP las lee una sola vez en el startup y configura `SiigoApi.builder()`. A partir de ahí, **el SDK** obtiene el token al necesitarlo, lo almacena en memoria y lo renueva según su ciclo de vida. El servidor no implementa `/auth`, ni una caché alternativa, ni persistencia del token en disco. Reiniciar el proceso inicia otra sesión del SDK.

Las variables se pasan al proceso con el entorno del shell, el gestor de secretos del despliegue, o el campo `env` del cliente MCP. No son argumentos de las herramientas ni parámetros `--username`, `--access-key` o `--token` de `java`. No es necesario pasar un token de Siigo; el servidor exige las tres variables anteriores.

Las variables deben llegar al proceso que iniciará Java: el cliente local para STDIO, tu terminal o servicio para HTTP, o `tunnel-client` para el ejemplo de ChatGPT. Configurarlas en otra terminal no garantiza que esos procesos las hereden.

No imprimirlas para comprobarlas ni guardarlas en el repositorio. Los ejemplos solo contienen marcadores; no son credenciales válidas.

## Quién arranca el MCP y qué preparar antes de conectar

| Cliente y modalidad | Quién inicia el proceso Java del MCP | Qué hacer antes de conectar el cliente |
|---|---|---|
| Claude Desktop por STDIO | Claude Desktop, cuando conecta el servidor configurado | Compilar el JAR y configurar su ruta, Java 21 y las credenciales en el cliente; después abrir o reiniciar Claude Desktop |
| Claude Code por STDIO | Claude Code, al conectar el servidor configurado | Compilar el JAR, preparar las variables en el entorno de Claude Code y registrar el servidor |
| OpenCode con `type: "local"` | OpenCode, al conectar el servidor configurado | Compilar el JAR y preparar `opencode.jsonc` y las variables en el entorno de OpenCode |
| OpenCode u otro cliente por HTTP | Tú, o un servicio de tu infraestructura | Configurar las credenciales y el bearer MCP, arrancar el servidor HTTP y mantenerlo funcionando; después conectar el cliente a su URL privada |
| ChatGPT mediante Secure MCP Tunnel y STDIO | `tunnel-client`, que ejecuta el comando Java del perfil | Compilar el JAR, configurar el perfil y las variables, y arrancar `tunnel-client`; después seleccionar el túnel en ChatGPT |

- **STDIO local:** configuras el comando; el cliente arranca y administra su propio proceso MCP. No debes ejecutar otro `java -jar` manualmente en una terminal para ese cliente: no se conectaría a ese proceso independiente. Cada cliente local tiene su propia instancia del MCP y su propia sesión del SDK.
- **HTTP:** arrancas el servidor primero y lo dejas funcionando. El cliente solo se conecta a una URL; no ejecuta Java. Varios clientes autorizados pueden compartir esa instancia, que actúa sobre una sola empresa Siigo.
- **ChatGPT con el túnel del ejemplo:** tú arrancas `tunnel-client`; este inicia el MCP por STDIO. ChatGPT se conecta al túnel y no inicia directamente un proceso Java en tu equipo. Mantener el túnel activo es necesario para usar sus herramientas.

En los tres casos, el proceso Java recibe las credenciales por entorno y el SDK gestiona el token Siigo. Cerrar una instancia descarta su sesión en memoria; la siguiente instancia obtiene un token mediante el SDK cuando lo necesita.

## Conectar un cliente local: STDIO

**El cliente arranca el MCP automáticamente al conectar.** STDIO es el transporte predeterminado.

Antes de abrir o conectar el cliente:

1. Compilar el JAR y comprobar que existe en la ruta que se configurará.
2. Indicar en el cliente el ejecutable Java 21, la ruta absoluta del JAR y las tres variables de credenciales.
3. Abrir, reiniciar o reconectar el cliente para que ejecute ese comando y descubra las herramientas. No iniciar un servidor Java aparte para esta modalidad.

En un cliente que admita la estructura `mcpServers`:

```json
{
  "mcpServers": {
    "siigo": {
      "command": "java",
      "args": ["-jar", "/ruta/absoluta/siigo-mcp-server-1.0.0-SNAPSHOT.jar"],
      "env": {
        "SIIGO_PARTNER_ID": "<partner-id>",
        "SIIGO_USERNAME": "<usuario-api>",
        "SIIGO_ACCESS_KEY": "<access-key>"
      }
    }
  }
}
```

Usar el soporte de secretos/entorno del cliente para los valores reales. La sintaxis para referenciar variables o secretos depende de cada host; los marcadores del ejemplo no se expanden automáticamente. Si el cliente hereda las tres variables, se puede omitir `env`. Usar una ruta absoluta al ejecutable Java 21 si el host no tiene configurado `PATH`.

El servidor mantiene STDOUT libre de banners y logs: solo mensajes JSON-RPC MCP. Al cerrar stdin, cierra el servidor y el SDK.

## Conectar por Streamable HTTP en una red privada

**Tú o un servicio de tu infraestructura arrancan el MCP antes de conectar los clientes.** El cliente HTTP no ejecuta el JAR y no inicia el servidor si está detenido.

Configurar en el equipo del servidor las tres credenciales Siigo y estas opciones adicionales:

| Variable | Valor / predeterminado |
|---|---|
| `SIIGO_MCP_TRANSPORT` | `http` (`stdio` por defecto) |
| `SIIGO_MCP_HOST` | Dirección de escucha; `127.0.0.1` por defecto |
| `SIIGO_MCP_PORT` | `8080` |
| `SIIGO_MCP_HTTP_TOKEN` | Secreto de al menos 32 caracteres para autenticar **clientes MCP**, independiente del token Siigo |
| `SIIGO_MCP_ALLOWED_HOSTS` | Nombres Host permitidos, separados por comas; por defecto `localhost,127.0.0.1,[::1]` |
| `SIIGO_MCP_ALLOWED_ORIGINS` | Orígenes de navegador permitidos, separados por comas; ninguno por defecto. Los clientes sin Origin pueden conectarse |

Orden de arranque:

1. Compilar el JAR y configurar en el entorno del servidor las tres credenciales Siigo y `SIIGO_MCP_HTTP_TOKEN`.
2. Ejecutar el comando siguiente en una terminal que permanecerá abierta, o configurar un servicio interno que mantenga ese proceso en ejecución.
3. Con el servidor ya funcionando, configurar la URL y el bearer MCP en cada cliente y conectar. Si el servidor se detiene, los clientes no podrán usar sus herramientas hasta que vuelva a arrancar.

```sh
SIIGO_MCP_TRANSPORT=http java -jar target/siigo-mcp-server-1.0.0-SNAPSHOT.jar
```

Las tres credenciales Siigo y `SIIGO_MCP_HTTP_TOKEN` deben estar configuradas antes de ejecutar ese comando. Endpoint: `http://127.0.0.1:8080/mcp`. El cliente debe enviar `Authorization: Bearer <SIIGO_MCP_HTTP_TOKEN>` en todas las solicitudes, incluidas las de sesión. La ruta soporta POST, GET y DELETE del transporte MCP; borrar una sesión HTTP no borra documentos Siigo.

El transporte negocia el protocolo MCP de la familia `2025-11-25` y revisiones anteriores admitidas por el SDK oficial. No implementa el servidor del protocolo `2026-07-28` en este primer alcance. Si otro equipo de la red privada necesita conectarse, usar HTTPS en el punto de entrada interno y configurar los Host/Origin admitidos. El endpoint debe permanecer privado; estas opciones no autorizan su publicación en Internet. Esta versión **no implementa un authorization server OAuth ni discovery OAuth**: los clientes remotos que exigen OAuth requieren una capa de autorización compatible. No afirmar compatibilidad directa con un host concreto sin probar su configuración.

`127.0.0.1` siempre se refiere al equipo donde se ejecuta el cliente. El ejemplo funciona cuando cliente y servidor están en el mismo equipo. Para equipos distintos dentro de una red privada, configurar `SIIGO_MCP_HOST` con la dirección privada de escucha, autorizar el nombre interno en `SIIGO_MCP_ALLOWED_HOSTS` y usar una URL HTTPS interna accesible para el cliente. Mantener la restricción de no exponerlo públicamente a Internet.

Una instancia utiliza **una sola cuenta/empresa Siigo**. Todos los clientes autorizados de esa instancia actúan sobre esa misma empresa. Para empresas o políticas diferentes, ejecutar instancias separadas; no compartir esta configuración global como si fuera aislamiento multiempresa.

## Ejemplos por cliente: ChatGPT, Claude y OpenCode

Los siguientes ejemplos son instrucciones de configuración; no implican que se haya conectado ninguna cuenta. Compilar el JAR primero, usar Java 21 y definir las credenciales Siigo en el entorno del proceso que lo arranca. El SDK conserva la gestión del token en todos los casos.

| Cliente | Conexión del ejemplo | Configuración |
|---|---|---|
| ChatGPT web | Secure MCP Tunnel → STDIO local | Perfil de `tunnel-client` y plugin en ChatGPT |
| Claude Desktop | STDIO local | `claude_desktop_config.json`, ejemplo de la sección anterior |
| Claude Code | STDIO local | `.mcp.json` o CLI |
| OpenCode V2 | STDIO local o Streamable HTTP | `opencode.jsonc`, bajo `mcp.servers` |

### ChatGPT: servidor privado mediante Secure MCP Tunnel

**Quién arranca el MCP:** `tunnel-client`, al ejecutar el comando Java definido en su perfil. **Qué arrancas tú primero:** el proceso `tunnel-client`; no un servidor Java separado para este ejemplo STDIO.

Antes de conectar ChatGPT:

1. Compilar el JAR e instalar `tunnel-client` según la guía oficial enlazada abajo.
2. Preparar el túnel, el perfil y las variables en el entorno del proceso `tunnel-client`, como se detalla a continuación.
3. Iniciar `tunnel-client run`, comprobar que la conexión esté lista y dejarlo funcionando. Solo después seleccionar el túnel en ChatGPT.

ChatGPT no ejecuta directamente `java -jar`. Una opción documentada por OpenAI es [Secure MCP Tunnel](https://developers.openai.com/api/docs/guides/secure-mcp-tunnels), que permite enlazar el proceso STDIO privado. Requiere acceso a Tunnels en la organización OpenAI y un `tunnel-client` instalado según esa guía; la disponibilidad depende de los permisos de la cuenta.

Crear un túnel en Platform, asociarlo al workspace previsto y configurar la credencial de ejecución del cliente mediante su mecanismo seguro/entorno (`CONTROL_PLANE_API_KEY`). Guardar el identificador, que no es la access key Siigo, en `SIIGO_MCP_TUNNEL_ID`. Las tres variables `SIIGO_*` de credenciales deben estar disponibles para el proceso Java hijo.

```sh
tunnel-client init \
  --sample sample_mcp_stdio_local \
  --profile siigo \
  --tunnel-id "$SIIGO_MCP_TUNNEL_ID" \
  --mcp-command "java -jar /ruta/absoluta/siigo-mcp-server-1.0.0-SNAPSHOT.jar"

tunnel-client doctor --profile siigo --explain
SIIGO_MCP_TRANSPORT=stdio tunnel-client run --profile siigo
```

Mantener ese proceso activo durante el uso. Consultar `tunnel-client help quickstart` si cambia la sintaxis de la versión instalada. El túnel tiene sus propios límites de tiempo/tamaño; para períodos grandes, preferir consultas paginadas.

En ChatGPT web, siguiendo [Add custom MCP server](https://developers.openai.com/api/docs/guides/custom-mcp-server):

1. Abrir **Plugins**, pulsar **+ → Add custom MCP server**.
2. Nombre: **Siigo GLD Consulting**. En **Connection**, seleccionar **Tunnel** y el túnel configurado.
3. Para este proceso STDIO, elegir **No authentication** a nivel del MCP: el acceso al túnel lo controla OpenAI. No publicar el endpoint HTTP de Siigo MCP sin su protección para imitar esta opción.
4. Revisar el acceso solicitado, crear el plugin e instalarlo. En un chat, seleccionarlo con `@` y pedir: «Consulta las facturas de compra entre el 1 y el 31 de agosto de 2026 usando Siigo GLD Consulting».

Este proyecto no ofrece un modo de despliegue público. Para conectar ChatGPT, mantener el servidor privado y utilizar el túnel descrito arriba. ChatGPT no admite una API key personalizada como sustituto de OAuth; `SIIGO_MCP_HTTP_TOKEN` no se pega en el formulario como si fuera una credencial Siigo. Ver [autenticación de plugins](https://developers.openai.com/plugins/build/auth). El túnel privado no equivale a publicar el endpoint ni el plugin en un directorio público.

### Claude Desktop

**Quién arranca el MCP:** Claude Desktop. No hay que dejar Java ejecutándose antes en una terminal.

Antes de conectar:

1. Compilar el JAR y localizar las rutas absolutas de Java 21 y del JAR.
2. Preparar la configuración y las variables del proceso Java con el bloque STDIO descrito abajo.
3. Cerrar y volver a abrir Claude Desktop para que inicie su MCP y descubra las herramientas.

En **Settings → Developer → Edit Config**, integrar el bloque `mcpServers.siigo` del [ejemplo STDIO](examples/stdio-client.json) con la configuración existente. Ubicaciones habituales:

- macOS: `~/Library/Application Support/Claude/claude_desktop_config.json`.
- Windows: `%APPDATA%\Claude\claude_desktop_config.json`.

Usar rutas absolutas a Java 21 y al JAR. Los campos `env` del ejemplo pasan valores al entorno del proceso Java; no añadir las credenciales a `args`. Los marcadores `<...>` deben reemplazarse mediante la configuración privada del host; no son referencias automáticas a variables del shell. Una app abierta desde la interfaz gráfica puede no heredar variables exportadas en una terminal.

Cerrar y volver a abrir Claude Desktop, comprobar el servidor en sus ajustes de desarrollador y solicitar una consulta de lectura. El archivo del ejemplo no es la configuración de Claude web. Referencia: [conexión local en la documentación oficial MCP](https://modelcontextprotocol.io/docs/develop/connect-local-servers).

### Claude Code

**Quién arranca el MCP:** Claude Code, al conectar el servidor registrado por STDIO. No iniciar un servidor Java aparte.

Antes de conectar, compilar el JAR, preparar las credenciales en el entorno que iniciará Claude Code y registrar el servidor mediante `.mcp.json` o el comando CLI. Después abrir o reconectar Claude Code y comprobarlo con `/mcp`.

Con las credenciales exportadas en el entorno desde el que se ejecuta Claude Code, agregar a `.mcp.json`:

```json
{
  "mcpServers": {
    "siigo": {
      "type": "stdio",
      "command": "java",
      "args": ["-jar", "/ruta/absoluta/siigo-mcp-server-1.0.0-SNAPSHOT.jar"],
      "env": {
        "SIIGO_PARTNER_ID": "${SIIGO_PARTNER_ID}",
        "SIIGO_USERNAME": "${SIIGO_USERNAME}",
        "SIIGO_ACCESS_KEY": "${SIIGO_ACCESS_KEY}",
        "SIIGO_MCP_TRANSPORT": "stdio"
      }
    }
  }
}
```

Claude Code sí documenta la expansión `${VARIABLE}`. Como alternativa, si hereda las tres credenciales y no se ha configurado el transporte HTTP:

```sh
claude mcp add --transport stdio --scope user siigo -- java -jar /ruta/absoluta/siigo-mcp-server-1.0.0-SNAPSHOT.jar
claude mcp list
```

Revisar el servidor con `/mcp` dentro de Claude Code. Referencia: [MCP en Claude Code](https://code.claude.com/docs/en/mcp). La forma de expansión anterior no se debe copiar sin cambios a otros clientes.

### OpenCode V2: local

**Quién arranca el MCP:** OpenCode, porque la entrada usa `type: "local"`. No hace falta iniciar Java antes.

Antes de conectar, compilar el JAR, configurar su ruta absoluta y las variables en el entorno de OpenCode. Guardar la configuración siguiente y abrir o reconectar OpenCode; este iniciará el proceso MCP.

Crear o integrar en `opencode.jsonc` del proyecto (o `~/.config/opencode/opencode.jsonc` para todo el usuario):

```json
{
  "$schema": "https://opencode.ai/config.json",
  "mcp": {
    "servers": {
      "siigo": {
        "type": "local",
        "command": ["java", "-jar", "/ruta/absoluta/siigo-mcp-server-1.0.0-SNAPSHOT.jar"],
        "protocol": "legacy",
        "environment": {
          "SIIGO_PARTNER_ID": "{env:SIIGO_PARTNER_ID}",
          "SIIGO_USERNAME": "{env:SIIGO_USERNAME}",
          "SIIGO_ACCESS_KEY": "{env:SIIGO_ACCESS_KEY}",
          "SIIGO_MCP_TRANSPORT": "stdio"
        }
      }
    }
  }
}
```

Las variables deben existir en el entorno que inicia OpenCode. V2 usa `mcp.servers`; los ejemplos antiguos de V1 colocaban los servidores directamente bajo `mcp`. `protocol: legacy` selecciona la familia MCP implementada por este servidor. Referencias: [MCP en OpenCode](https://opencode.ai/docs/mcp-servers/) y [ubicaciones y variables de configuración](https://opencode.ai/docs/config/).

### OpenCode V2: HTTP

**Quién arranca el MCP:** tú o el servicio interno que lo hospeda. Con `type: "remote"`, OpenCode solo se conecta a la URL y no arranca Java.

Antes de conectar:

1. Seguir el [orden de arranque HTTP](#conectar-por-streamable-http-en-una-red-privada) y dejar el servidor funcionando.
2. Configurar en el entorno de OpenCode `SIIGO_MCP_HTTP_TOKEN` con el mismo bearer MCP que usa el servidor. Las credenciales Siigo solo se configuran en el servidor.
3. Guardar la configuración de URL siguiente y abrir o reconectar OpenCode. Mantener el proceso del servidor activo durante su uso.

Con el servidor ya ejecutándose en modo HTTP, el cliente solo necesita el bearer de acceso **MCP**; las credenciales Siigo permanecen en el servidor:

```json
{
  "$schema": "https://opencode.ai/config.json",
  "mcp": {
    "servers": {
      "siigo": {
        "type": "remote",
        "url": "http://127.0.0.1:8080/mcp",
        "protocol": "legacy",
        "oauth": false,
        "headers": {
          "Authorization": "Bearer {env:SIIGO_MCP_HTTP_TOKEN}"
        }
      }
    }
  }
}
```

Comprobar con `opencode mcp list` o `/mcps`. Para conectarse desde otro equipo de una red privada, usar una URL HTTPS interna y autorizar su Host en el servidor. Mantener el endpoint fuera del acceso público a Internet. No ejecutar `opencode mcp auth siigo` para este ejemplo: utiliza bearer estático, no OAuth. Ambos ejemplos se conservan como archivos en `examples/`.

## Herramientas

El [inventario](docs/tools.md) lista las herramientas de los 14 grupos: productos, terceros, facturas, cotizaciones, notas crédito, compras, comprobantes contables, comprobantes de pago, documentos soporte, recibos de caja, categorías de inventario, catálogos, reportes y webhooks.

El registro parte de una lista explícita de interfaces del SDK. Excluye autenticación, transporte HTTP arbitrario y sobrecargas de conveniencia duplicadas. Cada herramienta publica su esquema JSON con parámetros propios, modelos anidados y nombres de campo Siigo en `snake_case`.

Ejemplos de argumentos de herramientas —el host MCP envía estas llamadas; no son comandos del shell—:

**Compras por página:** `siigo_get_purchases`

```json
{"filter":{"page":1,"page_size":25}}
```

**Compras de agosto de 2026:** `siigo_get_purchases_by_date`

```json
{"from":"2026-08-01","to":"2026-08-31","filter":{"page_size":100}}
```

**Comprobantes de agosto de 2026:** `siigo_get_journals_by_date`

```json
{"from":"2026-08-01","to":"2026-08-31","filter":{"page_size":100}}
```

Estas dos consultas recorren todas las páginas y filtran localmente la fecha contable inclusiva con el SDK. No se limitan a la primera página. Si falta una página o los resultados son inconsistentes, devuelven un error. Siigo ha ignorado filtros remotos de fecha y `document_id` en journals: no interpretar una respuesta como prueba de que esos filtros se aplicaron. Para fechas usar las herramientas `*_by_date`; verificar el tipo de documento en los resultados si se necesita restringirlo.

**Crear un comprobante contable:** `siigo_create_journal`

```json
{
  "idempotency_key":"EjemploComprobanteAgo2026",
  "request":{
    "document":{"id":123},
    "date":"2026-08-31",
    "items":[
      {"account":{"code":"530505","movement":"Debit"},"value":1000.00},
      {"account":{"code":"111005","movement":"Credit"},"value":1000.00}
    ]
  }
}
```

Ese payload es ilustrativo: obtener los IDs, cuentas, terceros y demás campos requeridos de la empresa antes de escribir. El servidor no los deduce ni valida reglas contables de Siigo; Siigo valida su contrato. Los importes se manejan con `BigDecimal` y las fechas contables con `LocalDate`. Los modelos admiten extensiones del proveedor tal como lo hace el SDK.

Las respuestas incluyen JSON en `content` y en `structuredContent.data`. Los fallos usan `isError=true`; conservan el estado HTTP cuando existe y ocultan credenciales. Ante una escritura con resultado incierto, comprobar el documento en Siigo antes de reintentar; no generar otra clave de idempotencia para repetirla a ciegas. Los errores nunca se convierten en resultados vacíos exitosos.

## Alcance y ejecución de escrituras

Las escrituras están disponibles por defecto, con anotaciones MCP de lectura, destrucción e idempotencia. El host debe recabar la intención/aprobación del usuario para las operaciones que cambian Siigo; las anotaciones son información para el host, no una barrera de autorización independiente. El servidor no añade un segundo ciclo de confirmación en cada método.

Para exponer solo consultas, definir `SIIGO_MCP_READ_ONLY=true`: las herramientas de escritura ni siquiera aparecen. Para reducir el catálogo, `SIIGO_MCP_TOOL_GROUPS=purchases,journals,catalogs`. Los grupos son los nombres del inventario, separados por comas. Los desconocidos impiden iniciar el servidor.

Otras opciones: `SIIGO_API_URL` cambia la URL del SDK (HTTPS, o HTTP en loopback para pruebas); `SIIGO_API_TIMEOUT_SECONDS=120` configura espera de lectura/escritura; `SIIGO_MCP_REQUEST_TIMEOUT_SECONDS=600` limita la operación MCP. Para grandes volúmenes preferir consultas por página. El MCP no trunca respuestas silenciosamente; el SDK conserva sus controles de paginación. Las respuestas completas pueden ser grandes, especialmente PDF/XML o `get_all_*`.

## Desarrollo y pruebas

```sh
./mvnw clean verify
```

Las pruebas no usan credenciales reales ni requieren una cuenta Siigo. Cubren contratos de herramientas, configuración por entorno, autenticación y renovación a través del SDK, paginación y período contable, creación de journals, precisión decimal, errores sin secretos, ausencia de reintentos de escrituras, y conexiones MCP reales contra el JAR empaquetado (STDIO) y el servidor HTTP local.

```text
src/main/java/co/gldconsulting/siigo/mcp/
  SiigoMcpServer.java          # Startup, SDK y transportes
  config/                     # Environment variables
  tool/                       # Registro, esquemas, argumentos y resultados
  transport/                  # Acceso HTTP al MCP
src/test/java/                 # Pruebas unitarias y de integración
```

El workflow de GitHub Actions compila y prueba con Java 21 y conserva el JAR. No despliega, no crea releases ni publica en Maven Central.

## Referencias

- [SDK de Siigo de GLD Consulting](https://github.com/gldconsulting/siigo-api-sdk-java).
- [SDK oficial de MCP Java: servidor y transportes](https://java.sdk.modelcontextprotocol.io/latest/server/).
- [Dependencias oficiales MCP Java](https://java.sdk.modelcontextprotocol.io/latest/quickstart/).
- [Documentación oficial de Siigo](https://siigoapi.docs.apiary.io/).

Código abierto bajo [licencia MIT](LICENSE). Contribuciones e incidencias: [GitHub](https://github.com/gldconsulting/siigo-mcp-server-java).
