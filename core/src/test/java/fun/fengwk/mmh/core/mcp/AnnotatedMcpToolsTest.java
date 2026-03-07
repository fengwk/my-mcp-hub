package fun.fengwk.mmh.core.mcp;

import fun.fengwk.mmh.core.facade.search.model.SearchResponse;
import fun.fengwk.mmh.core.facade.search.model.SearchResultItem;
import fun.fengwk.mmh.core.service.UtilMcpService;
import fun.fengwk.mmh.core.service.model.CreateTempDirResponse;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AnnotatedMcpToolsTest {

    @Mock
    private UtilMcpService utilMcpService;

    @Mock
    private McpFormatter mcpFormatter;

    private List<McpServerFeatures.SyncToolSpecification> specifications;

    @BeforeEach
    void setUp() {
        AnnotatedMcpTools annotatedMcpTools = new AnnotatedMcpTools(
            utilMcpService,
            new SearchMcpResultMapper(mcpFormatter),
            new TempDirMcpResultMapper()
        );
        specifications = SyncMcpAnnotationProviders.toolSpecifications(List.of(annotatedMcpTools));
    }

    @Test
    public void testExposesAnnotatedMcpTools() {
        assertThat(specifications)
            .extracting(specification -> specification.tool().name())
            .containsExactlyInAnyOrder("search", "create_temp_dir");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSearchReturnsTextAndStructuredContent() {
        SearchResponse response = SearchResponse.builder()
            .statusCode(200)
            .query("spring ai")
            .numberOfResults(1)
            .results(List.of(SearchResultItem.builder()
                .title("Spring AI")
                .url("https://spring.io/projects/spring-ai")
                .content("Spring AI project page")
                .build()))
            .build();
        when(utilMcpService.search("spring ai", 2, "month", 1)).thenReturn(response);
        when(mcpFormatter.format("mmh_search_result.ftl", response)).thenReturn("Result 1:\n* [Spring AI](https://spring.io/projects/spring-ai)");

        McpSchema.CallToolResult result = specification("search").callHandler().apply(
            null,
            new McpSchema.CallToolRequest(
                "search",
                Map.of(
                    "query", "spring ai",
                    "limit", 2,
                    "timeRange", "month",
                    "page", 1
                )
            )
        );

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isInstanceOf(McpSchema.TextContent.class);
        assertThat(((McpSchema.TextContent) result.content().get(0)).text()).contains("Spring AI");
        assertThat(result.structuredContent()).isInstanceOf(Map.class);

        Map<String, Object> structuredContent = (Map<String, Object>) result.structuredContent();
        assertThat(structuredContent.get("statusCode")).isEqualTo(200);
        assertThat(structuredContent.get("query")).isEqualTo("spring ai");
        assertThat(structuredContent.get("numberOfResults")).isEqualTo(1);
        List<Map<String, Object>> results = (List<Map<String, Object>>) structuredContent.get("results");
        assertThat(results).hasSize(1);
        assertThat(results.get(0)).containsEntry("title", "Spring AI");
        assertThat(results.get(0)).containsEntry("url", "https://spring.io/projects/spring-ai");
        assertThat(results.get(0)).containsEntry("content", "Spring AI project page");

        verify(utilMcpService).search("spring ai", 2, "month", 1);
        verify(mcpFormatter).format("mmh_search_result.ftl", response);
    }

    @Test
    public void testSearchRejectsInvalidLimitType() {
        McpSchema.CallToolResult result = specification("search").callHandler().apply(
            null,
            new McpSchema.CallToolRequest(
                "search",
                Map.of(
                    "query", "spring ai",
                    "limit", "oops"
                )
            )
        );

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isInstanceOf(McpSchema.TextContent.class);
        assertThat(((McpSchema.TextContent) result.content().get(0)).text()).contains("Character o is neither a decimal digit number");
        verifyNoInteractions(utilMcpService, mcpFormatter);
    }

    @Test
    public void testCreateTempDirReturnsTextContent() {
        when(utilMcpService.createTempDir()).thenReturn(CreateTempDirResponse.builder()
            .path("/tmp/mmh-123")
            .build());

        McpSchema.CallToolResult result = specification("create_temp_dir").callHandler().apply(
            null,
            new McpSchema.CallToolRequest("create_temp_dir", Map.of())
        );

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isInstanceOf(McpSchema.TextContent.class);
        assertThat(((McpSchema.TextContent) result.content().get(0)).text()).isEqualTo("/tmp/mmh-123");
        verify(utilMcpService).createTempDir();
    }

    @Test
    public void testCreateTempDirReturnsErrorResult() {
        when(utilMcpService.createTempDir()).thenReturn(CreateTempDirResponse.builder()
            .error("create temp dir error: boom")
            .build());

        McpSchema.CallToolResult result = specification("create_temp_dir").callHandler().apply(
            null,
            new McpSchema.CallToolRequest("create_temp_dir", Map.of())
        );

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isInstanceOf(McpSchema.TextContent.class);
        assertThat(((McpSchema.TextContent) result.content().get(0)).text()).contains("create temp dir error: boom");
        verify(utilMcpService).createTempDir();
    }

    private McpServerFeatures.SyncToolSpecification specification(String name) {
        return specifications.stream()
            .filter(specification -> specification.tool().name().equals(name))
            .findFirst()
            .orElseThrow();
    }

}
