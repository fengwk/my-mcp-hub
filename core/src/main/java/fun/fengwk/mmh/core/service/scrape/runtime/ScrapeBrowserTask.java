package fun.fengwk.mmh.core.service.scrape.runtime;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.RequestOptions;
import com.microsoft.playwright.options.WaitUntilState;
import fun.fengwk.convention4j.common.lang.StringUtils;
import fun.fengwk.mmh.core.service.browser.runtime.BrowserRuntimeContext;
import fun.fengwk.mmh.core.service.browser.runtime.BrowserTask;
import fun.fengwk.mmh.core.service.scrape.ScrapeProperties;
import fun.fengwk.mmh.core.service.scrape.model.ScrapeFormat;
import fun.fengwk.mmh.core.service.scrape.model.ScrapeRequest;
import fun.fengwk.mmh.core.service.scrape.model.ScrapeResponse;
import fun.fengwk.mmh.core.service.scrape.parser.HtmlMainContentCleaner;
import fun.fengwk.mmh.core.service.scrape.parser.LinkExtractor;
import fun.fengwk.mmh.core.service.scrape.parser.MarkdownPostProcessor;
import fun.fengwk.mmh.core.service.scrape.parser.MarkdownRenderer;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.regex.Pattern;

/**
 * Scrape task adapter over generic browser task runtime.
 *
 * @author fengwk
 */
@Slf4j
@RequiredArgsConstructor
public class ScrapeBrowserTask implements BrowserTask<ScrapeResponse> {

    private static final String SCREENSHOT_MIME = "image/png";

    private static final String FORMAT_MEDIA = "media";

    private static final Pattern UUID_PATTERN = Pattern.compile(
        "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b"
    );

    private static final Pattern LONG_NUMBER_PATTERN = Pattern.compile("\\b\\d{10,}\\b");

    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

    private final ScrapeRequest request;
    private final ScrapeFormat format;
    private final ScrapeProperties scrapeProperties;
    private final HtmlMainContentCleaner htmlMainContentCleaner;
    private final MarkdownRenderer markdownRenderer;
    private final MarkdownPostProcessor markdownPostProcessor;
    private final LinkExtractor linkExtractor;

    @Override
    public ScrapeResponse execute(BrowserRuntimeContext context) {
        Page page = context.getPage();

        DirectMedia directMedia = tryFetchDirectMedia(page, request.getUrl());
        if (directMedia != null) {
            return ScrapeResponse.builder()
                .statusCode(200)
                .format(FORMAT_MEDIA)
                .screenshotMime(directMedia.mime())
                .screenshotBase64(directMedia.dataUri())
                .build();
        }

        page.navigate(request.getUrl(),
            new Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                .setTimeout((double) scrapeProperties.getNavigateTimeoutMs())
        );

        if (request.getWaitFor() != null && request.getWaitFor() > 0) {
            page.waitForTimeout(request.getWaitFor());
        } else if (scrapeProperties.isSmartWaitEnabled()) {
            waitForContentStable(page);
        } else {
            waitForNetworkIdleBestEffort(page);
        }

        String html = page.content();
        List<FrameDocument> frameDocuments = collectFrameDocuments(page);
        boolean onlyMainContent = request.getOnlyMainContent() != null && request.getOnlyMainContent();
        String cleanedHtml = htmlMainContentCleaner.clean(
            html,
            request.getUrl(),
            onlyMainContent,
            scrapeProperties.isStripChromeTags(),
            scrapeProperties.isRemoveBase64Images()
        );
        String fallbackHtml = onlyMainContent
            ? htmlMainContentCleaner.clean(
                html,
                request.getUrl(),
                false,
                scrapeProperties.isStripChromeTags(),
                scrapeProperties.isRemoveBase64Images()
            )
            : cleanedHtml;
        List<FrameContent> frameContents = buildFrameContents(frameDocuments, onlyMainContent);

        ScrapeResponse.ScrapeResponseBuilder builder = ScrapeResponse.builder()
            .statusCode(200)
            .format(format.getValue());

        switch (format) {
            case HTML:
                builder.content(mergeHtml(cleanedHtml, frameContents));
                break;
            case MARKDOWN:
                String markdown = renderMarkdownWithFallback(cleanedHtml, fallbackHtml, request.getUrl(), onlyMainContent);
                builder.content(mergeMarkdown(markdown, frameContents, onlyMainContent));
                break;
            case LINKS:
                builder.links(extractLinks(cleanedHtml, frameContents));
                break;
            case SCREENSHOT:
                builder.screenshotMime(SCREENSHOT_MIME);
                builder.screenshotBase64(toDataUri(SCREENSHOT_MIME, page.screenshot()));
                break;
            case FULLSCREENSHOT:
                builder.screenshotMime(SCREENSHOT_MIME);
                builder.screenshotBase64(toDataUri(SCREENSHOT_MIME, page.screenshot(
                    new Page.ScreenshotOptions().setFullPage(true)
                )));
                break;
            default:
                throw new IllegalArgumentException("unsupported format: " + format.getValue());
        }
        return builder.build();
    }

