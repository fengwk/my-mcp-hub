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

    /**
     * Screenshot data URI: data:image/png;base64,...
     */
    private String screenshotBase64;

    /**
     * Screenshot mime type, currently image/png.
     */
    private String screenshotMime;

    private Long elapsedMs;
    private String error;

}
