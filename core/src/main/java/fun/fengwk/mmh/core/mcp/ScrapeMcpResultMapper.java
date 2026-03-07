package fun.fengwk.mmh.core.mcp;

import fun.fengwk.convention4j.common.lang.StringUtils;
import fun.fengwk.mmh.core.service.scrape.model.ScrapeResponse;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Component;

/**
 * Scrape result mapper.
 *
 * @author fengwk
 */
@Component
public class ScrapeMcpResultMapper {

    public McpSchema.CallToolResult toResult(String url, ScrapeResponse response) {
        if (response == null) {
            return errorResult("scrape response is null", null, null);
        }

        if (StringUtils.isNotBlank(response.getError())) {
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
    }

    private String formatTextOutput(ScrapeResponse response) {
        StringBuilder builder = new StringBuilder(192);
        builder.append("---\n");
        builder.append("format: ").append(McpToolSupport.nvl(response.getFormat())).append('\n');
        builder.append("elapsedMs: ").append(McpToolSupport.nvl(response.getElapsedMs())).append('\n');
        builder.append("---");

        if (response.getLinks() != null && !response.getLinks().isEmpty()) {
            builder.append("\nLinks:");
            for (String link : response.getLinks()) {
                builder.append("\n- ").append(McpToolSupport.nvl(link));
            }
            return builder.toString();
        }

        if (StringUtils.isNotBlank(response.getContent())) {
            builder.append('\n').append(response.getContent());
        }
        return builder.toString();
    }

    private McpSchema.CallToolResult errorResult(String error, Long elapsedMs, String format) {
        StringBuilder builder = new StringBuilder(96);
        builder.append("---\n");
        builder.append("format: ").append(McpToolSupport.nvl(format)).append('\n');
        builder.append("elapsedMs: ").append(McpToolSupport.nvl(elapsedMs)).append('\n');
        builder.append("---\n");
        builder.append("error: ").append(McpToolSupport.nvl(error));
        return McpSchema.CallToolResult.builder()
            .addTextContent(builder.toString())
            .isError(true)
            .build();
    }

    private MediaPayload parseMediaPayload(String dataUri, String mimeType) {
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

    private record MediaPayload(String mimeType, String base64Data) {
    }

}
