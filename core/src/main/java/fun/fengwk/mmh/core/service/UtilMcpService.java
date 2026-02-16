package fun.fengwk.mmh.core.service;

import fun.fengwk.mmh.core.facade.search.model.SearchResponse;

/**
 * @author fengwk
 */
public interface UtilMcpService {

    SearchResponse search(String query, Integer limit, String timeRange, Integer page);

    String createTempDir();

}
