package fun.fengwk.mmh.core.service.scrape.runtime;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
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
import fun.fengwk.mmh.core.service.scrape.support.ScrapeHttpUtils;
import fun.fengwk.mmh.core.service.scrape.support.ScrapeMediaUtils;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
        String requestUrl = request.getUrl();
        boolean mediaLikeUrl = ScrapeMediaUtils.hasMediaLikeFileExtension(requestUrl);

        DirectMedia directMedia = tryFetchDirectMedia(page, requestUrl);
        if (directMedia == null && mediaLikeUrl) {
            directMedia = tryFetchDirectMediaByHttp(requestUrl);
        }
        if (directMedia != null) {
            return toDirectMediaResponse(directMedia);
        }

        Response navigateResponse = null;
        try {
            navigateResponse = page.navigate(requestUrl,
                new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout((double) scrapeProperties.getNavigateTimeoutMs())
            );
        } catch (Exception ex) {
            if (isLikelyDownloadNavigationError(ex)) {
                DirectMedia fallbackDirectMedia = tryFetchDirectMediaByHttp(requestUrl);
                if (fallbackDirectMedia != null) {
                    return toDirectMediaResponse(fallbackDirectMedia);
                }
            }
            if (isLikelyAbortedNavigationError(ex)) {
                Integer statusCode = tryFetchHttpStatusByHttp(requestUrl);
                if (isNoContentStatus(statusCode) && supportsEmptyTextResult(format)) {
                    log.debug("return empty result for no-content response, url={}, statusCode={}, format={}", requestUrl, statusCode, format.getValue());
                    return toEmptyTextResponse(format);
                }
            }
            throw ex;
        }

        if (shouldSkipPostNavigateWait(navigateResponse, requestUrl)) {
            log.debug("skip post-navigate wait for error response, url={}", requestUrl);
        } else if (request.getWaitFor() != null && request.getWaitFor() > 0) {
            page.waitForTimeout(request.getWaitFor());
        } else if (scrapeProperties.isSmartWaitEnabled()) {
            waitForContentStable(page, requestUrl);
        } else {
            waitForNetworkIdleBestEffort(page, requestUrl);
        }

        String html = page.content();
        List<FrameDocument> frameDocuments = collectFrameDocuments(page, requestUrl);
        boolean onlyMainContent = request.getOnlyMainContent() != null && request.getOnlyMainContent();
        String cleanedHtml = htmlMainContentCleaner.clean(
            html,
            requestUrl,
            onlyMainContent,
            scrapeProperties.isStripChromeTags(),
            scrapeProperties.isRemoveBase64Images()
        );
        String fallbackHtml = onlyMainContent
            ? htmlMainContentCleaner.clean(
                html,
                requestUrl,
                false,
                scrapeProperties.isStripChromeTags(),
                scrapeProperties.isRemoveBase64Images()
            )
            : cleanedHtml;
        List<FrameContent> frameContents = buildFrameContents(frameDocuments, onlyMainContent, requestUrl);

        ScrapeResponse.ScrapeResponseBuilder builder = ScrapeResponse.builder()
            .statusCode(200)
            .format(format.getValue());

        switch (format) {
            case HTML:
                builder.content(mergeHtml(cleanedHtml, frameContents));
                break;
            case MARKDOWN:
                String markdown = renderMarkdownWithFallback(cleanedHtml, fallbackHtml, requestUrl, onlyMainContent);
                builder.content(mergeMarkdown(markdown, frameContents, onlyMainContent));
                break;
            case LINKS:
                builder.links(extractLinks(cleanedHtml, frameContents, requestUrl));
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

    private ScrapeResponse toDirectMediaResponse(DirectMedia directMedia) {
        return ScrapeResponse.builder()
            .statusCode(200)
            .format(FORMAT_MEDIA)
            .screenshotMime(directMedia.mime())
            .screenshotBase64(directMedia.dataUri())
            .build();
    }

    private boolean shouldSkipPostNavigateWait(Response navigateResponse, String requestUrl) {
        if (navigateResponse == null) {
            return false;
        }
        try {
            return navigateResponse.status() >= 400;
        } catch (Exception ex) {
            log.debug("read navigate response status failed, url={}, error={}", requestUrl, ex.getMessage());
            return false;
        }
    }

    private void waitForNetworkIdleBestEffort(Page page, String requestUrl) {
        waitForNetworkIdleBestEffort(page, scrapeProperties.getNavigateTimeoutMs(), true, requestUrl);
    }

    private void waitForNetworkIdleBestEffort(Page page, long timeoutMs, boolean warnOnTimeout, String requestUrl) {
        long effectiveTimeoutMs = Math.max(100L, timeoutMs);
        try {
            // NETWORKIDLE may never happen for long-polling pages, treat it as best-effort.
            page.waitForLoadState(
                LoadState.NETWORKIDLE,
                new Page.WaitForLoadStateOptions().setTimeout((double) effectiveTimeoutMs)
            );
        } catch (TimeoutError ex) {
            if (warnOnTimeout) {
                log.warn("network idle timeout, url={}, timeoutMs={}", requestUrl, effectiveTimeoutMs);
            } else {
                log.debug("pre smart-wait network idle timeout, url={}, timeoutMs={}", requestUrl, effectiveTimeoutMs);
            }
        }
    }

    private void waitForContentStable(Page page, String requestUrl) {
        int checkIntervalMs = Math.max(100, scrapeProperties.getStabilityCheckIntervalMs());
        int stableThreshold = Math.max(1, scrapeProperties.getStabilityThreshold());
        double lengthChangeThreshold = resolveLengthChangeThreshold();
        long stabilityMaxWaitMs = Math.max(checkIntervalMs, scrapeProperties.getStabilityMaxWaitMs());
        long maxWaitMs = Math.max(checkIntervalMs, Math.min(scrapeProperties.getNavigateTimeoutMs(), stabilityMaxWaitMs));
        long startedAt = System.currentTimeMillis();
        long networkIdleTimeoutMs = Math.min(
            maxWaitMs,
            Math.max((long) checkIntervalMs * stableThreshold, 1500L)
        );
        waitForNetworkIdleBestEffort(page, networkIdleTimeoutMs, false, requestUrl);

        long deadlineAt = startedAt + maxWaitMs;
        int stableRounds = 0;
        int unstableRounds = 0;
        int anchorTextLength = calculateTotalTextLength(page, requestUrl);

        while (System.currentTimeMillis() < deadlineAt) {
            page.waitForTimeout(checkIntervalMs);

            int currentTextLength = calculateTotalTextLength(page, requestUrl);
            if (isLengthStable(anchorTextLength, currentTextLength, lengthChangeThreshold)) {
                stableRounds++;
                unstableRounds = 0;
                anchorTextLength = currentTextLength;
                if (stableRounds >= stableThreshold) {
                    log.debug(
                        "smart wait settled by text-length stability, url={}, stableRounds={}, stableThreshold={}, lengthChangeThreshold={}, anchorTextLength={}",
                        requestUrl,
                        stableRounds,
                        stableThreshold,
                        lengthChangeThreshold,
                        anchorTextLength
                    );
                    return;
                }
            } else {
                stableRounds = 0;
                unstableRounds++;
                if (unstableRounds >= stableThreshold) {
                    anchorTextLength = currentTextLength;
                    unstableRounds = 0;
                    log.debug(
                        "smart wait re-anchored after unstable rounds, url={}, stableThreshold={}, anchorTextLength={}",
                        requestUrl,
                        stableThreshold,
                        anchorTextLength
                    );
                }
            }
        }

        log.warn(
            "smart wait timeout, url={}, maxWaitMs={}, stableThreshold={}, checkIntervalMs={}, lengthChangeThreshold={}",
            requestUrl,
            maxWaitMs,
            stableThreshold,
            checkIntervalMs,
            lengthChangeThreshold
        );
    }

    private double resolveLengthChangeThreshold() {
        double threshold = scrapeProperties.getStabilityLengthChangeThreshold();
        if (threshold > 1D) {
            threshold = threshold / 100D;
        }
        if (threshold <= 0D) {
            return 0.1D;
        }
        return Math.min(threshold, 1D);
    }

    private boolean isLengthStable(int previousLength, int currentLength, double lengthChangeThreshold) {
        if (previousLength == 0 && currentLength == 0) {
            return true;
        }
        if (previousLength <= 0 || currentLength <= 0) {
            return false;
        }
        double lengthChangeRatio = Math.abs(currentLength - previousLength) / (double) previousLength;
        return lengthChangeRatio <= lengthChangeThreshold;
    }

    private int calculateTotalTextLength(Page page, String requestUrl) {
        int totalLength = 0;

        String mainText = extractNormalizedText(page.content());
        if (StringUtils.isNotBlank(mainText)) {
            totalLength += mainText.length();
        }

        Frame mainFrame = null;
        try {
            mainFrame = page.mainFrame();
        } catch (Exception ex) {
            log.debug("get main frame failed, url={}, error={}", requestUrl, ex.getMessage());
        }

        List<Frame> frames;
        try {
            frames = page.frames();
        } catch (Exception ex) {
            log.debug("list frames failed, url={}, error={}", requestUrl, ex.getMessage());
            return totalLength;
        }
        if (frames == null || frames.isEmpty()) {
            return totalLength;
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
        }

        return totalLength;
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

    private List<FrameDocument> collectFrameDocuments(Page page, String requestUrl) {
        Frame mainFrame = safeMainFrame(page, requestUrl);
        List<Frame> frames = safeFrames(page, requestUrl);
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

    private Frame safeMainFrame(Page page, String requestUrl) {
        try {
            return page.mainFrame();
        } catch (Exception ex) {
            log.debug("get main frame failed when collect frame content, url={}, error={}", requestUrl, ex.getMessage());
            return null;
        }
    }

    private List<Frame> safeFrames(Page page, String requestUrl) {
        try {
            List<Frame> frames = page.frames();
            return frames == null ? List.of() : frames;
        } catch (Exception ex) {
            log.debug("list frames failed when collect frame content, url={}, error={}", requestUrl, ex.getMessage());
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

    private List<FrameContent> buildFrameContents(List<FrameDocument> frameDocuments, boolean onlyMainContent, String requestUrl) {
        if (frameDocuments == null || frameDocuments.isEmpty()) {
            return List.of();
        }

        List<FrameContent> frameContents = new ArrayList<>(frameDocuments.size());
        for (FrameDocument frameDocument : frameDocuments) {
            String frameUrl = StringUtils.isBlank(frameDocument.url()) ? requestUrl : frameDocument.url();
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
                frameDocument.depth()
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
            String frameUrl = resolveFrameDisplayTitle(frameContent.url());
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

    private List<String> extractLinks(String cleanedMainHtml, List<FrameContent> frameContents, String requestUrl) {
        Set<String> deduplicatedLinks = new LinkedHashSet<>();
        List<String> mainLinks = linkExtractor.extract(cleanedMainHtml, requestUrl);
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
            String mime = ScrapeMediaUtils.resolveMime(headers);
            String contentDisposition = ScrapeMediaUtils.findHeader(headers, "content-disposition");
            if (!ScrapeMediaUtils.isDirectMediaResponse(mime, contentDisposition, url)) {
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

    private boolean isLikelyDownloadNavigationError(Exception ex) {
        if (ex == null || StringUtils.isBlank(ex.getMessage())) {
            return false;
        }
        String message = ex.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("download is starting") || message.contains("net::err_aborted");
    }

    private boolean isLikelyAbortedNavigationError(Exception ex) {
        if (ex == null || StringUtils.isBlank(ex.getMessage())) {
            return false;
        }
        return ex.getMessage().toLowerCase(Locale.ROOT).contains("net::err_aborted");
    }

    private Integer tryFetchHttpStatusByHttp(String url) {
        return ScrapeHttpUtils.tryFetchStatusCodeWithRetry(url, scrapeProperties.getDirectMediaProbeTimeoutMs());
    }

    private boolean isNoContentStatus(Integer statusCode) {
        if (statusCode == null) {
            return false;
        }
        return statusCode == 204 || statusCode == 205 || statusCode == 304;
    }

    private boolean supportsEmptyTextResult(ScrapeFormat scrapeFormat) {
        return scrapeFormat == ScrapeFormat.HTML
            || scrapeFormat == ScrapeFormat.MARKDOWN
            || scrapeFormat == ScrapeFormat.LINKS;
    }

    private ScrapeResponse toEmptyTextResponse(ScrapeFormat scrapeFormat) {
        ScrapeResponse.ScrapeResponseBuilder builder = ScrapeResponse.builder()
            .statusCode(200)
            .format(scrapeFormat.getValue());
        if (scrapeFormat == ScrapeFormat.LINKS) {
            builder.links(List.of());
        } else {
            builder.content("");
        }
        return builder.build();
    }

    private DirectMedia tryFetchDirectMediaByHttp(String url) {
        ScrapeHttpUtils.HttpBytesResponse response = ScrapeHttpUtils.tryFetchBytesWithRetry(
            url,
            scrapeProperties.getDirectMediaProbeTimeoutMs()
        );
        if (response == null) {
            return null;
        }

        try {
            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                return null;
            }

            String mime = ScrapeMediaUtils.resolveMime(response.headers());
            String contentDisposition = ScrapeMediaUtils.findHeader(response.headers(), "content-disposition");
            if (!ScrapeMediaUtils.isDirectMediaResponse(mime, contentDisposition, url)) {
                return null;
            }

            byte[] body = response.body();
            if (body == null || body.length == 0) {
                return null;
            }

            return new DirectMedia(mime, toDataUri(mime, body));
        } catch (Exception ex) {
            log.debug("fallback direct media fetch failed, url={}, error={}", url, ex.getMessage());
            return null;
        }
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
        int depth
    ) {

    }

    private record RenderedFrameMarkdown(FrameContent frameContent, String markdown) {

    }

    private record DirectMedia(String mime, String dataUri) {

    }

}
