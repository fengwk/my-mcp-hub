package fun.fengwk.mmh.core.service.scrape.parser;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

/**
 * Lightweight html to markdown renderer.
 *
 * @author fengwk
 */
@Component
public class MarkdownRenderer {

    public String render(String html) {
        return Jsoup.parse(html == null ? "" : html).text();
    }

}
