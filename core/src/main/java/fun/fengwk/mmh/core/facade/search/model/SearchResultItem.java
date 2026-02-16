package fun.fengwk.mmh.core.facade.search.model;

import lombok.Builder;
import lombok.Data;

/**
 * Single search result item.
 *
 * @author fengwk
 */
@Data
@Builder
public class SearchResultItem {

    /**
     * Result title.
     */
    private String title;

    /**
     * Result URL.
     */
    private String url;

    /**
     * Result snippet/content.
     */
    private String content;

}
