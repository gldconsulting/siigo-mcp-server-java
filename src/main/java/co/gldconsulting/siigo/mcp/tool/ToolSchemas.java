package co.gldconsulting.siigo.mcp.tool;

import co.gldconsulting.siigo.model.ApiModel;
import co.gldconsulting.siigo.service.QueryFilter;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Schemas are derived from the pinned SDK contracts, preserving nested models and Siigo field names. */
public final class ToolSchemas {
    private final ObjectMapper mapper;
    public ToolSchemas(ObjectMapper mapper) { this.mapper=mapper; }
    public Map<String,Object> forMethod(Method method) {
        Map<String,Object> definitions=new TreeMap<>();
        Map<String,Object> properties=new LinkedHashMap<>();
        List<String> required=new ArrayList<>();
        for (Parameter parameter:method.getParameters()) {
            if (!parameter.isNamePresent()) throw new IllegalStateException("SDK parameter names are missing");
            String name=JsonSupport.snake(parameter.getName());
            Map<String,Object> schema=type(mapper.constructType(parameter.getParameterizedType()), definitions);
            if (name.equals("idempotency_key")) { schema=new LinkedHashMap<>(schema); schema.put("pattern", "^[A-Za-z0-9]{1,30}$"); schema.put("description", "Optional Siigo idempotency key. Reuse the same key when checking an uncertain write; never create a new key to retry blindly."); }
            properties.put(name, schema);
            if (!QueryFilter.class.isAssignableFrom(parameter.getType()) && !name.equals("idempotency_key")) required.add(name);
        }
        Map<String,Object> root=new LinkedHashMap<>(Map.of("type","object","properties",properties,"required",required,"additionalProperties",false));
        if (!definitions.isEmpty()) root.put("$defs", definitions);
        return root;
    }
    private Map<String,Object> type(JavaType type, Map<String,Object> definitions) {
        Class<?> raw=type.getRawClass();
        if (raw == LocalDate.class) return Map.of("type","string","format","date");
        if (raw.isEnum()) return Map.of("type","string","enum",List.of(raw.getEnumConstants()).stream().map(Object::toString).toList());
        if (raw == String.class || raw == Character.class) return Map.of("type","string");
        if (raw == Boolean.class || raw == boolean.class) return Map.of("type","boolean");
        if (raw == Integer.class || raw == Long.class || raw == int.class || raw == long.class) return Map.of("type","integer");
        if (Number.class.isAssignableFrom(raw)) return Map.of("type","number");
        if (type.isCollectionLikeType() || type.isArrayType()) return Map.of("type","array","items",type(type.getContentType(),definitions));
        if (type.isMapLikeType()) return Map.of("type","object","additionalProperties",type(type.getContentType(),definitions));
        if (JsonNode.class.isAssignableFrom(raw) || raw == Object.class) return Map.of();
        String name=raw.getSimpleName();
        if (!definitions.containsKey(name)) {
            Map<String,Object> definition=new LinkedHashMap<>();
            definitions.put(name,definition);
            Map<String,Object> properties=new TreeMap<>();
            if (QueryFilter.class.isAssignableFrom(raw)) {
                filterSetters(raw).forEach((property,setter) -> {
                    Map<String,Object> propertySchema=new LinkedHashMap<>(type(mapper.constructType(setter.getGenericParameterTypes()[0]),definitions));
                    if (property.equals("page")) propertySchema.put("minimum",1);
                    if (property.equals("page_size")) { propertySchema.put("minimum",1); propertySchema.put("maximum",100); }
                    properties.put(property,propertySchema);
                });
            } else {
                for (var property:mapper.getSerializationConfig().introspect(type).findProperties()) {
                    if (!property.getName().equals("additional_properties") && property.couldSerialize()) properties.put(property.getName(),type(property.getPrimaryType(),definitions));
                }
            }
            definition.put("type","object"); definition.put("properties",properties);
            // ApiModel intentionally preserves vendor extensions; filters must stay endpoint-specific.
            definition.put("additionalProperties",ApiModel.class.isAssignableFrom(raw));
        }
        return Map.of("$ref","#/$defs/"+name);
    }
    public static Map<String,Method> filterSetters(Class<?> raw) {
        Map<String,Method> result=new TreeMap<>();
        for (Method getter:raw.getMethods()) {
            if (!getter.getName().startsWith("get") || getter.getParameterCount()!=0 || getter.getName().equals("getClass")) continue;
            try { result.put(JsonSupport.snake(getter.getName().substring(3)),raw.getMethod("set"+getter.getName().substring(3),getter.getReturnType())); }
            catch (NoSuchMethodException ignored) { }
        }
        return result;
    }
}
