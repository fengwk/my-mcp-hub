package fun.fengwk.mmh.core.mcp;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Search MCP 工具配置属性。
 *
 * @author fengwk
 */
@Data
@Component
@ConfigurationProperties(prefix = "mmh.search")
public class SearchMcpProperties {

    /**
     * 是否启用 search MCP tool 注册，默认关闭。
     */
    private boolean mcpToolEnabled = false;

}
