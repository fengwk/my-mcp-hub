package fun.fengwk.mmh.core.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scrape MCP tool definition.
 *
 * @author fengwk
 */
@Component
public class ScrapeMcpToolDefinition {

    static final List<String> SUPPORTED_FORMATS = List.of("markdown", "links", "screenshot", "fullscreenshot");

    static final String SUPPORTED_FORMATS_HINT = String.join(", ", SUPPORTED_FORMATS);

    public McpSchema.Tool tool() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("url", McpToolSupport.stringProperty("Target page URL. Must be a fully-qualified http/https URL."));

        Map<String, Object> formatProperty = McpToolSupport.stringProperty("Output format. Optional, default markdown. Allowed: markdown, links, screenshot, fullscreenshot.");
        formatProperty.put("enum", SUPPORTED_FORMATS);
        properties.put("format", formatProperty);

        Map<String, Object> profileModeProperty = McpToolSupport.stringProperty("Browser profile mode. Optional, default default. Allowed: default, master.");
        profileModeProperty.put("enum", List.of("default", "master"));
        properties.put("profileMode", profileModeProperty);

        properties.put("onlyMainContent", McpToolSupport.booleanProperty("Keep only main content for text outputs. Optional, default false."));

        Map<String, Object> waitForProperty = McpToolSupport.integerProperty("Fixed wait in milliseconds after DOMContentLoaded. Optional. If > 0, skips smart wait. Range: 0-60000.");
        waitForProperty.put("minimum", 0);
        waitForProperty.put("maximum", 60000);
        properties.put("waitFor", waitForProperty);

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
                - waitFor: fixed wait in milliseconds after DOMContentLoaded, when > 0 smart wait is skipped
                Output:
                - Text: markdown with metadata header (format, elapsedMs) + body content; errors are in body
                - Media/screenshot: protocol-level image/resource content
                - If the model supports multimodal input, attachments are passed to the model directly
                """)
            .inputSchema(McpToolSupport.jsonSchema(properties, List.of("url")))
            .build();
    }

}
