package fun.fengwk.mmh.core.mcp;

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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AnnotatedMcpToolsTest {

    @Mock
    private UtilMcpService utilMcpService;

    private McpServerFeatures.SyncToolSpecification specification;

    @BeforeEach
    void setUp() {
        AnnotatedMcpTools annotatedMcpTools = new AnnotatedMcpTools(utilMcpService);
        specification = SyncMcpAnnotationProviders.toolSpecifications(List.of(annotatedMcpTools)).get(0);
    }

    @Test
    public void testExposesCreateTempDirOnly() {
        assertThat(specification.tool().name()).isEqualTo("create_temp_dir");
    }

    @Test
    public void testCreateTempDirReturnsTextContent() {
        when(utilMcpService.createTempDir()).thenReturn(CreateTempDirResponse.builder()
            .path("/tmp/mmh-123")
            .build());

        McpSchema.CallToolResult result = specification.callHandler().apply(
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

        McpSchema.CallToolResult result = specification.callHandler().apply(
            null,
            new McpSchema.CallToolRequest("create_temp_dir", Map.of())
        );

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isInstanceOf(McpSchema.TextContent.class);
        assertThat(((McpSchema.TextContent) result.content().get(0)).text()).contains("create temp dir error: boom");
        verify(utilMcpService).createTempDir();
    }

}
