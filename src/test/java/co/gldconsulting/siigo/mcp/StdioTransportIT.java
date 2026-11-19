package co.gldconsulting.siigo.mcp;

import co.gldconsulting.siigo.mcp.tool.JsonSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class StdioTransportIT {
    private final ObjectMapper mapper=JsonSupport.mapper();
    @Test void packagedJarSupportsInitializeDiscoveryAndToolCallsWithCleanStdout() throws Exception {
        try(var mock=new MockSiigoServer()) {
            mock.auth("fixture-token"); mock.reply(200,"[]");
            ProcessBuilder builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-jar",System.getProperty("mcp.test.jar"));
            builder.environment().keySet().removeIf(key -> key.startsWith("SIIGO_"));
            builder.environment().putAll(TestFixtures.environment(mock.url()));
            Process process=builder.start();
            try(var executor=Executors.newVirtualThreadPerTaskExecutor(); var reader=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8)); var writer=new BufferedWriter(new OutputStreamWriter(process.getOutputStream(),StandardCharsets.UTF_8))) {
                send(writer,Map.of("jsonrpc","2.0","id",1,"method","initialize","params",Map.of("protocolVersion","2025-11-25","capabilities",Map.of(),"clientInfo",Map.of("name","fixture-client","version","1.0"))));
                JsonNode initialized=read(reader,executor); assertTrue(initialized.path("result").path("capabilities").has("tools"),initialized.toString());
                send(writer,Map.of("jsonrpc","2.0","method","notifications/initialized","params",Map.of()));
                send(writer,Map.of("jsonrpc","2.0","id",2,"method","tools/list","params",Map.of()));
                JsonNode listed=read(reader,executor); assertTrue(listed.path("result").path("tools").size()>70,listed.toString());
                assertFalse(listed.toString().contains("fixture-access-key"));
                send(writer,Map.of("jsonrpc","2.0","id",3,"method","tools/call","params",Map.of("name","siigo_get_taxes","arguments",Map.of())));
                JsonNode called=read(reader,executor); assertFalse(called.path("result").path("isError").asBoolean(true),called.toString());
                assertTrue(called.path("result").path("structuredContent").path("data").isArray(),called.toString());
                writer.close(); assertTrue(process.waitFor(10,TimeUnit.SECONDS),"Process should exit after stdin EOF"); assertEquals(0,process.exitValue());
                assertFalse(new String(process.getErrorStream().readAllBytes(),StandardCharsets.UTF_8).contains("fixture-access-key"));
            } finally { process.destroyForcibly(); }
        }
    }
    private void send(BufferedWriter writer,Object value) throws Exception { writer.write(mapper.writeValueAsString(value)); writer.newLine(); writer.flush(); }
    private JsonNode read(BufferedReader reader,java.util.concurrent.ExecutorService executor) throws Exception { String line=executor.submit(reader::readLine).get(30,TimeUnit.SECONDS); assertNotNull(line,"Expected a JSON-RPC response, not process exit"); return mapper.readTree(line); }
}
