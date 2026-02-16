package fun.fengwk.mmh.core.facade.search.model;

import lombok.Data;

/**
 * Search request input.
 *
 * @author fengwk
 */
@Data
public class SearchRequest {

    /**
     * Search keywords with optional engine operators.
     */
    private String query;

    /**
     * Max number of results to return (local truncation, default 10).
     */
    private Integer limit;

    /**
     * Time range filter, day/week/month/year (empty means no filter).
     */
    private String timeRange;

    /**
     * Page number, starting from 1 (default 1).
     */
    private Integer page;

}
