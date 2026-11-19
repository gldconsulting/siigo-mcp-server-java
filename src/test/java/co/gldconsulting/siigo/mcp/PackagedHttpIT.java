package co.gldconsulting.siigo.mcp;

import org.junit.jupiter.api.Test;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class PackagedHttpIT {
    @Test void executableHttpServerRemainsRunningUntilStopped() throws Exception {
        int port;
        try (var reservation=new ServerSocket(0)) { port=reservation.getLocalPort(); }
        var builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-jar",System.getProperty("mcp.test.jar"));
        builder.environment().keySet().removeIf(key -> key.startsWith("SIIGO_"));
        builder.environment().putAll(TestFixtures.environment("http://127.0.0.1:1/"));
        builder.environment().put("SIIGO_MCP_TRANSPORT","http");
        builder.environment().put("SIIGO_MCP_PORT",Integer.toString(port));
        builder.environment().put("SIIGO_MCP_HTTP_TOKEN","fixture-packaged-http-bearer-at-least-32");
        var process=builder.start();
        try (var client=HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build()) {
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/mcp")).timeout(Duration.ofSeconds(2)).GET().build();
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15); boolean ready=false;
            while (process.isAlive() && System.nanoTime()<deadline) {
                try { ready=client.send(request,HttpResponse.BodyHandlers.ofString()).statusCode()==401; if(ready) break; }
                catch(java.io.IOException ignored) { }
                Thread.sleep(30);
            }
            assertTrue(ready,"The packaged HTTP main must keep serving until shutdown");
            assertEquals(401,client.send(request,HttpResponse.BodyHandlers.ofString()).statusCode());
            assertTrue(process.isAlive());
        } finally {
            process.destroy();
            if(!process.waitFor(10,TimeUnit.SECONDS)) process.destroyForcibly();
        }
    }
}
