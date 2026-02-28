package fun.fengwk.mmh.core.service.scrape.impl;

import fun.fengwk.mmh.core.service.browser.runtime.BrowserTaskExecutor;
import fun.fengwk.mmh.core.service.browser.runtime.ProfileType;
import fun.fengwk.mmh.core.service.scrape.PageScrapeService;
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
import fun.fengwk.convention4j.common.lang.StringUtils;
import fun.fengwk.mmh.core.service.scrape.runtime.MasterProfileLockedException;
import fun.fengwk.mmh.core.service.scrape.runtime.ScrapeBrowserTask;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Locale;

/**
 * Scrape service implementation.
 *
 * @author fengwk
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PageScrapeServiceImpl implements PageScrapeService {

    private static final String FORMAT_MEDIA = "media";

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
            ProfileType profileType = ProfileType.fromValue(request.getProfileMode());

            // Low-cost HttpClient path for media resources (default mode only)
            if (profileType == ProfileType.DEFAULT && ScrapeMediaUtils.hasMediaLikeFileExtension(request.getUrl())) {
                ScrapeResponse quickResponse = tryQuickScrapeMedia(request.getUrl());
                if (quickResponse != null) {
                    quickResponse.setElapsedMs(System.currentTimeMillis() - startAt);
                    return quickResponse;
                }
            }

            ScrapeBrowserTask scrapeTask = new ScrapeBrowserTask(
                request,
                format,
                scrapeProperties,
                htmlMainContentCleaner,
                markdownRenderer,
                markdownPostProcessor,
                linkExtractor
            );
            ScrapeResponse response = browserTaskExecutor.execute(profileType, scrapeTask);
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
        String normalizedUrl = request.getUrl();
        if (StringUtils.isBlank(normalizedUrl)) {
            throw new IllegalArgumentException("url is blank");
        }
        normalizedUrl = normalizedUrl.trim();
        request.setUrl(normalizedUrl);

        String lowerCaseUrl = normalizedUrl.toLowerCase(Locale.ROOT);
        if (!lowerCaseUrl.startsWith("http://") && !lowerCaseUrl.startsWith("https://")) {
            throw new IllegalArgumentException("unsupported url protocol");
        }
        if (request.getWaitFor() != null && (request.getWaitFor() < 0 || request.getWaitFor() > 60000)) {
            throw new IllegalArgumentException("waitFor out of range");
        }
        ScrapeFormat.fromValue(request.getFormat());
        ProfileType.fromValue(request.getProfileMode());
    }

    private ScrapeResponse tryQuickScrapeMedia(String url) {
        try {
            ScrapeHttpUtils.HttpBytesResponse response = ScrapeHttpUtils.tryFetchBytesWithRetry(
                url,
                scrapeProperties.getDirectMediaProbeTimeoutMs()
            );
            if (response == null || response.statusCode() < 200 || response.statusCode() >= 300) {
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

            return ScrapeResponse.builder()
                .statusCode(200)
                .format(FORMAT_MEDIA)
                .screenshotMime(mime)
                .screenshotBase64("data:" + mime + ";base64," + Base64.getEncoder().encodeToString(body))
                .build();
        } catch (Exception ex) {
            log.debug("quick media scrape failed, url={}, error={}", url, ex.getMessage());
            return null;
        }
    }

}
