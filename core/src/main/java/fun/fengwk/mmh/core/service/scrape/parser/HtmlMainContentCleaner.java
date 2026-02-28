package fun.fengwk.mmh.core.service.scrape.parser;

import fun.fengwk.convention4j.common.lang.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Cleans html content for markdown extraction.
 *
 * @author fengwk
 */
@Component
public class HtmlMainContentCleaner {

    private static final int MIN_MAIN_TEXT_LENGTH = 120;

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
        "#cookie",
        ".headerlink",
        ".copybutton",
        ".mw-editsection",
        "#toc",
        ".toc"
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

    private static final List<String> MAIN_CANDIDATE_SELECTORS = List.of(
        "main",
        "article",
        "[role=main]",
        "#main",
        "#main-content",
        "#content",
        ".main-content",
        ".content",
        ".article",
        ".article-content",
        ".post-content",
        ".entry-content",
        "#mw-content-text",
        ".mw-parser-output"
    );

    private static final DomainRule WIKIPEDIA_RULE = new DomainRule(
        "wikipedia.org",
        List.of("#mw-content-text", ".mw-parser-output", "main[role=main]"),
        List.of(
            ".shortdescription",
            ".hatnote",
            ".ambox",
            ".metadata",
            "#mw-navigation",
            ".vector-page-toolbar",
            ".vector-header-container",
            ".vector-sticky-pinned-container",
            ".mw-jump-link",
            "#p-lang-btn",
            "#p-search",
            "#toc",
            ".toc",
            ".mw-editsection",
            "sup.reference",
            ".reflist",
            ".mw-references-wrap",
            ".navbox",
            ".vertical-navbox",
            ".catlinks",
            ".mw-authority-control",
            ".printfooter"
        )
    );

    private static final DomainRule PYTHON_DOCS_RULE = new DomainRule(
        "docs.python.org",
        List.of("main", "article", "div[role=main]", ".body"),
        List.of(".sphinxsidebar", ".related", ".headerlink", ".copybutton")
    );

    private static final List<DomainRule> DOMAIN_RULES = List.of(
        WIKIPEDIA_RULE,
        PYTHON_DOCS_RULE
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
            removeDomainChromeElements(document, baseUrl);
        }
        if (removeBase64Images) {
            removeEmbeddedImages(document);
        }
        normalizeSrcset(document);
        normalizeUrls(document);

        Element body = document.body();
        if (body == null) {
            return "";
        }
        if (onlyMainContent) {
            Element mainContentElement = selectMainContentElement(document, body, baseUrl);
            if (mainContentElement != null && mainContentElement != body) {
                return mainContentElement.outerHtml();
            }
        }
        return body.html();
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

    private void removeDomainChromeElements(Document document, String baseUrl) {
        DomainRule domainRule = resolveDomainRule(baseUrl);
        if (domainRule == null) {
            return;
        }
        for (String selector : domainRule.stripSelectors()) {
            document.select(selector).remove();
        }
    }

    private Element selectMainContentElement(Document document, Element body, String baseUrl) {
        DomainRule domainRule = resolveDomainRule(baseUrl);
        Element preferredCandidate = findPreferredCandidate(document, domainRule, body);
        if (preferredCandidate != null) {
            return preferredCandidate;
        }

        Set<Element> candidates = new LinkedHashSet<>();
        if (domainRule != null) {
            for (String selector : domainRule.preferredMainSelectors()) {
                candidates.addAll(document.select(selector));
            }
        }
        for (String selector : MAIN_CANDIDATE_SELECTORS) {
            candidates.addAll(document.select(selector));
        }
        if (candidates.isEmpty()) {
            candidates.addAll(document.select("section, div"));
        }

        Element bestCandidate = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Element candidate : candidates) {
            if (candidate == null || candidate == body.parent()) {
                continue;
            }
            double score = scoreMainCandidate(candidate);
            if (score > bestScore) {
                bestScore = score;
                bestCandidate = candidate;
            }
        }

        if (bestCandidate == null) {
            return body;
        }

        int bodyTextLength = normalizedTextLength(body.text());
        int candidateTextLength = normalizedTextLength(bestCandidate.text());
        int minimumTextLength = Math.min(MIN_MAIN_TEXT_LENGTH, Math.max(40, bodyTextLength / 8));
        if (candidateTextLength < minimumTextLength) {
            return body;
        }