    private void waitForNetworkIdleBestEffort(Page page) {
        try {
            // NETWORKIDLE may never happen for long-polling pages, treat it as best-effort.
            page.waitForLoadState(
                LoadState.NETWORKIDLE,
                new Page.WaitForLoadStateOptions().setTimeout((double) scrapeProperties.getNavigateTimeoutMs())
            );
        } catch (TimeoutError ex) {
            log.warn("network idle timeout, url={}", request.getUrl());
        }
    }

    private void waitForContentStable(Page page) {
        int checkIntervalMs = Math.max(100, scrapeProperties.getStabilityCheckIntervalMs());
        int stableThreshold = Math.max(1, scrapeProperties.getStabilityThreshold());
        long stabilityMaxWaitMs = Math.max(checkIntervalMs, scrapeProperties.getStabilityMaxWaitMs());
        long maxWaitMs = Math.max(checkIntervalMs, Math.min(scrapeProperties.getNavigateTimeoutMs(), stabilityMaxWaitMs));

        long deadlineAt = System.currentTimeMillis() + maxWaitMs;
        int exactStableRounds = 0;
        int semanticStableRounds = 0;
        int lastTextLength = -1;
        long lastFingerprint = -1L;
        long lastSemanticFingerprint = -1L;

        while (System.currentTimeMillis() < deadlineAt) {
            StabilitySnapshot snapshot = calculateStabilitySnapshot(page);
            if (snapshot.totalTextLength() > 0
                && snapshot.totalTextLength() == lastTextLength
                && snapshot.contentFingerprint() == lastFingerprint) {
                exactStableRounds++;
                if (exactStableRounds >= stableThreshold) {
                    return;
                }
            } else {
                exactStableRounds = 0;
            }

            if (snapshot.totalTextLength() > 0
                && snapshot.semanticFingerprint() == lastSemanticFingerprint
                && isMinorTextLengthChange(snapshot.totalTextLength(), lastTextLength)) {
                semanticStableRounds++;
                if (semanticStableRounds >= stableThreshold + 1) {
                    log.debug(
                        "smart wait settled by semantic stability, url={}, semanticStableRounds={}, stableThreshold={}",
                        request.getUrl(),
                        semanticStableRounds,
                        stableThreshold
                    );
                    return;
                }
            } else {
                semanticStableRounds = 0;
            }

            lastTextLength = snapshot.totalTextLength();
            lastFingerprint = snapshot.contentFingerprint();
            lastSemanticFingerprint = snapshot.semanticFingerprint();
            page.waitForTimeout(checkIntervalMs);
        }

        log.warn(
            "smart wait timeout, url={}, maxWaitMs={}, stableThreshold={}, checkIntervalMs={}",
            request.getUrl(),
            maxWaitMs,
            stableThreshold,
            checkIntervalMs
        );
    }

