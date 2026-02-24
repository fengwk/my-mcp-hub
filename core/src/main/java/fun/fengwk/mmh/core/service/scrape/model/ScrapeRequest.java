package fun.fengwk.mmh.core.service.scrape.model;

import lombok.Builder;
import lombok.Data;

/**
 * Scrape request model.
 *
 * @author fengwk
 */
@Data
@Builder
public class ScrapeRequest {

    private String url;
    private String format;
    private String profileMode;
    private Boolean onlyMainContent;
    private Integer waitFor;

}
