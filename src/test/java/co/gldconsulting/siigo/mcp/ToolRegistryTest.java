package co.gldconsulting.siigo.mcp;

import co.gldconsulting.siigo.mcp.config.ServerConfiguration;
import co.gldconsulting.siigo.mcp.tool.JsonSupport;
import co.gldconsulting.siigo.mcp.tool.SiigoToolRegistry;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {
    @Test void listsEveryExplicitSdkServiceOperationWithEndpointSpecificSchemas() throws Exception {
        var config=TestFixtures.config("http://127.0.0.1:1/");
        try(var api=config.createApi()) {
            var registry=new SiigoToolRegistry(api,config);
            assertTrue(registry.specifications().size()>70);
            var names=registry.specifications().stream().map(spec -> spec.tool().name()).toList();
            assertEquals(names.size(),new java.util.HashSet<>(names).size());
            try(var input=getClass().getResourceAsStream("/expected-tools.json")) {
                var expected=JsonSupport.mapper().readTree(input);
                var documented=new java.util.TreeSet<String>();
                expected.elements().forEachRemaining(group -> group.forEach(name -> documented.add(name.asText())));
                assertEquals(documented,new java.util.TreeSet<>(names),"Published inventory must match tools/list");
            }
            assertFalse(names.stream().anyMatch(name -> name.contains("auth") || name.contains("token") || name.contains("endpoint")));
            var mapper=JsonSupport.mapper();
            var purchase=mapper.valueToTree(TestFixtures.tool(registry,"siigo_get_purchases").tool().inputSchema());
            assertTrue(purchase.path("$defs").path("PurchasesFilter").path("properties").has("page_size"));
            assertFalse(purchase.path("$defs").path("PurchasesFilter").path("properties").has("date_start"));
            var journal=mapper.valueToTree(TestFixtures.tool(registry,"siigo_create_journal").tool().inputSchema());
            assertEquals("number",journal.at("/$defs/JournalRequestItem/properties/value/type").asText());
            assertTrue(journal.path("properties").has("idempotency_key"));
            assertTrue(journal.path("$defs").path("JournalRequest").path("properties").has("date"));
            for (var spec:registry.specifications()) assertFalse(mapper.writeValueAsString(spec.tool()).contains("fixture-access-key"));
            assertFalse(TestFixtures.tool(registry,"siigo_create_journal").tool().annotations().readOnlyHint());
            assertTrue(TestFixtures.tool(registry,"siigo_delete_purchase").tool().annotations().destructiveHint());
        }
    }
    @Test void readOnlyAndGroupsActuallyRemoveTools() {
        var env=TestFixtures.environment("http://127.0.0.1:1/"); env.put("SIIGO_MCP_READ_ONLY","true"); env.put("SIIGO_MCP_TOOL_GROUPS","purchases,journals");
        var config=new ServerConfiguration(env);
        try(var api=config.createApi()) {
            var registry=new SiigoToolRegistry(api,config);
            assertTrue(registry.specifications().stream().allMatch(spec -> spec.tool().annotations().readOnlyHint()));
            assertTrue(registry.specifications().stream().noneMatch(spec -> spec.tool().name().contains("invoice")));
            assertThrows(java.util.NoSuchElementException.class,() -> TestFixtures.tool(registry,"siigo_create_journal"));
        }
        env.put("SIIGO_MCP_TOOL_GROUPS","authentication"); var invalid=new ServerConfiguration(env);
        try(var api=invalid.createApi()) { assertThrows(IllegalArgumentException.class,() -> new SiigoToolRegistry(api,invalid)); }
    }
    @Test void rejectsUnknownParametersAndBadPaginationBeforeAnyHttpCall() throws Exception {
        try(var mock=new MockSiigoServer()) {
            var config=TestFixtures.config(mock.url());
            try(var api=config.createApi()) {
                var registry=new SiigoToolRegistry(api,config);
                for(var args:java.util.List.of(Map.<String,Object>of("filter",Map.of("date_start","2026-08-01")),Map.<String,Object>of("access_key","do-not-accept"),Map.<String,Object>of("filter",Map.of("page_size",101)),Map.<String,Object>of("filter",Map.of("page",1.5)))) {
                    assertTrue(TestFixtures.call(registry,"siigo_get_purchases",args).isError());
                }
                assertTrue(TestFixtures.call(registry,"siigo_get_purchases_by_date",Map.of("from","2026-09-01","to","2026-08-01")).isError());
                assertTrue(mock.requests.isEmpty());
            }
        }
    }
}
