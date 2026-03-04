package fun.fengwk.mmh.core.service.browser;

import fun.fengwk.convention4j.common.lang.StringUtils;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Browser runtime shared configuration.
 *
 * @author fengwk
 */
@Data
@Component
@ConfigurationProperties(prefix = "mmh.browser")
public class BrowserProperties {

    private static final List<String> DEFAULT_USER_AGENTS = List.of(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Safari/605.1.15",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    );

    /**
     * Minimum worker count for default pool.
     */
    private int workerPoolMinSize = 0;

    /**
     * Maximum worker count for default pool.
     */
    private int workerPoolMaxSize = 5;

    /**
     * Timeout when waiting for an idle worker.
     */
    private int queueOfferTimeoutMs = 15000;

    /**
     * Regex for profile id validation.
     */
    private String profileIdRegex = "^[a-zA-Z0-9._-]{1,64}$";

    /**
     * Default profile id for browser tasks.
     */
    private String defaultProfileId = "master";

    /**
     * Extra browser args for manual master login command.
     */
    private List<String> masterLoginArgs = new ArrayList<>();

    /**
     * Browser launch settings for default profile mode.
     */
    private BrowserProfileProperties defaultProfile = new BrowserProfileProperties();

    /**
     * Browser launch settings for master profile mode.
     */
    private BrowserProfileProperties masterProfile = new BrowserProfileProperties();

    /**
     * Initial page url for manual master login.
     */
    private String masterLoginInitialPageUrl = "";

    /**
     * Navigate timeout for manual master login initial page.
     */
    private long masterLoginNavigateTimeoutMs = 30000;

    /**
     * Polling interval for manual master login page-state checks.
     */
    private long masterLoginRefreshIntervalMs = 0;

    /**
     * Timeout for manual master login, 0 means no timeout.
     */
    private long masterLoginTimeoutMs = 0;

    /**
     * Timeout for master profile lock in milliseconds, 0 means no wait.
     */
    private long masterProfileLockTimeoutMs = 2000;

    /**
     * Whether to enable stealth script.
     */
    private boolean stealthEnabled = true;

    /**
     * Optional stealth script, empty uses default.
     */
    private String stealthScript = "";

    public String resolveStealthScript() {
        if (!stealthEnabled) {
            return "";
        }
        if (StringUtils.isNotBlank(stealthScript)) {
            return stealthScript;
        }
        return BrowserStealthSupport.DEFAULT_STEALTH_SCRIPT;
    }

    public BrowserProfileProperties resolveDefaultProfile() {
        if (defaultProfile == null) {
            defaultProfile = new BrowserProfileProperties();
        }
        return defaultProfile;
    }

    public BrowserProfileProperties resolveMasterProfile() {
        if (masterProfile == null) {
            masterProfile = new BrowserProfileProperties();
        }
        return masterProfile;
    }

    @Data
    public static class BrowserProfileProperties {

        /**
         * Browser channel, e.g. chrome, msedge.
         */
        private String browserChannel = "";

        /**
         * Browser executable path.
         */
        private String executablePath = "";

        /**
         * Extra launch args for browser.
         */
        private List<String> launchArgs = List.of();

        /**
         * Ignore default args for browser launch.
         */
        private List<String> ignoreDefaultArgs = List.of("--enable-automation");

        /**
         * Ignore all default args for browser launch.
         */
        private boolean ignoreAllDefaultArgs = false;

        /**
         * Whether this profile runs in headless mode.
         */
        private boolean headless = true;

        /**
         * Optional fixed user agent for browser context.
         */
        private String userAgent = "";

        /**
         * User agent pool for random rotation.
         */
        private List<String> userAgents = DEFAULT_USER_AGENTS;

        /**
         * Optional Accept-Language header value.
         */
        private String acceptLanguage = "";

        /**
         * Optional locale for browser context.
         */
        private String locale = "";

        /**
         * Optional timezone id for browser context.
         */
        private String timezoneId = "";

        /**
         * Extra headers for browser context.
         */
        private Map<String, String> extraHeaders = Map.of();

        /**
         * Proxy server, for example http://proxy:8080.
         */
        private String proxyServer = "";

        /**
         * Proxy username.
         */
        private String proxyUsername = "";

        /**
         * Proxy password.
         */
        private String proxyPassword = "";

    }

}
