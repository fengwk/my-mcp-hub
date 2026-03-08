package fun.fengwk.mmh.core.mcp;

import fun.fengwk.mmh.core.service.UtilMcpService;
import io.modelcontextprotocol.server.McpServerFeatures;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class SearchMcpToolRegistrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(TestConfiguration.class);

    @Test
    public void testSearchToolDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(SearchMcpTool.class);
            assertThat(context).hasSingleBean(AnnotatedMcpTools.class);

            List<McpServerFeatures.SyncToolSpecification> specifications = SyncMcpAnnotationProviders.toolSpecifications(
                List.of(context.getBean(AnnotatedMcpTools.class))
            );

            assertThat(specifications)
                .extracting(specification -> specification.tool().name())
                .containsExactly("create_temp_dir");
        });
    }

    @Test
    public void testSearchToolEnabledWhenConfigured() {
        contextRunner
            .withPropertyValues("mmh.search.mcp-tool-enabled=true")
            .run(context -> {
                assertThat(context).hasSingleBean(SearchMcpTool.class);
                assertThat(context).hasSingleBean(AnnotatedMcpTools.class);

                List<Object> toolBeans = new ArrayList<>();
                toolBeans.add(context.getBean(AnnotatedMcpTools.class));
                toolBeans.add(context.getBean(SearchMcpTool.class));

                List<McpServerFeatures.SyncToolSpecification> specifications = SyncMcpAnnotationProviders.toolSpecifications(toolBeans);

                assertThat(specifications)
                    .extracting(specification -> specification.tool().name())
                    .containsExactlyInAnyOrder("search", "create_temp_dir");
            });
    }

    @Configuration
    @Import({AnnotatedMcpTools.class, SearchMcpTool.class, SearchMcpProperties.class})
    static class TestConfiguration {

        @Bean
        UtilMcpService utilMcpService() {
            return mock(UtilMcpService.class);
        }

    }

}
