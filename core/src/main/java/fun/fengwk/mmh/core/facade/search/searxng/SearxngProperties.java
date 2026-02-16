package fun.fengwk.mmh.core.facade.search.searxng;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * SearXNG configuration.
 *
 * @author fengwk
 */
@Data
@Component
@ConfigurationProperties(prefix = "mmh.search.searxng")
public class SearxngProperties {

    /**
     * SearXNG base url.
     */
    private String baseUrl = "";

    /**
     * Request timeout in milliseconds.
     */
    private int timeoutMs = 10000;

    /**
     * Request method: GET/POST.
     */
    private String method = "POST";

}
