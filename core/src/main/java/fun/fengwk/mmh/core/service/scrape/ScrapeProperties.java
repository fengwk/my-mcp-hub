package fun.fengwk.mmh.core.service.scrape;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Scrape pipeline specific configuration.
 *
 * @author fengwk
 */
@Data
@Component
@ConfigurationProperties(prefix = "mmh.scrape")
public class ScrapeProperties {

    /**
     * Page navigate timeout in milliseconds.
     */
    private int navigateTimeoutMs = 30000;

    /**
     * Strip chrome tags like header/footer/nav when onlyMainContent is true.
     */
    private boolean stripChromeTags = true;

    /**
     * Remove embedded base64/data images from markdown.
     */
    private boolean removeBase64Images = true;

    /**
     * Request timeout in milliseconds for direct-media probe.
     */
    private int directMediaProbeTimeoutMs = 3000;

    /**
     * Enable smart wait (content stability detection) when waitFor is not provided.
     */
    private boolean smartWaitEnabled = true;

    /**
     * Smart wait polling interval in milliseconds.
     */
    private int stabilityCheckIntervalMs = 1000;

    /**
     * Smart wait maximum duration in milliseconds.
     */
    private int stabilityMaxWaitMs = 15000;

    /**
     * Smart wait stable rounds required before considered settled.
     */
    private int stabilityThreshold = 2;

}
