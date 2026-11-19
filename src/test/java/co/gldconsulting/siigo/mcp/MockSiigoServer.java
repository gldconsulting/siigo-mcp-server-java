package co.gldconsulting.siigo.mcp;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

final class MockSiigoServer implements AutoCloseable {
    record Request(String method,String path,String query,Map<String,List<String>> headers,String body) {
        String header(String name) { return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name)).map(e -> e.getValue().getFirst()).findFirst().orElse(null); }
    }
    record Reply(int status,String body) { }
    private final HttpServer server;
    private final java.util.concurrent.ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor();
    private final LinkedBlockingQueue<Reply> replies=new LinkedBlockingQueue<>();
    final List<Request> requests=new CopyOnWriteArrayList<>();
    MockSiigoServer() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0); server.setExecutor(executor);
        server.createContext("/", exchange -> {
            try {
                requests.add(new Request(exchange.getRequestMethod(),exchange.getRequestURI().getPath(),exchange.getRequestURI().getRawQuery(),Map.copyOf(exchange.getRequestHeaders()),new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)));
                Reply reply=replies.poll(); if(reply==null) reply=new Reply(500,"{\"error\":\"unexpected mock request\"}");
                byte[] bytes=reply.body().getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().set("Content-Type","application/json"); exchange.sendResponseHeaders(reply.status(),bytes.length); exchange.getResponseBody().write(bytes);
            } finally { exchange.close(); }
        }); server.start();
    }
    String url() { return "http://127.0.0.1:"+server.getAddress().getPort()+"/"; }
    void reply(int status,String body) { replies.add(new Reply(status,body)); }
    void auth(String token) { reply(200,"{\"access_token\":\""+token+"\",\"expires_in\":3600,\"token_type\":\"Bearer\"}"); }
    static String page(int page,int size,int total,String... rows) { return "{\"pagination\":{\"page\":"+page+",\"page_size\":"+size+",\"total_results\":"+total+"},\"results\":["+String.join(",",rows)+"]}"; }
    static String row(String id,String date) { return "{\"id\":\""+id+"\",\"date\":\""+date+"\"}"; }
    @Override public void close() { server.stop(0); executor.shutdownNow(); }
}
