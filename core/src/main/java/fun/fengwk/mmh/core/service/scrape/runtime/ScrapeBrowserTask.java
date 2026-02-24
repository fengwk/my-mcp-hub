package fun.fengwk.mmh.core.service.scrape.runtime;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;
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

import java.util.Base64;

/**
 * Scrape task adapter over generic browser task runtime.
 *
 * @author fengwk
 */
@Slf4j
@RequiredArgsConstructor
public class ScrapeBrowserTask implements BrowserTask<ScrapeResponse> {

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
        page.navigate(request.getUrl(),
            new Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                .setTimeout((double) scrapeProperties.getNavigateTimeoutMs())
        );

        if (request.getWaitFor() != null && request.getWaitFor() > 0) {
            page.waitForTimeout(request.getWaitFor());
        } else {
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

        String html = page.content();
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

        ScrapeResponse.ScrapeResponseBuilder builder = ScrapeResponse.builder()
            .statusCode(200)
            .format(format.getValue());

        switch (format) {
            case HTML:
                builder.content(cleanedHtml);
                break;
            case MARKDOWN:
                String markdown = markdownPostProcessor.process(markdownRenderer.render(cleanedHtml, request.getUrl()));
                if (onlyMainContent && StringUtils.isBlank(markdown)) {
                    markdown = markdownPostProcessor.process(markdownRenderer.render(fallbackHtml, request.getUrl()));
                }
                builder.content(markdown);
                break;
            case LINKS:
                builder.links(linkExtractor.extract(cleanedHtml, request.getUrl()));
                break;
            case SCREENSHOT:
                builder.screenshotBase64(toBase64(page.screenshot()));
                break;
            case FULLSCREENSHOT:
                builder.screenshotBase64(toBase64(page.screenshot(
                    new Page.ScreenshotOptions().setFullPage(true)
                )));
                break;
            default:
                throw new IllegalArgumentException("unsupported format: " + format.getValue());
        }
        return builder.build();
    }

    private static String toBase64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

}
