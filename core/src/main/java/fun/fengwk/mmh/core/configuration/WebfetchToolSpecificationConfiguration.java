package fun.fengwk.mmh.core.configuration;

import fun.fengwk.convention4j.common.lang.StringUtils;
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
import java.util.Map;

/**
 * 自定义 webfetch 的 MCP ToolSpecification，输出协议级 image/resource content。
 *
 * @author fengwk
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "spring.ai.mcp.server", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WebfetchToolSpecificationConfiguration {

    @Bean
    public List<McpServerFeatures.SyncToolSpecification> webfetchToolSpecifications(UtilMcpService utilMcpService) {
        return List.of(
            McpServerFeatures.SyncToolSpecification.builder()
                .tool(buildTool())
                .callHandler((exchange, request) -> handleWebfetch(request, utilMcpService))
                .build()
        );
    }

    private static McpSchema.Tool buildTool() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("url", stringProperty("Target page URL. Must be a fully-qualified http/https URL."));
        properties.put("format", stringProperty("Output format. Optional, default markdown. Allowed: markdown, html, links, screenshot, fullscreenshot."));
        properties.put("profileMode", stringProperty("Browser profile mode. Optional, default default. Allowed: default, master."));
        properties.put("onlyMainContent", booleanProperty("Keep only main content for text outputs. Optional, default false."));

        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
            "object",
            properties,
            List.of("url"),
            false,
            null,
            null
        );

        return McpSchema.Tool.builder()
            .name("webfetch")
            .description("""
                webfetch, Fetches content from a URL and returns text or protocol-level attachments.
                Usage:
                - Required input: url
                - Optional input: format, profileMode, onlyMainContent
                - format values: markdown (default), html, links, screenshot, fullscreenshot
                - Use onlyMainContent=true to focus on the main article/content area for text outputs
                - profileMode values: default, master
                Output:
                - Text: markdown with metadata header (format, elapsedMs) + body content; errors are in body
                - Media/screenshot: protocol-level image/resource content
                - If the model supports multimodal input, attachments are passed to the model directly
                """)
            .inputSchema(inputSchema)
            .build();
    }

    private static McpSchema.CallToolResult handleWebfetch(McpSchema.CallToolRequest request, UtilMcpService utilMcpService) {
        try {
            Map<String, Object> arguments = request.arguments() == null ? Map.of() : request.arguments();
            String url = toString(arguments.get("url"));
            if (StringUtils.isBlank(url)) {
                return errorResult("url is blank", null, null);
            }

            String format = toString(arguments.get("format"));
            String profileMode = toString(arguments.get("profileMode"));
            Boolean onlyMainContent = toBoolean(arguments.get("onlyMainContent"));

            ScrapeResponse response = utilMcpService.scrape(url, format, onlyMainContent, null, profileMode);
            if (response == null) {
                return errorResult("webfetch response is null", null, format);
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
            log.warn("webfetch tool call failed, error={}", ex.getMessage(), ex);
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

    private static String nvl(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record MediaPayload(String mimeType, String base64Data) {
    }

}
