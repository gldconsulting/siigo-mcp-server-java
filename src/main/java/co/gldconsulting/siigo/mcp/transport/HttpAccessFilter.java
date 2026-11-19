package co.gldconsulting.siigo.mcp.transport;

import co.gldconsulting.siigo.mcp.config.ServerConfiguration;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;

/** One deployment serves one company; this bearer is independent of Siigo's access token. */
public final class HttpAccessFilter implements Filter {
    private final ServerConfiguration configuration;
    public HttpAccessFilter(ServerConfiguration configuration) { this.configuration=configuration; }
    @Override public void doFilter(ServletRequest request,ServletResponse response,FilterChain chain) throws IOException,ServletException {
        var req=(HttpServletRequest)request; var res=(HttpServletResponse)response;
        String host=req.getHeader("Host");
        try {
            URI authority=host==null ? null : URI.create("http://"+host);
            if (authority==null || authority.getUserInfo()!=null || !authority.getRawPath().isEmpty() || authority.getRawQuery()!=null || authority.getRawFragment()!=null || !configuration.allowedHosts().contains(authority.getHost())) { res.setStatus(403); return; }
        }
        catch (IllegalArgumentException exception) { res.setStatus(403); return; }
        String origin=req.getHeader("Origin");
        if (origin!=null && !configuration.allowedOrigins().contains(origin)) { res.setStatus(403); return; }
        String authorization=req.getHeader("Authorization");
        if (Collections.list(req.getHeaders("Authorization")).size()!=1 || authorization==null || !MessageDigest.isEqual(authorization.getBytes(StandardCharsets.UTF_8),("Bearer "+configuration.httpToken()).getBytes(StandardCharsets.UTF_8))) {
            res.setHeader("WWW-Authenticate","Bearer realm=\"siigo-mcp\""); res.setStatus(401); return;
        }
        res.setHeader("Cache-Control","no-store");
        // MCP/Reactor belongs to the application, not to Tomcat's disposable webapp loader.
        // Worker threads created during this request must not retain that loader after shutdown.
        Thread thread=Thread.currentThread();
        ClassLoader previous=thread.getContextClassLoader();
        thread.setContextClassLoader(HttpAccessFilter.class.getClassLoader());
        try { chain.doFilter(request,response); }
        finally { thread.setContextClassLoader(previous); }
    }
}
