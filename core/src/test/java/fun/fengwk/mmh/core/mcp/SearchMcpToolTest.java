package fun.fengwk.mmh.core.mcp;

import fun.fengwk.mmh.core.facade.search.model.SearchResponse;
import fun.fengwk.mmh.core.facade.search.model.SearchResultItem;
import fun.fengwk.mmh.core.service.UtilMcpService;
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
public class SearchMcpToolTest {

    @Mock
    private UtilMcpService utilMcpService;

    private McpServerFeatures.SyncToolSpecification specification;

    @BeforeEach
    void setUp() {
        SearchMcpTool searchMcpTool = new SearchMcpTool(utilMcpService);
        specification = SyncMcpAnnotationProviders.toolSpecifications(List.of(searchMcpTool)).get(0);
    }

    @Test
    public void testSearchReturnsSerializedJsonText() {
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

        McpSchema.CallToolResult result = specification.callHandler().apply(
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
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(text).contains("\"statusCode\":200");
        assertThat(text).contains("\"query\":\"spring ai\"");
        assertThat(text).contains("\"numberOfResults\":1");
        assertThat(text).contains("\"title\":\"Spring AI\"");
        assertThat(text).contains("\"url\":\"https://spring.io/projects/spring-ai\"");
        assertThat(text).contains("\"content\":\"Spring AI project page\"");
        assertThat(result.structuredContent()).isNull();

        verify(utilMcpService).search("spring ai", 2, "month", 1);
    }

    @Test
    public void testSearchRejectsInvalidLimitType() {
        McpSchema.CallToolResult result = specification.callHandler().apply(
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
        verifyNoInteractions(utilMcpService);
    }

    @Test
    public void testSearchReturnsErrorResultWhenServiceReportsError() {
        when(utilMcpService.search("spring ai", 2, "month", 1)).thenReturn(SearchResponse.builder()
            .error("search error: boom")
            .build());

        McpSchema.CallToolResult result = specification.callHandler().apply(
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

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isInstanceOf(McpSchema.TextContent.class);
        assertThat(((McpSchema.TextContent) result.content().get(0)).text()).contains("search error: boom");
        verify(utilMcpService).search("spring ai", 2, "month", 1);
    }

}
