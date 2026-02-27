package fun.fengwk.mmh.cli.all;

import fun.fengwk.mmh.core.mcp.UtilMcp;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * @author fengwk
 */

@SpringBootApplication
public class CliAllApplication {

    public static void main(String[] args) {
        SpringApplication.run(CliAllApplication.class, args);
    }

    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.mcp.server", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ToolCallbackProvider utilTools(UtilMcp utilMcp) {
        return MethodToolCallbackProvider.builder()
            .toolObjects(utilMcp)
            .build();
    }

}
