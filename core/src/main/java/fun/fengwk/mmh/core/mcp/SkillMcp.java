package fun.fengwk.mmh.core.mcp;

import fun.fengwk.mmh.core.service.skill.SkillManager;
import fun.fengwk.mmh.core.service.skill.model.Skill;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.util.json.JsonParser;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Skill MCP 工具提供者
 * 动态生成工具描述，包含可用 skill 列表
 *
 * @author fengwk
 */
//@Component
public class SkillMcp implements ToolCallbackProvider {

    private static final String TOOL_NAME = "skill";
    private static final String DESCRIPTION_TEMPLATE = "skill_description.ftl";
    private static final String CONTENT_TEMPLATE = "skill_content.ftl";

    private static final String INPUT_SCHEMA = """
        {
          "type": "object",
          "properties": {
            "name": {
              "type": "string",
              "description": "The name of the skill from available_skills"
            }
          },
          "required": ["name"]
        }
        """;

    private final SkillManager skillManager;
    private final McpFormatter mcpFormatter;

    public SkillMcp(SkillManager skillManager, McpFormatter mcpFormatter) {
        this.skillManager = skillManager;
        this.mcpFormatter = mcpFormatter;
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        return new ToolCallback[] { new SkillToolCallback() };
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

    /**
     * 用于描述模板的 Skill 信息
     */
    public record SkillInfo(String name, String description, String location) {}

    /**
     * 自定义 ToolCallback 实现
     */
    private class SkillToolCallback implements ToolCallback {

        @Override
        public ToolDefinition getToolDefinition() {
            return DefaultToolDefinition.builder()
                .name(TOOL_NAME)
                .description(buildDescription())
                .inputSchema(INPUT_SCHEMA)
                .build();
        }

        @Override
        @SuppressWarnings("unchecked")
        public String call(String toolInput) {
            Map<String, Object> params = JsonParser.fromJson(toolInput, Map.class);
            Object nameObj = params.get("name");
            String name = nameObj != null ? nameObj.toString() : null;

            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Skill name is required.");
            }

            Optional<Skill> skillOpt = skillManager.getSkill(name);
            if (skillOpt.isEmpty()) {
                List<String> available = skillManager.listSkillNames();
                String availableStr = available.isEmpty()
                    ? "none"
                    : String.join(", ", available);
                throw new IllegalArgumentException(
                    "Skill \"" + name + "\" not found. Available skills: " + availableStr);
            }

            return mcpFormatter.format(CONTENT_TEMPLATE, Map.of("skill", skillOpt.get()));
        }
    }

}
