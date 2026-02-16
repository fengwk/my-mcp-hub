package fun.fengwk.mmh.core.facade.search.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Normalized search response.
 *
 * @author fengwk
 */
@Data
@Builder
public class SearchResponse {

    /**
     * HTTP status code from upstream search.
     */
    private int statusCode;

    /**
     * Query text after normalization.
     */
    private String query;

    /**
     * Total number of results reported by upstream.
     */
    private Integer numberOfResults;

    /**
     * Search result items (possibly truncated by limit).
     */
    private List<SearchResultItem> results;

    /**
     * Error message when request fails or parse error occurs.
     */
    private String error;

}
