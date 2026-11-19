package co.gldconsulting.siigo.mcp.config;

import co.gldconsulting.siigo.CommunicationConfig;
import co.gldconsulting.siigo.SiigoApi;
import co.gldconsulting.siigo.model.AuthenticationCredentials;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Configuration is read from the process environment only. Never includes secrets in toString(). */
public final class ServerConfiguration {
    private final Map<String, String> environment;
    public ServerConfiguration(Map<String, String> environment) { this.environment = Map.copyOf(environment); validate(); }
    public static ServerConfiguration fromEnvironment() { return new ServerConfiguration(System.getenv()); }
    public String transport() { return value("SIIGO_MCP_TRANSPORT", "stdio"); }
    public String host() { return value("SIIGO_MCP_HOST", "127.0.0.1"); }
    public int port() { return integer("SIIGO_MCP_PORT", 8080, 0, 65535); }
    public String httpToken() { return value("SIIGO_MCP_HTTP_TOKEN", ""); }
    public boolean readOnly() { return bool("SIIGO_MCP_READ_ONLY", false); }
    public Duration requestTimeout() { return Duration.ofSeconds(integer("SIIGO_MCP_REQUEST_TIMEOUT_SECONDS", 600, 1, 3600)); }
    public Set<String> toolGroups() { return csv("SIIGO_MCP_TOOL_GROUPS"); }
    public Set<String> allowedOrigins() { return csv("SIIGO_MCP_ALLOWED_ORIGINS"); }
    public Set<String> allowedHosts() {
        Set<String> configured = csv("SIIGO_MCP_ALLOWED_HOSTS");
        return configured.isEmpty() ? Set.of("localhost", "127.0.0.1", "[::1]") : configured;
    }
    public List<String> secrets() {
        return Arrays.stream(new String[]{"SIIGO_PARTNER_ID", "SIIGO_USERNAME", "SIIGO_ACCESS_KEY", "SIIGO_MCP_HTTP_TOKEN"})
            .map(key -> value(key, "")).filter(value -> !value.isBlank()).toList();
    }
    public SiigoApi createApi() {
        var communication = CommunicationConfig.builder().setPartnerId(required("SIIGO_PARTNER_ID"))
            .setReadTimeout(Duration.ofSeconds(integer("SIIGO_API_TIMEOUT_SECONDS", 120, 1, 3600)))
            .setWriteTimeout(Duration.ofSeconds(integer("SIIGO_API_TIMEOUT_SECONDS", 120, 1, 3600)))
            .setUserAgent("GLD Siigo MCP Server/1.0.0");
        if (!value("SIIGO_API_URL", "").isEmpty()) communication.setApiUrl(value("SIIGO_API_URL", ""));
        var builder = SiigoApi.builder().withCommunicationConfig(communication);
        builder.setCredentials(new AuthenticationCredentials().setUsername(required("SIIGO_USERNAME")).setAccessKey(required("SIIGO_ACCESS_KEY")));
        return builder.build();
    }
    private void validate() {
        required("SIIGO_PARTNER_ID");
        required("SIIGO_USERNAME"); required("SIIGO_ACCESS_KEY");
        if (!Set.of("stdio", "http").contains(transport())) throw new IllegalArgumentException("SIIGO_MCP_TRANSPORT must be stdio or http");
        if (transport().equals("http")) { required("SIIGO_MCP_HTTP_TOKEN"); if (httpToken().length() < 32) throw new IllegalArgumentException("SIIGO_MCP_HTTP_TOKEN must contain at least 32 characters"); }
        readOnly(); port(); requestTimeout();
    }
    private String value(String key, String fallback) { String result=environment.get(key); return result == null || result.isBlank() ? fallback : result; }
    private String required(String key) { String result=value(key, ""); if (result.isBlank()) throw new IllegalArgumentException("Missing environment variable: " + key); return result; }
    private Set<String> csv(String key) { return Arrays.stream(value(key, "").split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet()); }
    private boolean bool(String key, boolean fallback) { String result=value(key, Boolean.toString(fallback)); if (!result.equals("true") && !result.equals("false")) throw new IllegalArgumentException(key + " must be true or false"); return Boolean.parseBoolean(result); }
    private int integer(String key, int fallback, int min, int max) {
        try { int result=Integer.parseInt(value(key, Integer.toString(fallback))); if (result >= min && result <= max) return result; }
        catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
    }
    @Override public String toString() { return "ServerConfiguration[transport=" + transport() + ", readOnly=" + readOnly() + "]"; }
}
