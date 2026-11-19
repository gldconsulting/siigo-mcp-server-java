package co.gldconsulting.siigo.mcp;

import co.gldconsulting.siigo.mcp.tool.JsonSupport;
import co.gldconsulting.siigo.mcp.tool.SiigoToolRegistry;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SiigoToolsIT {
    @Test void sdkAuthenticatesReusesAndRefreshesTokenWithoutMcpTokenManagement() throws Exception {
        try(var mock=new MockSiigoServer()) {
            mock.auth("fixture-token-one"); mock.reply(200,"[]"); mock.reply(401,"{\"error\":\"expired\"}"); mock.auth("fixture-token-two"); mock.reply(200,"[]");
            var config=TestFixtures.config(mock.url());
            try(var api=config.createApi()) {
                var registry=new SiigoToolRegistry(api,config);
                assertFalse(TestFixtures.call(registry,"siigo_get_taxes",Map.of()).isError());
                assertFalse(TestFixtures.call(registry,"siigo_get_taxes",Map.of()).isError());
            }
            assertEquals(List.of("/auth","/v1/taxes","/v1/taxes","/auth","/v1/taxes"),mock.requests.stream().map(MockSiigoServer.Request::path).toList());
            assertNull(mock.requests.getFirst().header("Authorization"));
            assertEquals("Bearer fixture-token-one",mock.requests.get(1).header("Authorization"));
            assertEquals("Bearer fixture-token-two",mock.requests.getLast().header("Authorization"));
            assertTrue(mock.requests.stream().allMatch(request -> "fixture-partner".equals(request.header("Partner-Id"))));
            assertTrue(mock.requests.getFirst().body().contains("fixture-access-key"));
        }
    }
    @Test void purchasesByDateTraversesEveryPageAndFiltersInclusiveAccountingDates() throws Exception {
        try(var mock=new MockSiigoServer()) {
            mock.auth("fixture-token");
            mock.reply(200,MockSiigoServer.page(1,2,4,MockSiigoServer.row("july","2026-07-31"),MockSiigoServer.row("first","2026-08-01")));
            mock.reply(200,MockSiigoServer.page(2,2,4,MockSiigoServer.row("last","2026-08-31"),MockSiigoServer.row("october","2026-10-06")));
            var config=TestFixtures.config(mock.url());
            try(var api=config.createApi()) {
                var result=TestFixtures.call(new SiigoToolRegistry(api,config),"siigo_get_purchases_by_date",Map.of("from","2026-08-01","to","2026-08-31","filter",Map.of("page_size",2)));
                assertFalse(result.isError()); var data=JsonSupport.mapper().valueToTree(result.structuredContent()).path("data");
                assertEquals(2,data.size()); assertEquals("first",data.get(0).path("id").asText()); assertEquals("last",data.get(1).path("id").asText());
            }
            assertEquals(3,mock.requests.size()); assertEquals("page=1&page_size=2",mock.requests.get(1).query()); assertEquals("page=2&page_size=2",mock.requests.get(2).query());
        }
    }
    @Test void createsJournalViaSdkPreservingDecimalPrecisionAndIdempotency() throws Exception {
        try(var mock=new MockSiigoServer()) {
            mock.auth("fixture-token"); mock.reply(201,"{\"id\":\"created-journal\",\"date\":\"2026-08-31\"}");
            var config=TestFixtures.config(mock.url());
            try(var api=config.createApi()) {
                var request=Map.of("date","2026-08-31","document",Map.of("id",123),"items",List.of(Map.of("account",Map.of("code","530505","movement","Debit"),"value",new BigDecimal("123.456789123456789"))));
                var result=TestFixtures.call(new SiigoToolRegistry(api,config),"siigo_create_journal",Map.of("request",request,"idempotency_key","FixtureJournalAug2026"));
                assertFalse(result.isError()); assertTrue(result.structuredContent().toString().contains("created-journal"));
            }
            assertEquals(2,mock.requests.size()); var write=mock.requests.getLast();
            assertEquals("POST",write.method()); assertEquals("/v1/journals",write.path());
            assertTrue(write.body().contains("123.456789123456789")); assertEquals("FixtureJournalAug2026",write.header("Idempotency-Key"));
        }
    }
    @Test void failedWriteIsNotRetriedAndApiErrorsRedactConfiguredSecrets() throws Exception {
        try(var mock=new MockSiigoServer()) {
            mock.auth("fixture-token"); mock.reply(429,"{\"message\":\"fixture-access-key\",\"access_token\":\"never-share\"}");
            var config=TestFixtures.config(mock.url());
            try(var api=config.createApi()) {
                var result=TestFixtures.call(new SiigoToolRegistry(api,config),"siigo_create_purchase",Map.of("request",Map.of("date","2026-08-31")));
                assertTrue(result.isError()); String encoded=JsonSupport.mapper().writeValueAsString(result);
                assertTrue(encoded.contains("429")); assertFalse(encoded.contains("fixture-access-key")); assertFalse(encoded.contains("never-share"));
            }
            assertEquals(2,mock.requests.size());
        }
    }
    @Test void incompletePaginationFailsInsteadOfReturningPartialOrEmptyData() throws Exception {
        try(var mock=new MockSiigoServer()) {
            mock.auth("fixture-token"); mock.reply(200,MockSiigoServer.page(1,1,2,MockSiigoServer.row("duplicate","2026-08-01"))); mock.reply(200,MockSiigoServer.page(2,1,2,MockSiigoServer.row("duplicate","2026-08-01")));
            var config=TestFixtures.config(mock.url());
            try(var api=config.createApi()) { assertTrue(TestFixtures.call(new SiigoToolRegistry(api,config),"siigo_get_all_purchases",Map.of("filter",Map.of("page_size",1))).isError()); }
        }
    }
}
