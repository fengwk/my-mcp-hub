package fun.fengwk.mmh.core.mcp;

import fun.fengwk.convention4j.common.lang.StringUtils;
import fun.fengwk.mmh.core.service.skill.SkillManager;
import fun.fengwk.mmh.core.service.skill.model.Skill;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Skill MCP handler.
 *
 * @author fengwk
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillMcpHandler {

    private final SkillManager skillManager;
    private final SkillMcpResultMapper skillMcpResultMapper;

    public McpSchema.CallToolResult handle(McpSchema.CallToolRequest request) {
        try {
            Map<String, Object> arguments = McpToolSupport.arguments(request);
            String name = McpToolSupport.requiredString(arguments, "name");
            if (StringUtils.isBlank(name)) {
                return McpToolSupport.errorResult("Skill name is required.");
            }

            String normalizedName = name.trim();
            Optional<Skill> skillOpt = skillManager.getSkill(normalizedName);
            if (skillOpt.isEmpty()) {
                List<String> available = skillManager.listSkillNames();
                String availableStr = available.isEmpty() ? "none" : String.join(", ", available);
                return skillMcpResultMapper.notFoundResult(normalizedName, availableStr);
            }

            return skillMcpResultMapper.toResult(skillOpt.get());
        } catch (IllegalArgumentException ex) {
            return McpToolSupport.errorResult(ex.getMessage());
        } catch (Exception ex) {
            log.warn("skill tool call failed, error={}", ex.getMessage(), ex);
            return McpToolSupport.errorResult(ex.getMessage());
        }
    }

}
