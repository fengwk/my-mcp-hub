package fun.fengwk.mmh.core.service.scrape;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Scrape tool specific configuration.
 *
 * @author fengwk
 */
@Data
@Component
@ConfigurationProperties(prefix = "mmh.scrape")
public class ScrapeProperties {

    /**
     * Default profile used by scrape requests.
     */
    private String defaultProfileId = "master";

    /**
     * Page navigate timeout in milliseconds.
     */
    private int navigateTimeoutMs = 30000;

    /**
     * Root directory for persistent profile used by manual login.
     */
    private String masterUserDataRoot = System.getProperty("user.home") + "/.mmh/browser-data";

}
