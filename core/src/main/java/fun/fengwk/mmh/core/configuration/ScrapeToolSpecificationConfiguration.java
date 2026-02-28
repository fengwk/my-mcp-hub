package fun.fengwk.mmh.core.configuration;

import fun.fengwk.convention4j.common.lang.StringUtils;
import fun.fengwk.mmh.core.service.browser.runtime.ProfileType;
import fun.fengwk.mmh.core.service.UtilMcpService;
import fun.fengwk.mmh.core.service.scrape.model.ScrapeResponse;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 自定义 scrape 的 MCP ToolSpecification，输出协议级 image/resource content。
 *
 * @author fengwk
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "spring.ai.mcp.server", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ScrapeToolSpecificationConfiguration {

    private static final List<String> SUPPORTED_FORMATS = List.of("markdown", "links", "screenshot", "fullscreenshot");

    private static final String SUPPORTED_FORMATS_HINT = String.join(", ", SUPPORTED_FORMATS);

    @Bean
    public List<McpServerFeatures.SyncToolSpecification> scrapeToolSpecifications(UtilMcpService utilMcpService) {
        return List.of(
            McpServerFeatures.SyncToolSpecification.builder()
                .tool(buildTool())
                .callHandler((exchange, request) -> handleScrape(request, utilMcpService))
                .build()
        );
    }

    private static McpSchema.Tool buildTool() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("url", stringProperty("Target page URL. Must be a fully-qualified http/https URL."));

        Map<String, Object> formatProperty = stringProperty("Output format. Optional, default markdown. Allowed: markdown, links, screenshot, fullscreenshot.");
        formatProperty.put("enum", SUPPORTED_FORMATS);
        properties.put("format", formatProperty);

        Map<String, Object> profileModeProperty = stringProperty("Browser profile mode. Optional, default default. Allowed: default, master.");
        profileModeProperty.put("enum", List.of("default", "master"));
        properties.put("profileMode", profileModeProperty);

        properties.put("onlyMainContent", booleanProperty("Keep only main content for text outputs. Optional, default false."));

        Map<String, Object> waitForProperty = integerProperty("Fixed wait in milliseconds after DOMContentLoaded. Optional. If > 0, skips smart wait. Range: 0-60000.");
        waitForProperty.put("minimum", 0);
        waitForProperty.put("maximum", 60000);
        properties.put("waitFor", waitForProperty);

        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
            "object",
            properties,
            List.of("url"),
            false,
            null,
            null
        );

        return McpSchema.Tool.builder()
            .name("scrape")
            .description("""
                scrape, Fetches content from a URL and returns text or protocol-level attachments.
                Usage:
                - Required input: url
                - Optional input: format, profileMode, onlyMainContent, waitFor
                - format values: markdown (default), links, screenshot, fullscreenshot
                - Use onlyMainContent=true to focus on the main article/content area for text outputs
                - profileMode values: default, master
                - profileMode guidance: try default first; use master only for anti-bot/login-gated pages (master is serialized and slower)
                - waitFor: fixed wait time in milliseconds after DOMContentLoaded, when > 0 smart wait is skipped
                Output:
                - Text: markdown with metadata header (format, elapsedMs) + body content; errors are in body
                - Media/screenshot: protocol-level image/resource content
                - If the model supports multimodal input, attachments are passed to the model directly
                """)
            .inputSchema(inputSchema)
            .build();
    }

    private static McpSchema.CallToolResult handleScrape(McpSchema.CallToolRequest request, UtilMcpService utilMcpService) {
        try {
            Map<String, Object> arguments = request.arguments() == null ? Map.of() : request.arguments();
            Object urlRaw = arguments.get("url");
            if (!isStringOrNull(urlRaw)) {
                return errorResult("url must be a string", null, null);
            }
            String url = toString(urlRaw);
            if (StringUtils.isBlank(url)) {
                return errorResult("url is blank", null, null);
            }
            url = url.trim();

            Object formatRaw = arguments.get("format");
            if (!isStringOrNull(formatRaw)) {
                return errorResult("format must be a string", null, null);
            }
            String format = normalizeOptionalString(toString(formatRaw));
            if (format != null) {
                String supportedFormat = resolveSupportedFormat(format);
                if (supportedFormat == null) {
                    return errorResult(buildUnsupportedFormatError(format), null, format);
                }
                format = supportedFormat;
            }

            Object profileModeRaw = arguments.get("profileMode");
            if (!isStringOrNull(profileModeRaw)) {
                return errorResult("profileMode must be a string", null, format);
            }
            String profileMode = toString(profileModeRaw);

            Object onlyMainContentRaw = arguments.get("onlyMainContent");
            Boolean onlyMainContent = toBoolean(onlyMainContentRaw);
            if (onlyMainContentRaw != null && onlyMainContent == null) {
                return errorResult("onlyMainContent must be a boolean", null, format);
            }
            Object waitForRaw = arguments.get("waitFor");
            Integer waitFor = toInteger(waitForRaw);
            if (waitForRaw != null && waitFor == null) {
                return errorResult("waitFor must be an integer", null, format);
            }
            if (!isSupportedHttpUrl(url)) {
                return errorResult("unsupported url protocol", null, format);
            }
            if (waitFor != null && (waitFor < 0 || waitFor > 60000)) {
                return errorResult("waitFor out of range", null, format);
            }
            try {
                ProfileType.fromValue(profileMode);
            } catch (IllegalArgumentException ex) {
                return errorResult(ex.getMessage(), null, format);
            }

            ScrapeResponse response = utilMcpService.scrape(url, format, onlyMainContent, waitFor, profileMode);
            if (response == null) {
                return errorResult("scrape response is null", null, format);
            }

            if (!StringUtils.isBlank(response.getError())) {
                return errorResult(response.getError(), response.getElapsedMs(), response.getFormat());
            }

            MediaPayload mediaPayload = parseMediaPayload(response.getScreenshotBase64(), response.getScreenshotMime());
            if (mediaPayload != null) {
                if (mediaPayload.mimeType().startsWith("image/")) {
                    return McpSchema.CallToolResult.builder()
                        .addContent(new McpSchema.ImageContent(null, mediaPayload.base64Data(), mediaPayload.mimeType()))
                        .isError(false)
                        .build();
                }

                return McpSchema.CallToolResult.builder()
                    .addContent(new McpSchema.EmbeddedResource(
                        null,
                        new McpSchema.BlobResourceContents(url, mediaPayload.mimeType(), mediaPayload.base64Data())
                    ))
                    .isError(false)
                    .build();
            }

            return McpSchema.CallToolResult.builder()
                .addTextContent(formatTextOutput(response))
                .isError(false)
                .build();
        } catch (Exception ex) {
            log.warn("scrape tool call failed, error={}", ex.getMessage(), ex);
            return errorResult(ex.getMessage(), null, null);
        }
    }

    private static String formatTextOutput(ScrapeResponse response) {
        StringBuilder builder = new StringBuilder(192);
        builder.append("---\n");
        builder.append("format: ").append(nvl(response.getFormat())).append('\n');
        builder.append("elapsedMs: ").append(nvl(response.getElapsedMs())).append('\n');
        builder.append("---");

        if (response.getLinks() != null && !response.getLinks().isEmpty()) {
            builder.append("\nLinks:");
            for (String link : response.getLinks()) {
                builder.append("\n- ").append(nvl(link));
            }
            return builder.toString();
        }

        if (!StringUtils.isBlank(response.getContent())) {
            builder.append('\n').append(response.getContent());
        }
        return builder.toString();
    }

    private static McpSchema.CallToolResult errorResult(String error, Long elapsedMs, String format) {
        StringBuilder builder = new StringBuilder(96);
        builder.append("---\n");
        builder.append("format: ").append(nvl(format)).append('\n');
        builder.append("elapsedMs: ").append(nvl(elapsedMs)).append('\n');
        builder.append("---\n");
        builder.append("error: ").append(nvl(error));
        return McpSchema.CallToolResult.builder()
            .addTextContent(builder.toString())
            .isError(true)
            .build();
    }

    private static MediaPayload parseMediaPayload(String dataUri, String mimeType) {
        if (StringUtils.isBlank(dataUri)) {
            return null;
        }

        String fallbackMimeType = StringUtils.isBlank(mimeType) ? "application/octet-stream" : mimeType;
        String payload = dataUri.trim();
        if (!payload.startsWith("data:")) {
            return new MediaPayload(fallbackMimeType, payload);
        }

        int commaIndex = payload.indexOf(',');
        if (commaIndex <= 5 || commaIndex >= payload.length() - 1) {
            return new MediaPayload(fallbackMimeType, payload);
        }

        String mimePart = payload.substring(5, commaIndex);
        int semicolonIndex = mimePart.indexOf(';');
        String actualMimeType = semicolonIndex > 0 ? mimePart.substring(0, semicolonIndex) : mimePart;
        if (StringUtils.isBlank(actualMimeType)) {
            actualMimeType = fallbackMimeType;
        }

        String base64Data = payload.substring(commaIndex + 1);
        return new MediaPayload(actualMimeType, base64Data);
    }

    private static String toString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        return String.valueOf(value);
    }

    private static boolean isStringOrNull(Object value) {
        return value == null || value instanceof String;
    }

    private static String normalizeOptionalString(String format) {
        if (StringUtils.isBlank(format)) {
            return null;
        }
        return format.trim();
    }

    private static String resolveSupportedFormat(String format) {
        for (String supportedFormat : SUPPORTED_FORMATS) {
            if (supportedFormat.equalsIgnoreCase(format)) {
                return supportedFormat;
            }
        }
        return null;
    }

    private static String buildUnsupportedFormatError(String format) {
        return "unsupported format: " + format + ", supported formats: " + SUPPORTED_FORMATS_HINT;
    }

    private static Boolean toBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            if ("true".equalsIgnoreCase(s)) {
                return true;
            }
            if ("false".equalsIgnoreCase(s)) {
                return false;
            }
        }
        return null;
    }

    private static Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer i) {
            return i;
        }
        if (value instanceof Long l) {
            if (l > Integer.MAX_VALUE || l < Integer.MIN_VALUE) {
                return null;
            }
            return l.intValue();
        }
        if (value instanceof Number n) {
            double doubleValue = n.doubleValue();
            int intValue = n.intValue();
            if (doubleValue != intValue) {
                return null;
            }
            return intValue;
        }
        if (value instanceof String s) {
            if (StringUtils.isBlank(s)) {
                return null;
            }
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private static boolean isSupportedHttpUrl(String url) {
        if (StringUtils.isBlank(url)) {
            return false;
        }
        String normalized = url.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("http://") || normalized.startsWith("https://");
    }

    private static Map<String, Object> stringProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "string");
        property.put("description", description);
        return property;
    }

    private static Map<String, Object> booleanProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "boolean");
        property.put("description", description);
        return property;
    }

    private static Map<String, Object> integerProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "integer");
        property.put("description", description);
        return property;
    }

    private static String nvl(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record MediaPayload(String mimeType, String base64Data) {
    }

}
