package fun.fengwk.mmh.core.service.impl;

import fun.fengwk.mmh.core.facade.search.SearchFacade;
import fun.fengwk.mmh.core.facade.search.model.SearchRequest;
import fun.fengwk.mmh.core.facade.search.model.SearchResponse;
import fun.fengwk.mmh.core.service.UtilMcpService;
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
    public String createTempDir() {
        try {
            Path tempDir = Files.createTempDirectory("mmh-");
            return tempDir.toAbsolutePath().toString();
        } catch (IOException e) {
            return "create temp dir error: " + e.getMessage();
        }
    }

}
