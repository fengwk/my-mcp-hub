package fun.fengwk.mmh.core.service.browser.runtime;

import lombok.Builder;
import lombok.Data;

/**
 * Configuration for a worker pool.
 *
 * @author fengwk
 */
@Data
@Builder
public class WorkerPoolConfig {

    /**
     * Minimum worker count (0 means scale to zero when idle).
     */
    @Builder.Default
    private int minWorkers = 1;

    /**
     * Maximum worker count.
     */
    @Builder.Default
    private int maxWorkers = 5;

    /**
     * Timeout when waiting for an available worker.
     */
    @Builder.Default
    private long queueTimeoutMs = 15000;

}
