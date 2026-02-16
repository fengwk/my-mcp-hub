package fun.fengwk.mmh.cli.util;

import fun.fengwk.mmh.core.mcp.UtilMcp;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * @author fengwk
 */

@SpringBootApplication
public class CliUtilApplication {

    public static void main(String[] args) {
        SpringApplication.run(CliUtilApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider utilTools(UtilMcp utilsMcp) {
        return MethodToolCallbackProvider.builder()
            .toolObjects(utilsMcp)
            .build();
    }

}
