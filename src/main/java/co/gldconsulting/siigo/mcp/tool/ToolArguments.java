package co.gldconsulting.siigo.mcp.tool;

import co.gldconsulting.siigo.service.QueryFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;

public final class ToolArguments {
    private final ObjectMapper mapper;
    public ToolArguments(ObjectMapper mapper) { this.mapper=mapper; }
    public Object[] bind(Method method, Map<String,Object> supplied) throws ReflectiveOperationException {
        Map<String,Object> values=supplied == null ? Map.of() : supplied;
        Set<String> names=Arrays.stream(method.getParameters()).map(p -> JsonSupport.snake(p.getName())).collect(Collectors.toSet());
        if (!names.containsAll(values.keySet())) throw new IllegalArgumentException("Unknown tool argument");
        Parameter[] parameters=method.getParameters(); Object[] arguments=new Object[parameters.length];
        for (int i=0;i<parameters.length;i++) {
            Parameter parameter=parameters[i]; String name=JsonSupport.snake(parameter.getName()); Object value=values.get(name);
            if (QueryFilter.class.isAssignableFrom(parameter.getType())) {
                if (values.containsKey(name) && value == null) throw new IllegalArgumentException("filter must be an object");
                Object filter=parameter.getType().getConstructor().newInstance();
                if (value != null) {
                    if (!(value instanceof Map<?,?> fields)) throw new IllegalArgumentException("filter must be an object");
                    var setters=ToolSchemas.filterSetters(parameter.getType());
                    for (var entry:fields.entrySet()) {
                        Method setter=setters.get(entry.getKey());
                        if (setter == null) throw new IllegalArgumentException("Unknown filter for this endpoint");
                        if (entry.getValue() == null) throw new IllegalArgumentException("Filter values must not be null");
                        setter.invoke(filter,mapper.convertValue(entry.getValue(),setter.getParameterTypes()[0]));
                    }
                }
                arguments[i]=filter;
            } else {
                if (value == null && !name.equals("idempotency_key")) throw new IllegalArgumentException("Missing required argument: "+name);
                arguments[i]=value == null ? null : mapper.convertValue(value,mapper.constructType(parameter.getParameterizedType()));
            }
        }
        return arguments;
    }
}
