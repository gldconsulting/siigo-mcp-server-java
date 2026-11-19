package co.gldconsulting.siigo.mcp.tool;

import co.gldconsulting.siigo.service.SiigoApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;

/** Never logs request bodies, authorization headers, environment values or raw stack traces. */
public final class ToolResults {
    private final ObjectMapper mapper;
    private final List<String> secrets;
    public ToolResults(ObjectMapper mapper,List<String> secrets) { this.mapper=mapper; this.secrets=List.copyOf(secrets); }
    public CallToolResult success(Object value) {
        ObjectNode result=mapper.createObjectNode(); result.set("data",redact(mapper.valueToTree(value)));
        return render(result,false);
    }
    public CallToolResult error(Throwable failure,boolean readOnly) {
        while (failure instanceof InvocationTargetException && failure.getCause()!=null) failure=failure.getCause();
        ObjectNode result=mapper.createObjectNode();
        if (failure instanceof SiigoApiException api) {
            result.put("code","SIIGO_API_ERROR"); result.put("http_status",api.getStatusCode());
            result.put("message",clean(api.getMessage()));
            result.put("outcome",!readOnly && api.getStatusCode()==0 ? "unknown" : "failed");
            if (api.getError().getDetails()!=null) result.set("details",redact(api.getError().getDetails()));
        } else if (failure instanceof IllegalArgumentException) {
            result.put("code","INVALID_ARGUMENTS"); result.put("message","Invalid arguments. Check the tool input schema, required values, date range and endpoint-specific filters.");
        } else {
            result.put("code","OPERATION_FAILED"); result.put("message","The operation did not return a complete verified result.");
            if (!readOnly) result.put("outcome","unknown");
        }
        if (!readOnly) result.put("retry_guidance","Do not repeat a write automatically. Verify the document in Siigo before retrying; preserve any idempotency key.");
        return render(result,true);
    }
    private CallToolResult render(JsonNode value,boolean error) {
        try { return CallToolResult.builder().structuredContent(value).addTextContent(mapper.writeValueAsString(value)).isError(error).build(); }
        catch (com.fasterxml.jackson.core.JsonProcessingException failure) { throw new IllegalStateException("Could not encode tool result"); }
    }
    private String clean(String value) {
        if (value==null) return "Siigo request failed";
        String result=value;
        for (String secret:secrets) result=result.replace(secret,"[REDACTED]");
        return result;
    }
    private JsonNode redact(JsonNode node) {
        if (node.isTextual()) return mapper.getNodeFactory().textNode(clean(node.textValue()));
        if (node.isObject()) {
            ObjectNode result=mapper.createObjectNode();
            node.fields().forEachRemaining(entry -> result.set(entry.getKey(),entry.getKey().toLowerCase().matches(".*(token|secret|password|access_key|authorization|username|partner_id).*") ? mapper.getNodeFactory().textNode("[REDACTED]") : redact(entry.getValue())));
            return result;
        }
        if (node.isArray()) { var result=mapper.createArrayNode(); node.forEach(value -> result.add(redact(value))); return result; }
        return node;
    }
}