    private StabilitySnapshot calculateStabilitySnapshot(Page page) {
        CRC32 contentCrc32 = new CRC32();
        CRC32 semanticCrc32 = new CRC32();
        int totalLength = 0;

        String mainText = extractNormalizedText(page.content());
        if (StringUtils.isNotBlank(mainText)) {
            totalLength += mainText.length();
            updateFingerprint(contentCrc32, "main");
            updateFingerprint(contentCrc32, mainText);
            updateFingerprint(semanticCrc32, "main");
            updateFingerprint(semanticCrc32, normalizeDynamicSignals(mainText));
        }

        Frame mainFrame = null;
        try {
            mainFrame = page.mainFrame();
        } catch (Exception ex) {
            log.debug("get main frame failed, url={}, error={}", request.getUrl(), ex.getMessage());
        }

        List<Frame> frames;
        try {
            frames = page.frames();
        } catch (Exception ex) {
            log.debug("list frames failed, url={}, error={}", request.getUrl(), ex.getMessage());
            return new StabilitySnapshot(totalLength, contentCrc32.getValue(), semanticCrc32.getValue());
        }
        if (frames == null || frames.isEmpty()) {
            return new StabilitySnapshot(totalLength, contentCrc32.getValue(), semanticCrc32.getValue());
        }

        for (Frame frame : frames) {
            if (frame == null || frame == mainFrame) {
                continue;
            }
            String frameText = safeFrameText(frame);
            if (StringUtils.isBlank(frameText)) {
                continue;
            }
            totalLength += frameText.length();
            updateFingerprint(contentCrc32, "frame");
            updateFingerprint(contentCrc32, safeFrameUrl(frame));
            updateFingerprint(contentCrc32, frameText);
            updateFingerprint(semanticCrc32, "frame");
            updateFingerprint(semanticCrc32, safeFrameUrl(frame));
            updateFingerprint(semanticCrc32, normalizeDynamicSignals(frameText));
        }

        return new StabilitySnapshot(totalLength, contentCrc32.getValue(), semanticCrc32.getValue());
    }

    private boolean isMinorTextLengthChange(int currentLength, int previousLength) {
        if (currentLength <= 0 || previousLength <= 0) {
            return false;
        }
        int tolerance = Math.max(8, previousLength / 100);
        return Math.abs(currentLength - previousLength) <= tolerance;
    }

    private String safeFrameText(Frame frame) {
        try {
            if (frame.isDetached()) {
                return "";
            }
            return extractNormalizedText(frame.content());
        } catch (Exception ex) {
            log.debug("get frame text length failed, frameUrl={}, error={}", safeFrameUrl(frame), ex.getMessage());
            return "";
        }
    }

