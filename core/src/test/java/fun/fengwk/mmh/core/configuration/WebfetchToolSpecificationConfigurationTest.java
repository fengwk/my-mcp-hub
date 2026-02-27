package fun.fengwk.mmh.core.configuration;

import fun.fengwk.mmh.core.service.UtilMcpService;
import fun.fengwk.mmh.core.service.scrape.model.ScrapeResponse;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class WebfetchToolSpecificationConfigurationTest {

    @Mock
    private UtilMcpService utilMcpService;

    @Test
    public void testWebfetchReturnsImageContent() {
        ScrapeResponse response = ScrapeResponse.builder()
            .statusCode(200)
            .format("media")
            .screenshotMime("image/webp")
            .screenshotBase64("data:image/webp;base64,AAAA")
            .build();
        when(utilMcpService.scrape("https://example.com/a.webp", null, null, null, null)).thenReturn(response);

        McpServerFeatures.SyncToolSpecification specification = buildSpecification();
        McpSchema.CallToolResult result = specification.callHandler().apply(
            null,
            new McpSchema.CallToolRequest("webfetch", Map.of("url", "https://example.com/a.webp"))
        );

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isInstanceOf(McpSchema.ImageContent.class);

        McpSchema.ImageContent imageContent = (McpSchema.ImageContent) result.content().get(0);
        assertThat(imageContent.mimeType()).isEqualTo("image/webp");
        assertThat(imageContent.data()).isEqualTo("AAAA");
    }

    @Test
    public void testWebfetchReturnsBlobResourceForNonImageMedia() {
        ScrapeResponse response = ScrapeResponse.builder()
            .statusCode(200)
            .format("media")
            .screenshotMime("application/pdf")
            .screenshotBase64("data:application/pdf;base64,BBBB")
            .build();
        when(utilMcpService.scrape("https://example.com/a.pdf", null, null, null, null)).thenReturn(response);

        McpServerFeatures.SyncToolSpecification specification = buildSpecification();
        McpSchema.CallToolResult result = specification.callHandler().apply(
            null,
            new McpSchema.CallToolRequest("webfetch", Map.of("url", "https://example.com/a.pdf"))
        );

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isInstanceOf(McpSchema.EmbeddedResource.class);

        McpSchema.EmbeddedResource embeddedResource = (McpSchema.EmbeddedResource) result.content().get(0);
        assertThat(embeddedResource.resource()).isInstanceOf(McpSchema.BlobResourceContents.class);

        McpSchema.BlobResourceContents blobResourceContents = (McpSchema.BlobResourceContents) embeddedResource.resource();
        assertThat(blobResourceContents.uri()).isEqualTo("https://example.com/a.pdf");
        assertThat(blobResourceContents.mimeType()).isEqualTo("application/pdf");
        assertThat(blobResourceContents.blob()).isEqualTo("BBBB");
    }

    @Test
    public void testWebfetchReturnsTextContentForTextOutput() {
        ScrapeResponse response = ScrapeResponse.builder()
            .statusCode(200)
            .format("markdown")
            .content("# Hello")
            .elapsedMs(123L)
            .build();
        when(utilMcpService.scrape("https://example.com/doc", "markdown", true, null, "master")).thenReturn(response);

        McpServerFeatures.SyncToolSpecification specification = buildSpecification();
        McpSchema.CallToolResult result = specification.callHandler().apply(
            null,
            new McpSchema.CallToolRequest(
                "webfetch",
                Map.of(
                    "url", "https://example.com/doc",
                    "format", "markdown",
                    "profileMode", "master",
                    "onlyMainContent", true
                )
            )
        );

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isInstanceOf(McpSchema.TextContent.class);

        McpSchema.TextContent textContent = (McpSchema.TextContent) result.content().get(0);
        assertThat(textContent.text()).contains("format: markdown");
        assertThat(textContent.text()).contains("elapsedMs: 123");
        assertThat(textContent.text()).contains("# Hello");
    }

    @Test
    public void testWebfetchReturnsErrorResult() {
        ScrapeResponse response = ScrapeResponse.builder()
            .statusCode(500)
            .format("markdown")
            .error("boom")
            .elapsedMs(98L)
            .build();
        when(utilMcpService.scrape("https://example.com/error", null, null, null, null)).thenReturn(response);

        McpServerFeatures.SyncToolSpecification specification = buildSpecification();
        McpSchema.CallToolResult result = specification.callHandler().apply(
            null,
            new McpSchema.CallToolRequest("webfetch", Map.of("url", "https://example.com/error"))
        );

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isInstanceOf(McpSchema.TextContent.class);

        McpSchema.TextContent textContent = (McpSchema.TextContent) result.content().get(0);
        assertThat(textContent.text()).contains("format: markdown");
        assertThat(textContent.text()).contains("elapsedMs: 98");
        assertThat(textContent.text()).contains("---\nerror: boom");
        assertThat(textContent.text()).contains("error: boom");
    }

    private McpServerFeatures.SyncToolSpecification buildSpecification() {
        WebfetchToolSpecificationConfiguration configuration = new WebfetchToolSpecificationConfiguration();
        List<McpServerFeatures.SyncToolSpecification> specifications = configuration.webfetchToolSpecifications(utilMcpService);
        assertThat(specifications).hasSize(1);
        assertThat(specifications.get(0).tool().name()).isEqualTo("webfetch");
        return specifications.get(0);
    }

}
