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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ScrapeToolSpecificationConfigurationTest {

    @Mock
    private UtilMcpService utilMcpService;

    @Test
    public void testScrapeReturnsImageContent() {
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
            new McpSchema.CallToolRequest("scrape", Map.of("url", "https://example.com/a.webp"))
        );

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isInstanceOf(McpSchema.ImageContent.class);

        McpSchema.ImageContent imageContent = (McpSchema.ImageContent) result.content().get(0);
        assertThat(imageContent.mimeType()).isEqualTo("image/webp");
        assertThat(imageContent.data()).isEqualTo("AAAA");
    }

    @Test
    public void testScrapeReturnsBlobResourceForNonImageMedia() {
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
            new McpSchema.CallToolRequest("scrape", Map.of("url", "https://example.com/a.pdf"))
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
    public void testScrapeReturnsTextContentForTextOutput() {
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
                "scrape",
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
    public void testScrapeForwardsWaitForWhenProvided() {
        ScrapeResponse response = ScrapeResponse.builder()
            .statusCode(200)
            .format("markdown")
            .content("ok")
            .elapsedMs(66L)
            .build();
        when(utilMcpService.scrape("https://example.com/wait", "markdown", false, 250, "default")).thenReturn(response);

        McpServerFeatures.SyncToolSpecification specification = buildSpecification();
        McpSchema.CallToolResult result = specification.callHandler().apply(
            null,
            new McpSchema.CallToolRequest(
                "scrape",
                Map.of(
                    "url", "https://example.com/wait",
                    "format", "markdown",
                    "profileMode", "default",
                    "onlyMainContent", false,
                    "waitFor", 250
                )
            )
        );

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isInstanceOf(McpSchema.TextContent.class);
        McpSchema.TextContent textContent = (McpSchema.TextContent) result.content().get(0);
        assertThat(textContent.text()).contains("format: markdown");
        assertThat(textContent.text()).contains("ok");
    }

    @Test
    public void testScrapeTrimsUrlBeforeForwarding() {
        ScrapeResponse response = ScrapeResponse.builder()
            .statusCode(200)
            .format("markdown")
            .content("ok")
            .build();
        when(utilMcpService.scrape("https://example.com/trim", "markdown", null, null, null)).thenReturn(response);

        McpServerFeatures.SyncToolSpecification specification = buildSpecification();
        McpSchema.CallToolResult result = specification.callHandler().apply(
            null,
            new McpSchema.CallToolRequest(
                "scrape",
                Map.of(
                    "url", "  https://example.com/trim  ",
                    "format", "markdown"
                )
            )
        );

        assertThat(result.isError()).isFalse();
        verify(utilMcpService).scrape("https://example.com/trim", "markdown", null, null, null);
    }

    @Test
    public void testScrapeRejectsHtmlFormatWithSupportedHints() {
        McpServerFeatures.SyncToolSpecification specification = buildSpecification();
        McpSchema.CallToolResult result = specification.callHandler().apply(
            null,
            new McpSchema.CallToolRequest(
                "scrape",
                Map.of(
                    "url", "https://example.com/doc",
                    "format", "html"
                )
            )
        );

        assertThat(result.isError()).isTrue();
        verifyNoInteractions(utilMcpService);
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isInstanceOf(McpSchema.TextContent.class);
        McpSchema.TextContent textContent = (McpSchema.TextContent) result.content().get(0);
        assertThat(textContent.text()).contains("error: unsupported format: html, supported formats: markdown, links, screenshot, fullscreenshot");
    }

    @Test
    public void testScrapeRejectsNonIntegerWaitFor() {
        McpServerFeatures.SyncToolSpecification specification = buildSpecification();
        McpSchema.CallToolResult result = specification.callHandler().apply(
            null,
            new McpSchema.CallToolRequest(
                "scrape",
                Map.of(
                    "url", "https://example.com/wait",
                    "waitFor", "abc"
                )
            )
        );

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isInstanceOf(McpSchema.TextContent.class);
        McpSchema.TextContent textContent = (McpSchema.TextContent) result.content().get(0);
        assertThat(textContent.text()).contains("error: waitFor must be an integer");
        verifyNoInteractions(utilMcpService);
    }

    @Test
    public void testScrapeRejectsNonBooleanOnlyMainContent() {
        McpServerFeatures.SyncToolSpecification specification = buildSpecification();
        McpSchema.CallToolResult result = specification.callHandler().apply(
            null,
            new McpSchema.CallToolRequest(
                "scrape",
                Map.of(
                    "url", "https://example.com/doc",
                    "onlyMainContent", "yes"
                )
            )
        );

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isInstanceOf(McpSchema.TextContent.class);
        McpSchema.TextContent textContent = (McpSchema.TextContent) result.content().get(0);
        assertThat(textContent.text()).contains("error: onlyMainContent must be a boolean");
        verifyNoInteractions(utilMcpService);
    }

    @Test
    public void testScrapeRejectsNonStringUrl() {
        McpServerFeatures.SyncToolSpecification specification = buildSpecification();
        McpSchema.CallToolResult result = specification.callHandler().apply(
            null,
            new McpSchema.CallToolRequest(
                "scrape",
                Map.of(
                    "url", 123
                )
            )
        );

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isInstanceOf(McpSchema.TextContent.class);
        McpSchema.TextContent textContent = (McpSchema.TextContent) result.content().get(0);
        assertThat(textContent.text()).contains("error: url must be a string");
        verifyNoInteractions(utilMcpService);
    }

    @Test
    public void testScrapeRejectsNonStringFormat() {
        McpServerFeatures.SyncToolSpecification specification = buildSpecification();
        McpSchema.CallToolResult result = specification.callHandler().apply(
            null,
            new McpSchema.CallToolRequest(
                "scrape",
                Map.of(
                    "url", "https://example.com/doc",
                    "format", 1
                )
            )
        );

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isInstanceOf(McpSchema.TextContent.class);
        McpSchema.TextContent textContent = (McpSchema.TextContent) result.content().get(0);
        assertThat(textContent.text()).contains("error: format must be a string");
        verifyNoInteractions(utilMcpService);
    }

    @Test
    public void testScrapeRejectsNonStringProfileMode() {
        McpServerFeatures.SyncToolSpecification specification = buildSpecification();
        McpSchema.CallToolResult result = specification.callHandler().apply(
            null,
            new McpSchema.CallToolRequest(
                "scrape",
                Map.of(
                    "url", "https://example.com/doc",
                    "profileMode", true
                )
            )
        );

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isInstanceOf(McpSchema.TextContent.class);
        McpSchema.TextContent textContent = (McpSchema.TextContent) result.content().get(0);
        assertThat(textContent.text()).contains("error: profileMode must be a string");
        verifyNoInteractions(utilMcpService);
    }

    @Test
    public void testScrapeRejectsWaitForOutOfRange() {
        McpServerFeatures.SyncToolSpecification specification = buildSpecification();
        McpSchema.CallToolResult result = specification.callHandler().apply(
            null,
            new McpSchema.CallToolRequest(
                "scrape",
                Map.of(
                    "url", "https://example.com/wait",
                    "waitFor", 60001
                )
            )
        );

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isInstanceOf(McpSchema.TextContent.class);
        McpSchema.TextContent textContent = (McpSchema.TextContent) result.content().get(0);
        assertThat(textContent.text()).contains("error: waitFor out of range");
        verifyNoInteractions(utilMcpService);
    }

    @Test
    public void testScrapeRejectsUnsupportedProtocol() {
        McpServerFeatures.SyncToolSpecification specification = buildSpecification();
        McpSchema.CallToolResult result = specification.callHandler().apply(
            null,
            new McpSchema.CallToolRequest(
                "scrape",
                Map.of(
                    "url", "ftp://example.com/doc"
                )
            )
        );

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isInstanceOf(McpSchema.TextContent.class);
        McpSchema.TextContent textContent = (McpSchema.TextContent) result.content().get(0);
        assertThat(textContent.text()).contains("error: unsupported url protocol");
        verifyNoInteractions(utilMcpService);
    }

    @Test
    public void testScrapeRejectsUnsupportedFormat() {
        McpServerFeatures.SyncToolSpecification specification = buildSpecification();
        McpSchema.CallToolResult result = specification.callHandler().apply(
            null,
            new McpSchema.CallToolRequest(
                "scrape",
                Map.of(
                    "url", "https://example.com/doc",
                    "format", "pdf"
                )
            )
        );

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isInstanceOf(McpSchema.TextContent.class);
        McpSchema.TextContent textContent = (McpSchema.TextContent) result.content().get(0);
        assertThat(textContent.text()).contains("error: unsupported format: pdf, supported formats: markdown, links, screenshot, fullscreenshot");
        verifyNoInteractions(utilMcpService);
    }

    @Test
    public void testScrapeRejectsUnsupportedProfileMode() {
        McpServerFeatures.SyncToolSpecification specification = buildSpecification();
        McpSchema.CallToolResult result = specification.callHandler().apply(
            null,
            new McpSchema.CallToolRequest(
                "scrape",
                Map.of(
                    "url", "https://example.com/doc",
                    "profileMode", "unknown"
                )
            )
        );

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isInstanceOf(McpSchema.TextContent.class);
        McpSchema.TextContent textContent = (McpSchema.TextContent) result.content().get(0);
        assertThat(textContent.text()).contains("error: unsupported profileMode: unknown");
        verifyNoInteractions(utilMcpService);
    }

    @Test
    public void testScrapeReturnsErrorResult() {
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
            new McpSchema.CallToolRequest("scrape", Map.of("url", "https://example.com/error"))
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
        ScrapeToolSpecificationConfiguration configuration = new ScrapeToolSpecificationConfiguration();
        List<McpServerFeatures.SyncToolSpecification> specifications = configuration.scrapeToolSpecifications(utilMcpService);
        assertThat(specifications).hasSize(1);
        assertThat(specifications.get(0).tool().name()).isEqualTo("scrape");
        return specifications.get(0);
    }

}
