package fun.fengwk.mmh.core.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;

/**
 * @author fengwk
 */
@Configuration
public class FreeMarkerConfiguration {

    @Bean(name = "mcpTemplateConfiguration")
    public freemarker.template.Configuration mcpTemplateConfiguration() {
        freemarker.template.Configuration cfg = new freemarker.template.Configuration(
            freemarker.template.Configuration.VERSION_2_3_34);
        cfg.setClassLoaderForTemplateLoading(ClassUtils.getDefaultClassLoader(), "/mcp/templates/");
        cfg.setDefaultEncoding("UTF-8");
        return cfg;
    }

}
