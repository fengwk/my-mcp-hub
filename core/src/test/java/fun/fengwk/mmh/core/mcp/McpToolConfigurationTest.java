package fun.fengwk.mmh.core.mcp;

import fun.fengwk.mmh.core.service.skill.SkillProperties;
import io.modelcontextprotocol.server.McpServerFeatures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class McpToolConfigurationTest {

    @Test
    public void testSkillToolDisabledByDefault() {
        SkillProperties skillProperties = new SkillProperties();
        List<McpServerFeatures.SyncToolSpecification> specifications = new McpToolConfiguration().explicitToolSpecifications(
            skillProperties,
            mock(SkillMcpToolDefinition.class),
            mock(SkillMcpHandler.class),
            scrapeMcpToolDefinition(),
            mock(ScrapeMcpHandler.class)
        );

        assertThat(specifications)
            .extracting(specification -> specification.tool().name())
            .containsExactly("scrape");
    }

    @Test
    public void testSkillToolEnabledWhenConfigured() {
        SkillProperties skillProperties = new SkillProperties();
        skillProperties.setMcpToolEnabled(true);

        SkillMcpToolDefinition skillMcpToolDefinition = mock(SkillMcpToolDefinition.class);
        when(skillMcpToolDefinition.tool()).thenReturn(io.modelcontextprotocol.spec.McpSchema.Tool.builder()
            .name("skill")
            .description("skill")
            .inputSchema(new io.modelcontextprotocol.spec.McpSchema.JsonSchema("object", java.util.Map.of(), java.util.List.of(), false, null, null))
            .build());

        List<McpServerFeatures.SyncToolSpecification> specifications = new McpToolConfiguration().explicitToolSpecifications(
            skillProperties,
            skillMcpToolDefinition,
            mock(SkillMcpHandler.class),
            scrapeMcpToolDefinition(),
            mock(ScrapeMcpHandler.class)
        );

        assertThat(specifications)
            .extracting(specification -> specification.tool().name())
            .containsExactly("skill", "scrape");
    }

    private ScrapeMcpToolDefinition scrapeMcpToolDefinition() {
        return new ScrapeMcpToolDefinition();
    }

}