    private String extractNormalizedText(String html) {
        if (StringUtils.isBlank(html)) {
            return "";
        }
        String text = Jsoup.parse(html).text();
        if (StringUtils.isBlank(text)) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    private void updateFingerprint(CRC32 crc32, String value) {
        if (StringUtils.isBlank(value)) {
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        crc32.update(bytes, 0, bytes.length);
        crc32.update('\n');
    }

    private String normalizeDynamicSignals(String value) {
        if (StringUtils.isBlank(value)) {
            return "";
        }

        String normalized = UUID_PATTERN.matcher(value).replaceAll("<uuid>");
        normalized = LONG_NUMBER_PATTERN.matcher(normalized).replaceAll("<long-number>");
        return NUMBER_PATTERN.matcher(normalized).replaceAll("#");
    }

    private List<FrameDocument> collectFrameDocuments(Page page) {
        Frame mainFrame = safeMainFrame(page);
        List<Frame> frames = safeFrames(page);
        if (frames == null || frames.isEmpty()) {
            return List.of();
        }

        Map<Frame, String> frameIdMap = new HashMap<>();
        for (int i = 0; i < frames.size(); i++) {
            Frame frame = frames.get(i);
            if (frame == null) {
                continue;
            }
            frameIdMap.put(frame, "frame-" + (i + 1));
        }

        List<FrameDocument> frameDocuments = new ArrayList<>();
        for (int i = 0; i < frames.size(); i++) {
            Frame frame = frames.get(i);
            if (frame == null || frame == mainFrame) {
                continue;
            }

            try {
                if (frame.isDetached()) {
                    continue;
                }
                String frameHtml = frame.content();
                if (StringUtils.isBlank(frameHtml)) {
                    continue;
                }
                String frameId = frameIdMap.get(frame);
                if (StringUtils.isBlank(frameId)) {
                    frameId = "frame-" + (i + 1);
                }
                Frame parentFrame = safeParentFrame(frame);
                String parentId = parentFrame == null || parentFrame == mainFrame
                    ? null
                    : frameIdMap.get(parentFrame);
                int depth = resolveFrameDepth(frame, mainFrame);
                frameDocuments.add(new FrameDocument(
                    frameId,
                    parentId,
                    frame.url(),
                    frameHtml,
                    depth,
                    i
                ));
            } catch (Exception ex) {
                log.debug("collect frame content failed, frameUrl={}, error={}", safeFrameUrl(frame), ex.getMessage());
            }
        }

        return orderFrameDocuments(frameDocuments);
    }

    private Frame safeMainFrame(Page page) {
        try {
            return page.mainFrame();
        } catch (Exception ex) {
            log.debug("get main frame failed when collect frame content, url={}, error={}", request.getUrl(), ex.getMessage());
            return null;
        }
    }

    private List<Frame> safeFrames(Page page) {
        try {
            List<Frame> frames = page.frames();
            return frames == null ? List.of() : frames;
        } catch (Exception ex) {
            log.debug("list frames failed when collect frame content, url={}, error={}", request.getUrl(), ex.getMessage());
            return List.of();
        }
    }

    private Frame safeParentFrame(Frame frame) {
        try {
            return frame.parentFrame();
        } catch (Exception ex) {
            log.debug("get parent frame failed, frameUrl={}, error={}", safeFrameUrl(frame), ex.getMessage());
            return null;
        }
    }

    private String safeFrameUrl(Frame frame) {
        if (frame == null) {
            return "";
        }
        try {
            return frame.url();
        } catch (Exception ex) {
            return "";
        }
    }

    private int resolveFrameDepth(Frame frame, Frame mainFrame) {
        int depth = 1;
        Frame current = frame;
        Set<Frame> visited = new HashSet<>();
        while (true) {
            Frame parentFrame = safeParentFrame(current);
            if (parentFrame == null || parentFrame == mainFrame) {
                return depth;
            }
            if (!visited.add(parentFrame)) {
                return depth;
            }
            depth++;
            current = parentFrame;
        }
    }

    private List<FrameDocument> orderFrameDocuments(List<FrameDocument> frameDocuments) {
        if (frameDocuments == null || frameDocuments.isEmpty()) {
            return List.of();
        }

        Map<String, List<FrameDocument>> childrenByParentId = new HashMap<>();
        Set<String> knownFrameIds = new HashSet<>();
        for (FrameDocument frameDocument : frameDocuments) {
            knownFrameIds.add(frameDocument.id());
            String parentId = frameDocument.parentId();
            childrenByParentId.computeIfAbsent(parentId, key -> new ArrayList<>()).add(frameDocument);
        }
        for (List<FrameDocument> siblings : childrenByParentId.values()) {
            siblings.sort((left, right) -> Integer.compare(left.order(), right.order()));
        }

        List<FrameDocument> roots = new ArrayList<>();
        for (FrameDocument frameDocument : frameDocuments) {
            if (frameDocument.parentId() == null || !knownFrameIds.contains(frameDocument.parentId())) {
                roots.add(frameDocument);
            }
        }
        roots.sort((left, right) -> Integer.compare(left.order(), right.order()));

        List<FrameDocument> ordered = new ArrayList<>(frameDocuments.size());
        Set<String> visited = new HashSet<>();
        for (FrameDocument root : roots) {
            appendFrameDocumentDepthFirst(root, childrenByParentId, visited, ordered);
        }

        if (ordered.size() < frameDocuments.size()) {
            List<FrameDocument> remaining = new ArrayList<>(frameDocuments);
            remaining.sort((left, right) -> Integer.compare(left.order(), right.order()));
            for (FrameDocument frameDocument : remaining) {
                appendFrameDocumentDepthFirst(frameDocument, childrenByParentId, visited, ordered);
            }
        }

        return ordered;
    }

    private void appendFrameDocumentDepthFirst(
        FrameDocument frameDocument,
        Map<String, List<FrameDocument>> childrenByParentId,
        Set<String> visited,
        List<FrameDocument> ordered
    ) {
        if (frameDocument == null || !visited.add(frameDocument.id())) {
            return;
        }

        ordered.add(frameDocument);
        List<FrameDocument> children = childrenByParentId.get(frameDocument.id());
        if (children == null || children.isEmpty()) {
            return;
        }
        for (FrameDocument child : children) {
            appendFrameDocumentDepthFirst(child, childrenByParentId, visited, ordered);
        }
    }

    private List<FrameContent> buildFrameContents(List<FrameDocument> frameDocuments, boolean onlyMainContent) {
        if (frameDocuments == null || frameDocuments.isEmpty()) {
            return List.of();
        }

        List<FrameContent> frameContents = new ArrayList<>(frameDocuments.size());
        for (FrameDocument frameDocument : frameDocuments) {
            String frameUrl = StringUtils.isBlank(frameDocument.url()) ? request.getUrl() : frameDocument.url();
            String cleanedHtml = htmlMainContentCleaner.clean(
                frameDocument.html(),
                frameUrl,
                onlyMainContent,
                scrapeProperties.isStripChromeTags(),
                scrapeProperties.isRemoveBase64Images()
            );
            String fallbackHtml = onlyMainContent
                ? htmlMainContentCleaner.clean(
                    frameDocument.html(),
                    frameUrl,
                    false,
                    scrapeProperties.isStripChromeTags(),
                    scrapeProperties.isRemoveBase64Images()
                )
                : cleanedHtml;
            frameContents.add(new FrameContent(
                frameDocument.id(),
                frameDocument.parentId(),
                frameUrl,
                cleanedHtml,
                fallbackHtml,
                frameDocument.depth(),
                frameDocument.order()
            ));
        }
        return frameContents;
    }

    private String mergeHtml(String cleanedMainHtml, List<FrameContent> frameContents) {
        if (frameContents == null || frameContents.isEmpty()) {
            return cleanedMainHtml;
        }

        StringBuilder merged = new StringBuilder(cleanedMainHtml == null ? "" : cleanedMainHtml);
        for (FrameContent frameContent : frameContents) {
            String frameHtml = selectFrameHtml(frameContent);
            if (StringUtils.isBlank(frameHtml)) {
                continue;
            }
            String frameUrl = normalizeFrameTitle(frameContent.url());
            if (merged.length() > 0) {
                merged.append("\n");
            }
            merged.append("<section data-mmh-frame=\"true\" data-mmh-frame-url=\"")
                .append(escapeHtmlAttribute(frameUrl))
                .append("\"><h2>Frame Content: ")
                .append(escapeHtmlText(frameUrl))
                .append("</h2>")
                .append(frameHtml)
                .append("</section>");
        }
        return merged.toString();
    }

    private String mergeMarkdown(String mainMarkdown, List<FrameContent> frameContents, boolean onlyMainContent) {
        StringBuilder merged = new StringBuilder();
        if (StringUtils.isNotBlank(mainMarkdown)) {
            merged.append(mainMarkdown.trim());
        }

        if (frameContents == null || frameContents.isEmpty()) {
            return merged.toString();
        }

        List<RenderedFrameMarkdown> renderedFrameMarkdowns = new ArrayList<>();
        for (FrameContent frameContent : frameContents) {
            String frameMarkdown = renderMarkdownWithFallback(
                frameContent.cleanedHtml(),
                frameContent.fallbackHtml(),
                frameContent.url(),
                onlyMainContent
            );
            if (StringUtils.isBlank(frameMarkdown)) {
                continue;
            }
            frameMarkdown = normalizeFrameMarkdown(frameMarkdown, onlyMainContent);
            if (!shouldIncludeFrameMarkdown(frameMarkdown, onlyMainContent)) {
                continue;
            }
            renderedFrameMarkdowns.add(new RenderedFrameMarkdown(frameContent, frameMarkdown.trim()));
        }

        if (renderedFrameMarkdowns.isEmpty()) {
            return merged.toString().trim();
        }

        if (merged.length() > 0) {
            merged.append("\n\n");
        }
        merged.append("## Embedded Frame Contents");

        Map<String, String> sectionByFrameId = new HashMap<>();
        Map<String, Integer> siblingCounters = new HashMap<>();

        for (RenderedFrameMarkdown renderedFrameMarkdown : renderedFrameMarkdowns) {
            FrameContent frameContent = renderedFrameMarkdown.frameContent();
            String sectionNumber = buildFrameSectionNumber(frameContent, sectionByFrameId, siblingCounters);
            int headingLevel = Math.min(6, 2 + Math.max(1, frameContent.depth()));

            merged.append("\n\n")
                .append(markdownHeading(headingLevel))
                .append(" Frame ")
                .append(sectionNumber)
                .append(": ")
                .append(resolveFrameDisplayTitle(frameContent.url()))
                .append("\n\n")
                .append(renderedFrameMarkdown.markdown());
        }

        return merged.toString().trim();
    }

    private String normalizeFrameMarkdown(String frameMarkdown, boolean onlyMainContent) {
        if (!onlyMainContent || StringUtils.isBlank(frameMarkdown)) {
            return frameMarkdown;
        }
        return trimLeadingUiNoise(frameMarkdown);
    }

    private String trimLeadingUiNoise(String markdown) {
        String[] lines = markdown.split("\n", -1);
        int removalEnd = 0;
        int removedNonBlank = 0;
        int inspectedNonBlank = 0;

        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.isEmpty()) {
                if (removedNonBlank > 0) {
                    removalEnd = i + 1;
                }
                continue;
            }

            if (inspectedNonBlank >= 12 || !isLikelyUiNoiseLine(trimmed)) {
                break;
            }

            inspectedNonBlank++;
            removedNonBlank++;
            removalEnd = i + 1;
        }

        if (removedNonBlank < 3) {
            return markdown;
        }

        int remainingNonBlank = 0;
        for (int i = removalEnd; i < lines.length; i++) {
            if (!lines[i].trim().isEmpty()) {
                remainingNonBlank++;
            }
        }
        if (remainingNonBlank < 3) {
            return markdown;
        }

        StringBuilder trimmed = new StringBuilder();
        for (int i = removalEnd; i < lines.length; i++) {
            trimmed.append(lines[i]);
            if (i < lines.length - 1) {
                trimmed.append("\n");
            }
        }
        return trimmed.toString().stripLeading();
    }

