package fun.fengwk.mmh.core.service.scrape;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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
     * Strip chrome tags like header/footer/nav when onlyMainContent is true.
     */
    private boolean stripChromeTags = true;

    /**
     * Remove embedded base64/data images from markdown.
     */
    private boolean removeBase64Images = true;

    /**
     * Root directory for persistent profile used by manual login.
     */
    private String masterUserDataRoot = System.getProperty("user.home") + "/.my-mcp-hub/browser-data";

    /**
     * Extra browser args for manual login.
     */
    private List<String> masterLoginArgs = new ArrayList<>();

    /**
     * Initial page url for manual login.
     */
    private String masterLoginInitialPageUrl;


    /**
     * Snapshot refresh interval for manual login.
     */
    private long masterLoginRefreshIntervalMs = 0;

    /**
     * Timeout for manual login, 0 means no timeout.
     */
    private long masterLoginTimeoutMs = 0;

    /**
     * Timeout for master profile lock in milliseconds, 0 means no wait.
     */
    private long masterProfileLockTimeoutMs = 2000;

}
