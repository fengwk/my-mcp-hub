package fun.fengwk.mmh.core.service.browser;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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
    private String snapshotRoot = System.getProperty("user.home") + "/.mmh/browser-snapshots";

    /**
     * Min worker count per MCP process.
     */
    private int workerPoolMinSizePerProcess = 1;

    /**
     * Max worker count per MCP process.
     */
    private int workerPoolMaxSizePerProcess = Runtime.getRuntime().availableProcessors();

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

}
