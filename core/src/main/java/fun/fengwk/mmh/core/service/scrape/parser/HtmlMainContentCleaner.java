package fun.fengwk.mmh.core.service.scrape.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * Extracts main content from html.
 *
 * @author fengwk
 */
@Component
public class HtmlMainContentCleaner {

    public String clean(String html) {
        Document document = Jsoup.parse(html == null ? "" : html);
        Element main = document.selectFirst("main, article, [role=main]");
        if (main != null) {
            return main.outerHtml();
        }
        Element body = document.body();
        return body == null ? "" : body.html();
    }

}
