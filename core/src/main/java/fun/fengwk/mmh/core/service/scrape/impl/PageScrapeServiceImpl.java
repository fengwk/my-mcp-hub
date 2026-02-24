package fun.fengwk.mmh.core.service.scrape.impl;

import fun.fengwk.mmh.core.service.browser.runtime.BrowserTaskExecutor;
import fun.fengwk.mmh.core.service.scrape.PageScrapeService;
import fun.fengwk.mmh.core.service.scrape.ScrapeProperties;
import fun.fengwk.mmh.core.service.scrape.model.ScrapeProfileMode;
import fun.fengwk.mmh.core.service.scrape.model.ScrapeFormat;
import fun.fengwk.mmh.core.service.scrape.model.ScrapeRequest;
import fun.fengwk.mmh.core.service.scrape.model.ScrapeResponse;
import fun.fengwk.mmh.core.service.scrape.parser.HtmlMainContentCleaner;
import fun.fengwk.mmh.core.service.scrape.parser.LinkExtractor;
import fun.fengwk.mmh.core.service.scrape.parser.MarkdownPostProcessor;
import fun.fengwk.mmh.core.service.scrape.parser.MarkdownRenderer;
import fun.fengwk.convention4j.common.lang.StringUtils;
import fun.fengwk.mmh.core.service.scrape.runtime.MasterProfileLockedException;
import fun.fengwk.mmh.core.service.scrape.runtime.MasterProfileTaskExecutor;
import fun.fengwk.mmh.core.service.scrape.runtime.ScrapeBrowserTask;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Scrape service implementation.
 *
 * @author fengwk
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PageScrapeServiceImpl implements PageScrapeService {

    private static final String MASTER_PROFILE_LOCKED_MESSAGE =
        "Master profile is in use and temporarily unavailable; please retry later or use default mode.";

    private final BrowserTaskExecutor browserTaskExecutor;
    private final MasterProfileTaskExecutor masterProfileTaskExecutor;
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
            ScrapeProfileMode profileMode = ScrapeProfileMode.fromValue(request.getProfileMode());
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
            ScrapeResponse response = profileMode == ScrapeProfileMode.MASTER
                ? masterProfileTaskExecutor.execute(profileId, scrapeTask)
                : browserTaskExecutor.execute(profileId, scrapeTask);
            if (response == null) {
                log.warn("scrape response is null, url={}", request.getUrl());
                return ScrapeResponse.builder()
                    .statusCode(500)
                    .error("scrape response is null")
                    .elapsedMs(System.currentTimeMillis() - startAt)
                    .build();
            }
            response.setElapsedMs(System.currentTimeMillis() - startAt);
            return response;
        } catch (MasterProfileLockedException ex) {
            log.warn("master profile locked, url={}", request == null ? "" : request.getUrl());
            return ScrapeResponse.builder()
                .statusCode(500)
                .error(MASTER_PROFILE_LOCKED_MESSAGE)
                .elapsedMs(System.currentTimeMillis() - startAt)
                .build();
        } catch (IllegalArgumentException ex) {
            log.warn("scrape request invalid, url={}, error={}", request == null ? "" : request.getUrl(), ex.getMessage());
            return ScrapeResponse.builder()
                .statusCode(400)
                .error(ex.getMessage())
                .elapsedMs(System.currentTimeMillis() - startAt)
                .build();
        } catch (Exception ex) {
            log.warn("scrape failed, url={}, error={}", request == null ? "" : request.getUrl(), ex.getMessage());
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
        ScrapeProfileMode.fromValue(request.getProfileMode());
    }

}
