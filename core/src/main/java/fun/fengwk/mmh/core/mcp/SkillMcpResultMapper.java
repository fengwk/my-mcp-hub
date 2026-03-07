package fun.fengwk.mmh.core.mcp;

import fun.fengwk.mmh.core.service.skill.model.Skill;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Skill MCP result mapper.
 *
 * @author fengwk
 */
@Component
@RequiredArgsConstructor
public class SkillMcpResultMapper {

    private static final String CONTENT_TEMPLATE = "skill_content.ftl";

    private final McpFormatter mcpFormatter;

    public McpSchema.CallToolResult toResult(Skill skill) {
        return McpToolSupport.textResult(mcpFormatter.format(CONTENT_TEMPLATE, Map.of("skill", skill)));
    }

    public McpSchema.CallToolResult notFoundResult(String name, String availableSkills) {
        return McpToolSupport.errorResult("Skill \"" + name + "\" not found. Available skills: " + availableSkills);
    }

}
