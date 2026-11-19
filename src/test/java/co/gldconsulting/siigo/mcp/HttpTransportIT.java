package co.gldconsulting.siigo.mcp;

import co.gldconsulting.siigo.mcp.config.ServerConfiguration;
import co.gldconsulting.siigo.mcp.tool.JsonSupport;
import co.gldconsulting.siigo.mcp.tool.SiigoToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class HttpTransportIT {
    private static final String BEARER="fixture-mcp-http-bearer-at-least-32-characters";
    @Test void streamableHttpAuthenticatesAndServesMcpHandshakeAndTools() throws Exception {
        try(var mock=new MockSiigoServer()) {
            var env=TestFixtures.environment(mock.url()); env.put("SIIGO_MCP_TRANSPORT","http"); env.put("SIIGO_MCP_PORT","0"); env.put("SIIGO_MCP_HTTP_TOKEN",BEARER);
            var config=new ServerConfiguration(env);
            try(var api=config.createApi(); var server=SiigoMcpServer.startHttp(config,new SiigoToolRegistry(api,config).specifications()); var client=HttpClient.newHttpClient()) {
                URI uri=URI.create("http://127.0.0.1:"+server.port()+"/mcp");
                var denied=client.send(HttpRequest.newBuilder(uri).GET().build(),HttpResponse.BodyHandlers.ofString()); assertEquals(401,denied.statusCode());
                var origin=client.send(HttpRequest.newBuilder(uri).header("Authorization","Bearer "+BEARER).header("Origin","https://untrusted.invalid").GET().build(),HttpResponse.BodyHandlers.ofString()); assertEquals(403,origin.statusCode());
                var initialized=post(client,uri,null,Map.of("jsonrpc","2.0","id",1,"method","initialize","params",Map.of("protocolVersion","2025-11-25","capabilities",Map.of(),"clientInfo",Map.of("name","fixture-http-client","version","1.0"))));
                assertEquals(200,initialized.statusCode(),initialized.body()); String session=initialized.headers().firstValue("mcp-session-id").orElseThrow();
                assertTrue(json(initialized.body()).path("result").path("capabilities").has("tools"));
                post(client,uri,session,Map.of("jsonrpc","2.0","method","notifications/initialized","params",Map.of()));
                var listed=post(client,uri,session,Map.of("jsonrpc","2.0","id",2,"method","tools/list","params",Map.of()));
                assertEquals(200,listed.statusCode(),listed.body()); assertTrue(json(listed.body()).path("result").path("tools").size()>70,listed.body());
                var invalid=post(client,uri,session,Map.of("jsonrpc","2.0","id",20,"method","tools/call","params",Map.of("name","siigo_get_purchases","arguments",Map.of("filter",Map.of("page_size",101)))));
                assertTrue(json(invalid.body()).has("error") || json(invalid.body()).path("result").path("isError").asBoolean(),invalid.body());
                assertTrue(mock.requests.isEmpty(),"Schema validation must happen before Siigo authentication or HTTP");
                mock.auth("fixture-token"); mock.reply(200,"[]");
                var called=post(client,uri,session,Map.of("jsonrpc","2.0","id",3,"method","tools/call","params",Map.of("name","siigo_get_taxes","arguments",Map.of())));
                assertFalse(json(called.body()).path("result").path("isError").asBoolean(true),called.body());
                assertEquals(2,mock.requests.size());
            }
            assertTrue(Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.isAlive() && thread.getName().startsWith("boundedElastic"))
                .noneMatch(thread -> thread.getContextClassLoader()!=null && thread.getContextClassLoader().getClass().getName().contains("WebappClassLoader")),
                "Shared MCP workers must not retain a stopped Tomcat webapp classloader");
        }
    }
    private static HttpResponse<String> post(HttpClient client,URI uri,String session,Object body) throws Exception {
        var builder=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).header("Authorization","Bearer "+BEARER).header("Content-Type","application/json").header("Accept","application/json, text/event-stream").header("MCP-Protocol-Version","2025-11-25");
        if(session!=null) builder.header("Mcp-Session-Id",session);
        return client.send(builder.POST(HttpRequest.BodyPublishers.ofString(JsonSupport.mapper().writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
    private static JsonNode json(String body) throws Exception {
        String value=body.lines().filter(line -> line.startsWith("data:")).map(line -> line.substring(5).trim()).findFirst().orElse(body);
        return JsonSupport.mapper().readTree(value);
    }
}
