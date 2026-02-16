package fun.fengwk.mmh.core.facade.search;

import fun.fengwk.mmh.core.facade.search.model.SearchRequest;
import fun.fengwk.mmh.core.facade.search.model.SearchResponse;

/**
 * @author fengwk
 */
public interface SearchFacade {

    /**
     * Execute search with normalized request.
     */
    SearchResponse search(SearchRequest request);

}