    private boolean isLikelyUiNoiseLine(String line) {
        if (line.length() > 14) {
            return false;
        }
        if (line.startsWith("#")
            || line.startsWith("- ")
            || line.startsWith("* ")
            || line.startsWith(">")
            || line.startsWith("|")
            || line.startsWith("```")
            || line.startsWith("![")
            || line.startsWith("[")
        ) {
            return false;
        }
        if (line.contains("http://") || line.contains("https://")) {
            return false;
        }
        if (line.matches(".*[。！？.!?;；].*")) {
            return false;
        }
        return line.matches("[\\p{L}\\p{N}\\p{IsHan}\\s_\\-+()/（）【】\\[\\]、，,:：|]+$");
    }

    private boolean shouldIncludeFrameMarkdown(String frameMarkdown, boolean onlyMainContent) {
        if (StringUtils.isBlank(frameMarkdown)) {
            return false;
        }
        if (!onlyMainContent) {
            return true;
        }

        String[] lines = frameMarkdown.split("\n");
        int nonBlankLines = 0;
        int totalChars = 0;
        int longLines = 0;
        int sentenceLines = 0;
        int structuralLines = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            nonBlankLines++;
            totalChars += trimmed.length();

            if (trimmed.length() >= 28) {
                longLines++;
            }
            if (trimmed.matches(".*[。！？.!?;；].*")) {
                sentenceLines++;
            }
            if (trimmed.startsWith("#")
                || trimmed.startsWith("- ")
                || trimmed.startsWith("* ")
                || trimmed.startsWith("|")
                || trimmed.startsWith(">")
                || trimmed.startsWith("```")
                || trimmed.startsWith("![")
            ) {
                structuralLines++;
            }
        }

