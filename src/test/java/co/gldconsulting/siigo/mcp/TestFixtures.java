package co.gldconsulting.siigo.mcp;

import co.gldconsulting.siigo.mcp.config.ServerConfiguration;
import co.gldconsulting.siigo.mcp.tool.SiigoToolRegistry;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.HashMap;
import java.util.Map;

final class TestFixtures {
    static Map<String,String> environment(String url) {
        return new HashMap<>(Map.of("SIIGO_PARTNER_ID","fixture-partner","SIIGO_USERNAME","fixture@example.invalid","SIIGO_ACCESS_KEY","fixture-access-key","SIIGO_API_URL",url));
    }
    static ServerConfiguration config(String url) { return new ServerConfiguration(environment(url)); }
    static SyncToolSpecification tool(SiigoToolRegistry registry,String name) { return registry.specifications().stream().filter(spec -> spec.tool().name().equals(name)).findFirst().orElseThrow(); }
    static CallToolResult call(SiigoToolRegistry registry,String name,Map<String,Object> args) { return tool(registry,name).callHandler().apply(null,CallToolRequest.builder(name).arguments(args).build()); }
}
