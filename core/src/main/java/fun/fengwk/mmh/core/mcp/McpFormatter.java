package fun.fengwk.mmh.core.mcp;

import freemarker.template.Template;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.StringWriter;
import java.util.Map;

/**
 * @author fengwk
 */
@Component
public class McpFormatter {

    private final freemarker.template.Configuration mcpTemplateConfiguration;

    public McpFormatter(@Qualifier("mcpTemplateConfiguration") freemarker.template.Configuration mcpTemplateConfiguration) {
        this.mcpTemplateConfiguration = mcpTemplateConfiguration;
    }

    public String format(String templateName, Object model) {
        if (model == null) {
            return "empty response";
        }
        StringWriter result = new StringWriter(1024);
        try {
            Template template = mcpTemplateConfiguration.getTemplate(templateName);
            Object root = model instanceof Map ? model : Map.of("data", model);
            template.process(root, result);
            return result.toString();
        } catch (Exception e) {
            return "format error: " + e.getMessage();
        }
    }

}
