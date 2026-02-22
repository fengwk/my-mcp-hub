package fun.fengwk.mmh.core.service.scrape.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Scrape response model.
 *
 * @author fengwk
 */
@Data
@Builder
public class ScrapeResponse {

    private int statusCode;
    private String format;
    private String content;
    private List<String> links;
    private String screenshotBase64;
    private Long elapsedMs;
    private String error;

}