        if (nonBlankLines == 0) {
            return false;
        }
        if (longLines > 0 || sentenceLines > 0 || structuralLines >= 3) {
            return true;
        }
        double averageLineLength = totalChars / (double) nonBlankLines;
        return !(nonBlankLines <= 20 && averageLineLength < 10.0);
    }

    private String buildFrameSectionNumber(
        FrameContent frameContent,
        Map<String, String> sectionByFrameId,
        Map<String, Integer> siblingCounters
    ) {
        String parentId = frameContent.parentId();
        String counterKey = StringUtils.isBlank(parentId) ? "root" : parentId;
        int siblingIndex = siblingCounters.merge(counterKey, 1, Integer::sum);

        String parentSection = StringUtils.isBlank(parentId) ? null : sectionByFrameId.get(parentId);
        String sectionNumber = StringUtils.isBlank(parentSection)
            ? String.valueOf(siblingIndex)
            : parentSection + "." + siblingIndex;
        sectionByFrameId.put(frameContent.id(), sectionNumber);
        return sectionNumber;
    }

    private String markdownHeading(int level) {
        int normalizedLevel = Math.min(6, Math.max(1, level));
        return "#".repeat(normalizedLevel);
    }

    private String resolveFrameDisplayTitle(String frameUrl) {
        return StringUtils.isBlank(frameUrl) ? "unknown" : frameUrl;
    }

    private List<String> extractLinks(String cleanedMainHtml, List<FrameContent> frameContents) {
        Set<String> deduplicatedLinks = new LinkedHashSet<>();
        List<String> mainLinks = linkExtractor.extract(cleanedMainHtml, request.getUrl());
        if (mainLinks != null && !mainLinks.isEmpty()) {
            deduplicatedLinks.addAll(mainLinks);
        }

        if (frameContents != null && !frameContents.isEmpty()) {
            for (FrameContent frameContent : frameContents) {
                String frameHtml = selectFrameHtml(frameContent);
                if (StringUtils.isBlank(frameHtml)) {
                    continue;
                }
                List<String> frameLinks = linkExtractor.extract(frameHtml, frameContent.url());
                if (frameLinks != null && !frameLinks.isEmpty()) {
                    deduplicatedLinks.addAll(frameLinks);
                }
            }
        }

        return new ArrayList<>(deduplicatedLinks);
    }

    private String renderMarkdownWithFallback(String cleanedHtml, String fallbackHtml, String baseUrl, boolean onlyMainContent) {
        String markdown = markdownPostProcessor.process(markdownRenderer.render(cleanedHtml, baseUrl));
        if (onlyMainContent && StringUtils.isBlank(markdown)) {
            markdown = markdownPostProcessor.process(markdownRenderer.render(fallbackHtml, baseUrl));
        }
        return markdown;
    }

    private String selectFrameHtml(FrameContent frameContent) {
        if (StringUtils.isNotBlank(frameContent.cleanedHtml())) {
            return frameContent.cleanedHtml();
        }
        return frameContent.fallbackHtml();
    }

    private String normalizeFrameTitle(String frameUrl) {
        return StringUtils.isBlank(frameUrl) ? "unknown" : frameUrl;
    }

    private String escapeHtmlAttribute(String value) {
        return value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }

    private String escapeHtmlText(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }

    private static String toBase64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static String toDataUri(String mime, byte[] bytes) {
        return "data:" + mime + ";base64," + toBase64(bytes);
    }

    private DirectMedia tryFetchDirectMedia(Page page, String url) {
        APIResponse response = null;
        try {
            double probeTimeoutMs = Math.max(500, scrapeProperties.getDirectMediaProbeTimeoutMs());
            response = page.request().get(
                url,
                RequestOptions.create()
                    .setTimeout(probeTimeoutMs)
                    .setMaxRedirects(5)
            );
            if (response == null || !response.ok()) {
                return null;
            }

            Map<String, String> headers = response.headers();
            String mime = resolveMime(headers);
            String contentDisposition = findHeader(headers, "content-disposition");
            if (!isDirectMediaResponse(mime, contentDisposition, url)) {
                return null;
            }

            byte[] body = response.body();
            if (body == null || body.length == 0) {
                return null;
            }

            return new DirectMedia(mime, toDataUri(mime, body));
        } catch (Exception ex) {
            log.debug("detect direct media failed, url={}, error={}", url, ex.getMessage());
            return null;
        } finally {
            if (response != null) {
                try {
                    response.dispose();
                } catch (Exception ex) {
                    log.debug("dispose api response failed, url={}, error={}", url, ex.getMessage());
                }
            }
        }
    }

    private String resolveMime(Map<String, String> headers) {
        String contentType = findHeader(headers, "content-type");
        if (StringUtils.isBlank(contentType)) {
            return "application/octet-stream";
        }
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        int semicolonIndex = normalized.indexOf(';');
        if (semicolonIndex >= 0) {
            normalized = normalized.substring(0, semicolonIndex).trim();
        }
        return StringUtils.isBlank(normalized) ? "application/octet-stream" : normalized;
    }

    private String findHeader(Map<String, String> headers, String name) {
        if (headers == null || headers.isEmpty()) {
            return "";
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue() == null ? "" : entry.getValue();
            }
        }
        return "";
    }

    private boolean isDirectMediaResponse(String mime, String contentDisposition, String url) {
        if (StringUtils.isNotBlank(contentDisposition)
            && contentDisposition.toLowerCase(Locale.ROOT).contains("attachment")) {
            return true;
        }
        if ("application/octet-stream".equals(mime)) {
            return hasMediaLikeFileExtension(url);
        }
        return mime.startsWith("image/")
            || mime.startsWith("video/")
            || mime.startsWith("audio/")
            || "application/pdf".equals(mime);
    }

    private boolean hasMediaLikeFileExtension(String url) {
        if (StringUtils.isBlank(url)) {
            return false;
        }
        String lowerUrl = url.toLowerCase(Locale.ROOT);
        int fragmentIndex = lowerUrl.indexOf('#');
        if (fragmentIndex >= 0) {
            lowerUrl = lowerUrl.substring(0, fragmentIndex);
        }
        int queryIndex = lowerUrl.indexOf('?');
        if (queryIndex >= 0) {
            lowerUrl = lowerUrl.substring(0, queryIndex);
        }
        return lowerUrl.endsWith(".png")
            || lowerUrl.endsWith(".jpg")
            || lowerUrl.endsWith(".jpeg")
            || lowerUrl.endsWith(".webp")
            || lowerUrl.endsWith(".gif")
            || lowerUrl.endsWith(".bmp")
            || lowerUrl.endsWith(".svg")
            || lowerUrl.endsWith(".mp4")
            || lowerUrl.endsWith(".mov")
            || lowerUrl.endsWith(".mkv")
            || lowerUrl.endsWith(".webm")
            || lowerUrl.endsWith(".mp3")
            || lowerUrl.endsWith(".wav")
            || lowerUrl.endsWith(".flac")
            || lowerUrl.endsWith(".m4a")
            || lowerUrl.endsWith(".pdf");
    }

    private record FrameDocument(
        String id,
        String parentId,
        String url,
        String html,
        int depth,
        int order
    ) {

    }

    private record FrameContent(
        String id,
        String parentId,
        String url,
        String cleanedHtml,
        String fallbackHtml,
        int depth,
        int order
    ) {

    }

    private record StabilitySnapshot(int totalTextLength, long contentFingerprint, long semanticFingerprint) {

    }

    private record RenderedFrameMarkdown(FrameContent frameContent, String markdown) {

    }

    private record DirectMedia(String mime, String dataUri) {

    }

}
