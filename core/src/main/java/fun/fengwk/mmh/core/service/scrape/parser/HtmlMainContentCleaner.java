package fun.fengwk.mmh.core.service.scrape.parser;

import fun.fengwk.convention4j.common.lang.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Cleans html content for markdown extraction.
 *
 * @author fengwk
 */
@Component
public class HtmlMainContentCleaner {

    private static final List<String> EXCLUDE_NON_MAIN_TAGS = List.of(
        "header",
        "footer",
        "nav",
        "aside",
        ".header",
        ".top",
        ".navbar",
        "#header",
        ".footer",
        ".bottom",
        "#footer",
        ".sidebar",
        ".side",
        ".aside",
        "#sidebar",
        ".modal",
        ".popup",
        "#modal",
        ".overlay",
        ".ad",
        ".ads",
        ".advert",
        "#ad",
        ".lang-selector",
        ".language",
        "#language-selector",
        ".social",
        ".social-media",
        ".social-links",
        "#social",
        ".menu",
        ".navigation",
        "#nav",
        ".breadcrumbs",
        "#breadcrumbs",
        ".share",
        "#share",
        ".widget",
        "#widget",
        ".cookie",
        "#cookie"
    );

    private static final List<String> FORCE_INCLUDE_MAIN_TAGS = List.of(
        "#main",
        ".swoogo-cols",
        ".swoogo-text",
        ".swoogo-table-div",
        ".swoogo-space",
        ".swoogo-alert",
        ".swoogo-sponsors",
        ".swoogo-title",
        ".swoogo-tabs",
        ".swoogo-logo",
        ".swoogo-image",
        ".swoogo-button",
        ".swoogo-agenda"
    );

    public String clean(String html) {
        return clean(html, "", true, true, true);
    }

    public String clean(
        String html,
        String baseUrl,
        boolean onlyMainContent,
        boolean stripChromeTags,
        boolean removeBase64Images
    ) {
        Document document = Jsoup.parse(html == null ? "" : html, StringUtils.isBlank(baseUrl) ? "" : baseUrl);
        document.select("script, style, noscript, meta, head").remove();
        if (onlyMainContent && stripChromeTags) {
            removeNonMainElements(document, true);
        }
        if (removeBase64Images) {
            removeEmbeddedImages(document);
        }
        normalizeSrcset(document);
        normalizeUrls(document);
        Element body = document.body();
        return body == null ? "" : body.html();
    }

    private void removeNonMainElements(Document document, boolean strict) {
        for (String selector : EXCLUDE_NON_MAIN_TAGS) {
            for (Element element : document.select(selector)) {
                if (!strict && isInsideMain(element)) {
                    continue;
                }
                if (!containsForceInclude(element)) {
                    element.remove();
                }
            }
        }
    }

    private boolean isInsideMain(Element element) {
        return element.closest("main, article, [role=main]") != null;
    }

    private void removeEmbeddedImages(Document document) {
        for (Element element : document.select("img[src^=data:image], img[srcset*=data:image]")) {
            element.remove();
        }
    }

    private boolean containsForceInclude(Element element) {
        for (String selector : FORCE_INCLUDE_MAIN_TAGS) {
            if (element.selectFirst(selector) != null) {
                return true;
            }
        }
        return false;
    }

    private void normalizeSrcset(Document document) {
        for (Element element : document.select("img[srcset]")) {
            String srcset = element.attr("srcset");
            if (StringUtils.isBlank(srcset)) {
                continue;
            }
            List<SrcCandidate> candidates = parseSrcsetCandidates(srcset);
            if (candidates.isEmpty()) {
                continue;
            }
            boolean allX = candidates.stream().allMatch(candidate -> candidate.isX);
            String src = element.attr("src");
            if (allX && !StringUtils.isBlank(src)) {
                candidates.add(new SrcCandidate(src, 1, true));
            }
            candidates.sort(Comparator.comparingInt(SrcCandidate::size).reversed());
            String selected = candidates.get(0).url();
            if (!StringUtils.isBlank(selected)) {
                element.attr("src", selected);
            }
        }
    }

    private List<SrcCandidate> parseSrcsetCandidates(String srcset) {
        List<SrcCandidate> candidates = new ArrayList<>();
        for (String part : srcset.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] tokens = trimmed.split("\\s+");
            if (tokens.length == 0) {
                continue;
            }
            String url = tokens[0];
            String sizeToken = tokens.length > 1 ? tokens[1] : "1x";
            boolean isX = sizeToken.endsWith("x");
            int size = parseSize(sizeToken);
            if (StringUtils.isBlank(url)) {
                continue;
            }
            candidates.add(new SrcCandidate(url, size, isX));
        }
        return candidates;
    }

    private int parseSize(String token) {
        if (StringUtils.isBlank(token)) {
            return 1;
        }
        String number = token.replaceAll("[^0-9]", "");
        if (StringUtils.isBlank(number)) {
            return 1;
        }
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private void normalizeUrls(Document document) {
        for (Element element : document.select("img[src]")) {
            String absolute = element.absUrl("src");
            if (!StringUtils.isBlank(absolute)) {
                element.attr("src", absolute);
            }
        }
        for (Element element : document.select("a[href]")) {
            String absolute = element.absUrl("href");
            if (!StringUtils.isBlank(absolute)) {
                element.attr("href", absolute);
            }
        }
    }

    private record SrcCandidate(String url, int size, boolean isX) {

    }

}
