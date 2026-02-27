package fun.fengwk.mmh.core.service.scrape.impl;

import fun.fengwk.mmh.core.service.browser.runtime.BrowserTaskExecutor;
import fun.fengwk.mmh.core.service.browser.runtime.ProfileType;
import fun.fengwk.mmh.core.service.browser.BrowserProperties;
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
import fun.fengwk.mmh.core.service.scrape.runtime.MasterProfileLockedException;
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

    private final BrowserTaskExecutor browserTaskExecutor;
    private final BrowserProperties browserProperties;
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
            ProfileType profileType = ProfileType.fromValue(request.getProfileMode());
            String profileId = browserProperties.getDefaultProfileId();
            ScrapeBrowserTask scrapeTask = new ScrapeBrowserTask(
                request,
                format,
                scrapeProperties,
                htmlMainContentCleaner,
                markdownRenderer,
                markdownPostProcessor,
                linkExtractor
            );
            ScrapeResponse response = browserTaskExecutor.execute(profileId, profileType, scrapeTask);
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
            log.warn(
                "master profile locked, url={}, profileMode={}, error={}",
                request == null ? "" : request.getUrl(),
                request == null ? "" : request.getProfileMode(),
                ex.getMessage()
            );
            String error = StringUtils.isBlank(ex.getMessage()) ? MasterProfileLockedException.DEFAULT_MESSAGE : ex.getMessage();
            return ScrapeResponse.builder()
                .statusCode(500)
                .error(error)
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
            log.warn(
                "scrape failed, url={}, profileMode={}, format={}, error={}",
                request == null ? "" : request.getUrl(),
                request == null ? "" : request.getProfileMode(),
                request == null ? "" : request.getFormat(),
                ex.getMessage(),
                ex
            );
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
        ProfileType.fromValue(request.getProfileMode());
    }

}
