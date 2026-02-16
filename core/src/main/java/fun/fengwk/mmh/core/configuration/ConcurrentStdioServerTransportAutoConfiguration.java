package fun.fengwk.mmh.core.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpServerTransportProviderBase;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerAutoConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 修复 stdio 再内容并发写回时候的 bug
 *
 * @author fengwk
 */
@AutoConfiguration(before = McpServerAutoConfiguration.class)
public class ConcurrentStdioServerTransportAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public McpServerTransportProviderBase concurrentStdioServerTransport(
        @Qualifier("mcpServerObjectMapper") ObjectMapper mcpServerObjectMapper) {
        return new ConcurrentStdioServerTransportProvider(new JacksonMcpJsonMapper(mcpServerObjectMapper));
    }

}
