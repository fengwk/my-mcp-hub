package fun.fengwk.mmh.core.mcp;

import fun.fengwk.mmh.core.service.skill.SkillManager;
import fun.fengwk.mmh.core.service.skill.model.Skill;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Skill MCP tool definition.
 *
 * @author fengwk
 */
@Component
@RequiredArgsConstructor
public class SkillMcpToolDefinition {

    private static final String DESCRIPTION_TEMPLATE = "skill_description.ftl";

    private final SkillManager skillManager;
    private final McpFormatter mcpFormatter;

    public McpSchema.Tool tool() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", McpToolSupport.stringProperty("The name of the skill from available_skills"));

        return McpSchema.Tool.builder()
            .name("skill")
            .description(buildDescription())
            .inputSchema(McpToolSupport.jsonSchema(properties, List.of("name")))
            .build();
    }

    private String buildDescription() {
        List<SkillInfo> skillInfos = skillManager.listSkills().stream()
            .map(this::toSkillInfo)
            .toList();
        return mcpFormatter.format(DESCRIPTION_TEMPLATE, Map.of("skills", skillInfos));
    }

    private SkillInfo toSkillInfo(Skill skill) {
        String basePath = skill.getBasePath();
        String location = "";
        if (basePath != null && !basePath.isBlank()) {
            location = Paths.get(basePath, "SKILL.md").toString();
        }
        return new SkillInfo(skill.getName(), skill.getDescription(), location);
    }

    public record SkillInfo(String name, String description, String location) {
    }

}