        return bestCandidate;
    }

    private Element findPreferredCandidate(Document document, DomainRule domainRule, Element body) {
        if (domainRule == null) {
            return null;
        }

        Element bestCandidate = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (String selector : domainRule.preferredMainSelectors()) {
            for (Element candidate : document.select(selector)) {
                if (candidate == null || candidate == body.parent()) {
                    continue;
                }
                int textLength = normalizedTextLength(candidate.text());
                if (textLength < 80) {
                    continue;
                }
                double score = scoreMainCandidate(candidate);
                if (score > bestScore) {
                    bestScore = score;
                    bestCandidate = candidate;
                }
            }
        }
        return bestCandidate;
    }

    private DomainRule resolveDomainRule(String baseUrl) {
        String host = extractHost(baseUrl);
        if (StringUtils.isBlank(host)) {
            return null;
        }
        for (DomainRule domainRule : DOMAIN_RULES) {
            if (host.equals(domainRule.hostSuffix()) || host.endsWith("." + domainRule.hostSuffix())) {
                return domainRule;
            }
        }
        return null;
    }

    private String extractHost(String baseUrl) {
        if (StringUtils.isBlank(baseUrl)) {
            return "";
        }
        try {
            URI uri = URI.create(baseUrl.trim());
            String host = uri.getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (Exception ex) {
            return "";
        }
    }

    private double scoreMainCandidate(Element candidate) {
        int textLength = normalizedTextLength(candidate.text());
        if (textLength <= 0) {
            return Double.NEGATIVE_INFINITY;
        }

        int linkTextLength = 0;
        for (Element linkElement : candidate.select("a")) {
            linkTextLength += normalizedTextLength(linkElement.text());
        }
        double linkDensity = linkTextLength / (double) Math.max(1, textLength);

        int blockCount = candidate.select("p, h1, h2, h3, h4, li, pre, blockquote, table, tr").size();
        int imageCount = candidate.select("img").size();
        int markupLength = Math.max(1, candidate.outerHtml().length());
        double markupOverhead = markupLength / (double) Math.max(1, textLength);

        double score = textLength * (1D - Math.min(0.95D, linkDensity));
        score += Math.min(80, blockCount) * 12D;
        score += Math.min(20, imageCount) * 6D;
        score -= Math.min(240D, markupOverhead * 18D);

        if (isLikelyPrimaryContainer(candidate)) {
            score += 120D;
        }
        score += specificPrimaryContainerBonus(candidate);
        if (isLikelyBoilerplateContainer(candidate)) {
            score *= 0.3D;
        }

        return score;
    }

    private boolean isLikelyPrimaryContainer(Element candidate) {
        String tagName = candidate.tagName();
        if ("main".equalsIgnoreCase(tagName) || "article".equalsIgnoreCase(tagName)) {
            return true;
        }
        String marker = buildContainerMarker(candidate);
        return marker.contains("main")
            || marker.contains("content")
            || marker.contains("article")
            || marker.contains("post")
            || marker.contains("entry")
            || marker.contains("mw-content-text")
            || marker.contains("mw-parser-output");
    }

    private boolean isLikelyBoilerplateContainer(Element candidate) {
        String marker = buildContainerMarker(candidate);
        return marker.contains("nav")
            || marker.contains("menu")
            || marker.contains("sidebar")
            || marker.contains("footer")
            || marker.contains("header")
            || marker.contains("breadcrumb")
            || marker.contains("social")
            || marker.contains("share")
            || marker.contains("comment")
            || marker.contains("related")
            || marker.contains("cookie")
            || marker.contains("banner")
            || marker.contains("popup")
            || marker.contains("modal")
            || marker.contains("login")
            || marker.contains("search")
            || marker.contains("toc");
    }

    private double specificPrimaryContainerBonus(Element candidate) {
        String marker = buildContainerMarker(candidate);
        if (marker.contains("mw-content-text") || marker.contains("mw-parser-output")) {
            return 220D;
        }
        if (marker.contains("article-content") || marker.contains("post-content") || marker.contains("entry-content")) {
            return 180D;
        }
        if (marker.contains("main-content")) {
            return 100D;
        }
        return 0D;
    }

    private String buildContainerMarker(Element candidate) {
        String role = candidate.attr("role");
        String id = candidate.id();
        String className = candidate.className();
        return (role + " " + id + " " + className).toLowerCase(Locale.ROOT);
    }

    private int normalizedTextLength(String value) {
        if (StringUtils.isBlank(value)) {
            return 0;
        }
        return value.replaceAll("\\s+", " ").trim().length();
    }

    private record SrcCandidate(String url, int size, boolean isX) {

    }

    private record DomainRule(String hostSuffix, List<String> preferredMainSelectors, List<String> stripSelectors) {

    }

}
