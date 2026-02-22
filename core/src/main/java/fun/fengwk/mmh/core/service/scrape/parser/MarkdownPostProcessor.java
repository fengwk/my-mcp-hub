package fun.fengwk.mmh.core.service.scrape.parser;

import fun.fengwk.convention4j.common.lang.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Post-process markdown output.
 *
 * @author fengwk
 */
@Component
public class MarkdownPostProcessor {

    public String process(String markdown) {
        if (StringUtils.isBlank(markdown)) {
            return "";
        }
        return markdown.trim();
    }

}
