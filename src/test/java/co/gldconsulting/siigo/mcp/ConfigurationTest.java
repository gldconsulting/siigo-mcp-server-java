package co.gldconsulting.siigo.mcp;

import co.gldconsulting.siigo.mcp.config.ServerConfiguration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationTest {
    @Test void requiresEnvironmentCredentialsAndNeverAcceptsOnlyAnAccessToken() {
        assertThrows(IllegalArgumentException.class,() -> new ServerConfiguration(Map.of()));
        assertThrows(IllegalArgumentException.class,() -> new ServerConfiguration(Map.of("SIIGO_PARTNER_ID","fixture","SIIGO_ACCESS_TOKEN","fixture-only")));
        var env=TestFixtures.environment("http://127.0.0.1:1/");
        for (String key:java.util.List.of("SIIGO_PARTNER_ID","SIIGO_USERNAME","SIIGO_ACCESS_KEY")) {
            var invalid=new java.util.HashMap<>(env); invalid.remove(key); assertThrows(IllegalArgumentException.class,() -> new ServerConfiguration(invalid));
        }
        var config=new ServerConfiguration(env);
        assertEquals("stdio",config.transport());
        env.values().forEach(value -> assertFalse(config.toString().contains(value)));
    }
    @Test void requiresIndependentHttpBearerAndRejectsInvalidOptions() {
        var env=TestFixtures.environment("http://127.0.0.1:1/"); env.put("SIIGO_MCP_TRANSPORT","http");
        assertThrows(IllegalArgumentException.class,() -> new ServerConfiguration(env));
        env.put("SIIGO_MCP_HTTP_TOKEN","short"); assertThrows(IllegalArgumentException.class,() -> new ServerConfiguration(env));
        env.put("SIIGO_MCP_HTTP_TOKEN","fixture-http-bearer-at-least-32-characters");
        assertEquals("127.0.0.1",new ServerConfiguration(env).host());
        env.put("SIIGO_MCP_READ_ONLY","yes"); assertThrows(IllegalArgumentException.class,() -> new ServerConfiguration(env));
        env.remove("SIIGO_MCP_READ_ONLY"); env.put("SIIGO_MCP_PORT","65536"); assertThrows(IllegalArgumentException.class,() -> new ServerConfiguration(env));
    }
}
