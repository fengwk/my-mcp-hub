package fun.fengwk.mmh.core.service.scrape.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Extracts links from html.
 *
 * @author fengwk
 */
@Component
public class LinkExtractor {

    public List<String> extract(String html, String baseUrl) {
        Document document = Jsoup.parse(html == null ? "" : html, baseUrl == null ? "" : baseUrl);
        Set<String> deduplicated = new LinkedHashSet<>();
        for (Element element : document.select("a[href]")) {
            String href = element.attr("abs:href");
            if (href != null && !href.isBlank()) {
                deduplicated.add(href);
            }
        }
        return new ArrayList<>(deduplicated);
    }

}
