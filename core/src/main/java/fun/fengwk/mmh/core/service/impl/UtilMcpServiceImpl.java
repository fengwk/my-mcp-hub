package fun.fengwk.mmh.core.service.impl;

import fun.fengwk.mmh.core.facade.search.SearchFacade;
import fun.fengwk.mmh.core.facade.search.model.SearchRequest;
import fun.fengwk.mmh.core.facade.search.model.SearchResponse;
import fun.fengwk.mmh.core.service.UtilMcpService;
import fun.fengwk.mmh.core.service.model.CreateTempDirResponse;
import fun.fengwk.mmh.core.service.scrape.PageScrapeService;
import fun.fengwk.mmh.core.service.scrape.model.ScrapeRequest;
import fun.fengwk.mmh.core.service.scrape.model.ScrapeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author fengwk
 */
@Component
@RequiredArgsConstructor
public class UtilMcpServiceImpl implements UtilMcpService {

    private final SearchFacade searchFacade;
    private final PageScrapeService pageScrapeService;

    @Override
    public SearchResponse search(String query, Integer limit, String timeRange, Integer page) {
        SearchRequest request = new SearchRequest();
        request.setQuery(query);
        request.setLimit(limit);
        request.setTimeRange(timeRange);
        request.setPage(page);

        return searchFacade.search(request);
    }

    @Override
    public CreateTempDirResponse createTempDir() {
        try {
            Path tempDir = Files.createTempDirectory("mmh-");
            return CreateTempDirResponse.builder()
                .path(tempDir.toAbsolutePath().toString())
                .build();
        } catch (IOException e) {
            return CreateTempDirResponse.builder()
                .error("create temp dir error: " + e.getMessage())
                .build();
        }
    }

    @Override
    public ScrapeResponse scrape(String url, String format, Boolean onlyMainContent, Integer waitFor, String profileMode) {
        ScrapeRequest request = ScrapeRequest.builder()
            .url(url)
            .format(format)
            .profileMode(profileMode)
            .onlyMainContent(onlyMainContent)
            .waitFor(waitFor)
            .build();
        return pageScrapeService.scrape(request);
    }

}
