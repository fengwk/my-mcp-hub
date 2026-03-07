package fun.fengwk.mmh.core.mcp;

import fun.fengwk.mmh.core.service.skill.SkillProperties;
import io.modelcontextprotocol.server.McpServerFeatures;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * MCP tool specification configuration.
 *
 * @author fengwk
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.ai.mcp.server", name = "enabled", havingValue = "true", matchIfMissing = true)
public class McpToolConfiguration {

    @Bean
    public List<McpServerFeatures.SyncToolSpecification> explicitToolSpecifications(
        SkillProperties skillProperties,
        SkillMcpToolDefinition skillMcpToolDefinition,
        SkillMcpHandler skillMcpHandler,
        ScrapeMcpToolDefinition scrapeMcpToolDefinition,
        ScrapeMcpHandler scrapeMcpHandler
    ) {
        List<McpServerFeatures.SyncToolSpecification> specifications = new ArrayList<>();
        if (skillProperties.isMcpToolEnabled()) {
            specifications.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(skillMcpToolDefinition.tool())
                .callHandler((exchange, request) -> skillMcpHandler.handle(request))
                .build());
        }
        specifications.add(McpServerFeatures.SyncToolSpecification.builder()
            .tool(scrapeMcpToolDefinition.tool())
            .callHandler((exchange, request) -> scrapeMcpHandler.handle(request))
            .build());
        return specifications;
    }

}
