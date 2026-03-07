package fun.fengwk.mmh.core.service;

import fun.fengwk.mmh.core.facade.search.model.SearchResponse;
import fun.fengwk.mmh.core.service.model.CreateTempDirResponse;
import fun.fengwk.mmh.core.service.scrape.model.ScrapeResponse;

/**
 * @author fengwk
 */
public interface UtilMcpService {

    SearchResponse search(String query, Integer limit, String timeRange, Integer page);

    CreateTempDirResponse createTempDir();

    ScrapeResponse scrape(String url, String format, Boolean onlyMainContent, Integer waitFor, String profileMode);

}
