package fun.fengwk.mmh.core.service.browser;

import fun.fengwk.convention4j.common.lang.StringUtils;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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

    /**
     * Snapshot root directory.
     */
    private String snapshotRoot = System.getProperty("user.home") + "/.my-mcp-hub/browser-snapshots";

    /**
     * Min worker count per MCP process.
     */
    private int workerPoolMinSizePerProcess = 1;

    /**
     * Max worker count per MCP process.
     */
    private int workerPoolMaxSizePerProcess = 5;

    /**
     * Request queue capacity.
     */
    private int requestQueueCapacity = 200;

    /**
     * Worker poll interval for refresh/idle checks.
     */
    private long workerRefreshIntervalMs = 5000;

    /**
     * Release worker pool when globally idle longer than ttl.
     */
    private long workerIdleTtlMs = 300000;

    /**
     * Timeout when waiting for an idle worker.
     */
    private int queueOfferTimeoutMs = 200;

    /**
     * Whether to refresh snapshot when request ends.
     */
    private boolean snapshotRefreshOnRequestEnd = true;

    /**
     * Snapshot publish lock timeout.
     */
    private int snapshotPublishLockTimeoutMs = 500;

    /**
     * Regex for profile id validation.
     */
    private String profileIdRegex = "^[a-zA-Z0-9._-]{1,64}$";

    /**
     * Optional fixed user agent for browser context.
     */
    private String userAgent = "";

    /**
     * User agent pool for random rotation.
     */
    private List<String> userAgents = List.of(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Safari/605.1.15",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    );

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

}
