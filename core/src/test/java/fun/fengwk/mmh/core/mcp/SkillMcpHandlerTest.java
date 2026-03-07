package fun.fengwk.mmh.core.mcp;

import fun.fengwk.mmh.core.service.skill.SkillManager;
import fun.fengwk.mmh.core.service.skill.SkillProperties;
import fun.fengwk.mmh.core.service.skill.model.Skill;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SkillMcpHandlerTest {

    @Mock
    private SkillManager skillManager;

    @Mock
    private McpFormatter mcpFormatter;

    @Test
    public void testSkillToolDescriptionUsesCurrentSkillCatalog() {
        when(skillManager.listSkills()).thenReturn(List.of(
            Skill.builder()
                .name("openspec-apply-change")
                .description("Implement tasks from an OpenSpec change")
                .basePath("/tmp/openspec-apply-change")
                .build()
        ));
        when(mcpFormatter.format(eq("skill_description.ftl"), anyMap())).thenReturn("dynamic skill description");

        McpServerFeatures.SyncToolSpecification specification = buildSpecification();

        assertThat(specification.tool().name()).isEqualTo("skill");
        assertThat(specification.tool().description()).isEqualTo("dynamic skill description");
        verify(skillManager).listSkills();
        verify(mcpFormatter).format(eq("skill_description.ftl"), anyMap());
    }

    @Test
    public void testSkillReturnsRenderedContent() {
        Skill skill = Skill.builder()
            .name("openspec-apply-change")
            .description("Implement tasks from an OpenSpec change")
            .content("# Skill Body")
            .basePath("/tmp/openspec-apply-change")
            .build();
        when(skillManager.listSkills()).thenReturn(List.of(skill));
        when(skillManager.getSkill("openspec-apply-change")).thenReturn(Optional.of(skill));
        when(mcpFormatter.format(eq("skill_description.ftl"), anyMap())).thenReturn("dynamic skill description");
        when(mcpFormatter.format(eq("skill_content.ftl"), anyMap())).thenReturn("<skill_content name=\"openspec-apply-change\">...</skill_content>");

        McpSchema.CallToolResult result = buildSpecification().callHandler().apply(
            null,
            new McpSchema.CallToolRequest("skill", Map.of("name", "openspec-apply-change"))
        );

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isInstanceOf(McpSchema.TextContent.class);
        assertThat(((McpSchema.TextContent) result.content().get(0)).text()).contains("skill_content");
        verify(skillManager).getSkill("openspec-apply-change");
    }

    @Test
    public void testSkillReturnsErrorWhenNotFound() {
        when(skillManager.listSkills()).thenReturn(List.of());
        when(skillManager.getSkill("missing-skill")).thenReturn(Optional.empty());
        when(skillManager.listSkillNames()).thenReturn(List.of("known-skill"));
        when(mcpFormatter.format(eq("skill_description.ftl"), anyMap())).thenReturn("dynamic skill description");

        McpSchema.CallToolResult result = buildSpecification().callHandler().apply(
            null,
            new McpSchema.CallToolRequest("skill", Map.of("name", "missing-skill"))
        );

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isInstanceOf(McpSchema.TextContent.class);
        assertThat(((McpSchema.TextContent) result.content().get(0)).text())
            .contains("Skill \"missing-skill\" not found. Available skills: known-skill");
        verify(skillManager).listSkillNames();
    }

    @Test
    public void testSkillReturnsProtocolErrorOnUnexpectedFailure() {
        when(skillManager.listSkills()).thenReturn(List.of());
        when(mcpFormatter.format(eq("skill_description.ftl"), anyMap())).thenReturn("dynamic skill description");
        when(skillManager.getSkill("broken-skill")).thenThrow(new IllegalStateException("boom"));

        McpSchema.CallToolResult result = buildSpecification().callHandler().apply(
            null,
            new McpSchema.CallToolRequest("skill", Map.of("name", "broken-skill"))
        );

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isInstanceOf(McpSchema.TextContent.class);
        assertThat(((McpSchema.TextContent) result.content().get(0)).text()).contains("boom");
    }

    private McpServerFeatures.SyncToolSpecification buildSpecification() {
        McpToolConfiguration configuration = new McpToolConfiguration();
        SkillProperties skillProperties = new SkillProperties();
        skillProperties.setMcpToolEnabled(true);
        return configuration.explicitToolSpecifications(
                skillProperties,
                new SkillMcpToolDefinition(skillManager, mcpFormatter),
                new SkillMcpHandler(skillManager, new SkillMcpResultMapper(mcpFormatter)),
                new ScrapeMcpToolDefinition(),
                new ScrapeMcpHandler(null, new ScrapeMcpResultMapper())
            )
            .get(0);
    }

}
