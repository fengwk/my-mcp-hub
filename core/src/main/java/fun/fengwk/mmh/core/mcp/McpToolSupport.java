package fun.fengwk.mmh.core.mcp;

import fun.fengwk.convention4j.common.lang.StringUtils;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared MCP tool helpers.
 *
 * @author fengwk
 */
final class McpToolSupport {

    private McpToolSupport() {
    }

    static Map<String, Object> arguments(McpSchema.CallToolRequest request) {
        return request.arguments() == null ? Map.of() : request.arguments();
    }

    static String requiredString(Map<String, Object> arguments, String name) {
        if (!arguments.containsKey(name)) {
            throw new IllegalArgumentException(name + " is required");
        }
        Object raw = arguments.get(name);
        if (!(raw instanceof String value)) {
            throw new IllegalArgumentException(name + " must be a string");
        }
        return value;
    }

    static String optionalString(Map<String, Object> arguments, String name) {
        Object raw = arguments.get(name);
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof String value)) {
            throw new IllegalArgumentException(name + " must be a string");
        }
        return value;
    }

    static Boolean optionalBoolean(Map<String, Object> arguments, String name) {
        Object raw = arguments.get(name);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Boolean value) {
            return value;
        }
        if (raw instanceof String value) {
            if ("true".equalsIgnoreCase(value)) {
                return true;
            }
            if ("false".equalsIgnoreCase(value)) {
                return false;
            }
        }
        throw new IllegalArgumentException(name + " must be a boolean");
    }

    static Integer optionalInteger(Map<String, Object> arguments, String name) {
        Object raw = arguments.get(name);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Integer value) {
            return value;
        }
        if (raw instanceof Long value) {
            if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
                throw new IllegalArgumentException(name + " must be an integer");
            }
            return value.intValue();
        }
        if (raw instanceof Number value) {
            double doubleValue = value.doubleValue();
            int intValue = value.intValue();
            if (doubleValue != intValue) {
                throw new IllegalArgumentException(name + " must be an integer");
            }
            return intValue;
        }
        if (raw instanceof String value) {
            if (StringUtils.isBlank(value)) {
                throw new IllegalArgumentException(name + " must be an integer");
            }
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(name + " must be an integer");
            }
        }
        throw new IllegalArgumentException(name + " must be an integer");
    }

    static boolean isSupportedHttpUrl(String url) {
        if (StringUtils.isBlank(url)) {
            return false;
        }
        String normalized = url.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("http://") || normalized.startsWith("https://");
    }

    static Map<String, Object> stringProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "string");
        property.put("description", description);
        return property;
    }

    static Map<String, Object> integerProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "integer");
        property.put("description", description);
        return property;
    }

    static Map<String, Object> booleanProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "boolean");
        property.put("description", description);
        return property;
    }

    static Map<String, Object> arrayProperty(String description, Map<String, Object> itemSchema) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "array");
        property.put("description", description);
        property.put("items", itemSchema);
        return property;
    }

    static McpSchema.JsonSchema jsonSchema(Map<String, Object> properties, List<String> required) {
        return new McpSchema.JsonSchema("object", properties, required, false, null, null);
    }

    static McpSchema.CallToolResult textResult(String text) {
        return McpSchema.CallToolResult.builder()
            .addTextContent(text)
            .isError(false)
            .build();
    }

    static McpSchema.CallToolResult errorResult(String error) {
        return McpSchema.CallToolResult.builder()
            .addTextContent(StringUtils.isBlank(error) ? "unknown error" : error)
            .isError(true)
            .build();
    }

    static String nvl(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

}
