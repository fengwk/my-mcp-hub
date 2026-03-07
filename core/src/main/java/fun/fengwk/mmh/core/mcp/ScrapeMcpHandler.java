package fun.fengwk.mmh.core.mcp;

import fun.fengwk.convention4j.common.lang.StringUtils;
import fun.fengwk.mmh.core.service.UtilMcpService;
import fun.fengwk.mmh.core.service.browser.runtime.ProfileType;
import fun.fengwk.mmh.core.service.scrape.model.ScrapeResponse;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Scrape MCP handler.
 *
 * @author fengwk
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScrapeMcpHandler {

    private final UtilMcpService utilMcpService;
    private final ScrapeMcpResultMapper scrapeMcpResultMapper;

    public McpSchema.CallToolResult handle(McpSchema.CallToolRequest request) {
        try {
            Map<String, Object> arguments = McpToolSupport.arguments(request);
            String url = McpToolSupport.requiredString(arguments, "url");
            if (StringUtils.isBlank(url)) {
                return errorResult("url is blank", null, null);
            }
            url = url.trim();

            String format = normalizeOptionalString(McpToolSupport.optionalString(arguments, "format"));
            if (format != null) {
                String supportedFormat = resolveSupportedFormat(format);
                if (supportedFormat == null) {
                    return errorResult(buildUnsupportedFormatError(format), null, format);
                }
                format = supportedFormat;
            }

            String profileMode = McpToolSupport.optionalString(arguments, "profileMode");
            Boolean onlyMainContent = McpToolSupport.optionalBoolean(arguments, "onlyMainContent");
            Integer waitFor = McpToolSupport.optionalInteger(arguments, "waitFor");

            if (!McpToolSupport.isSupportedHttpUrl(url)) {
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
            return scrapeMcpResultMapper.toResult(url, response);
        } catch (IllegalArgumentException ex) {
            return errorResult(ex.getMessage(), null, null);
        } catch (Exception ex) {
            log.warn("scrape tool call failed, error={}", ex.getMessage(), ex);
            return errorResult(ex.getMessage(), null, null);
        }
    }

    private McpSchema.CallToolResult errorResult(String error, Long elapsedMs, String format) {
        return scrapeMcpResultMapper.toResult("", ScrapeResponse.builder()
            .error(error)
            .elapsedMs(elapsedMs)
            .format(format)
            .build());
    }

    private String normalizeOptionalString(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return value.trim();
    }

    private String resolveSupportedFormat(String format) {
        for (String supportedFormat : ScrapeMcpToolDefinition.SUPPORTED_FORMATS) {
            if (supportedFormat.equalsIgnoreCase(format)) {
                return supportedFormat;
            }
        }
        return null;
    }

    private String buildUnsupportedFormatError(String format) {
        return "unsupported format: " + format + ", supported formats: " + ScrapeMcpToolDefinition.SUPPORTED_FORMATS_HINT;
    }

}
