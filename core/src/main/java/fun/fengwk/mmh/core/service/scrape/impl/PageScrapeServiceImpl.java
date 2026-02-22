package fun.fengwk.mmh.core.service.scrape.impl;

import fun.fengwk.mmh.core.service.browser.runtime.BrowserTaskExecutor;
import fun.fengwk.mmh.core.service.scrape.PageScrapeService;
import fun.fengwk.mmh.core.service.scrape.ScrapeProperties;
import fun.fengwk.mmh.core.service.scrape.model.ScrapeFormat;
import fun.fengwk.mmh.core.service.scrape.model.ScrapeRequest;
import fun.fengwk.mmh.core.service.scrape.model.ScrapeResponse;
import fun.fengwk.mmh.core.service.scrape.parser.HtmlMainContentCleaner;
import fun.fengwk.mmh.core.service.scrape.parser.LinkExtractor;
import fun.fengwk.mmh.core.service.scrape.parser.MarkdownPostProcessor;
import fun.fengwk.mmh.core.service.scrape.parser.MarkdownRenderer;
import fun.fengwk.convention4j.common.lang.StringUtils;
import fun.fengwk.mmh.core.service.scrape.runtime.ScrapeBrowserTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Scrape service implementation.
 *
 * @author fengwk
 */
@Component
@RequiredArgsConstructor
public class PageScrapeServiceImpl implements PageScrapeService {

    private final BrowserTaskExecutor browserTaskExecutor;
    private final ScrapeProperties scrapeProperties;
    private final HtmlMainContentCleaner htmlMainContentCleaner;
    private final MarkdownRenderer markdownRenderer;
    private final MarkdownPostProcessor markdownPostProcessor;
    private final LinkExtractor linkExtractor;

    @Override
    public ScrapeResponse scrape(ScrapeRequest request) {
        long startAt = System.currentTimeMillis();
        try {
            validateRequest(request);
            ScrapeFormat format = ScrapeFormat.fromValue(request.getFormat());
            String profileId = scrapeProperties.getDefaultProfileId();
            ScrapeBrowserTask scrapeTask = new ScrapeBrowserTask(
                request,
                format,
                scrapeProperties,
                htmlMainContentCleaner,
                markdownRenderer,
                markdownPostProcessor,
                linkExtractor
            );
            ScrapeResponse response = browserTaskExecutor.execute(profileId, scrapeTask);
            if (response == null) {
                return ScrapeResponse.builder()
                    .statusCode(500)
                    .error("scrape response is null")
                    .elapsedMs(System.currentTimeMillis() - startAt)
                    .build();
            }
            response.setElapsedMs(System.currentTimeMillis() - startAt);
            return response;
        } catch (IllegalArgumentException ex) {
            return ScrapeResponse.builder()
                .statusCode(400)
                .error(ex.getMessage())
                .elapsedMs(System.currentTimeMillis() - startAt)
                .build();
        } catch (Exception ex) {
            return ScrapeResponse.builder()
                .statusCode(500)
                .error(ex.getMessage())
                .elapsedMs(System.currentTimeMillis() - startAt)
                .build();
        }
    }

    private void validateRequest(ScrapeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is null");
        }
        if (StringUtils.isBlank(request.getUrl())) {
            throw new IllegalArgumentException("url is blank");
        }
        String normalizedUrl = request.getUrl().trim().toLowerCase();
        if (!normalizedUrl.startsWith("http://") && !normalizedUrl.startsWith("https://")) {
            throw new IllegalArgumentException("unsupported url protocol");
        }
        if (request.getWaitFor() != null && (request.getWaitFor() < 0 || request.getWaitFor() > 60000)) {
            throw new IllegalArgumentException("waitFor out of range");
        }
        ScrapeFormat.fromValue(request.getFormat());
    }

}
