package fun.fengwk.mmh.core.service.browser.runtime;

import fun.fengwk.mmh.core.service.browser.BrowserProperties;
import fun.fengwk.mmh.core.service.browser.coordination.LoginLockManager;
import fun.fengwk.mmh.core.service.scrape.runtime.MasterProfileLockedException;
import fun.fengwk.convention4j.common.lang.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Master browser worker pool with a configured profile ID.
 *
 * <p>Master mode is serialized (max=1) and protected by a cross-process lock
 * on {@code userDataDir/browser.lock}. This keeps scrape runtime and manual login
 * command mutually exclusive on the same persistent profile.
 *
 * @author fengwk
 */
public class MasterBrowserWorkerPool extends BrowserWorkerPool {

    private static final Logger log = LoggerFactory.getLogger(MasterBrowserWorkerPool.class);

    private static final long LOCK_RETRY_INTERVAL_MS = 100;

    private final String masterProfileId;
    private final long lockTimeoutMs;

    public MasterBrowserWorkerPool(
        Path profileRoot,
        String masterProfileId,
        BrowserProperties browserProperties,
        LoginLockManager loginLockManager,
        long lockTimeoutMs
    ) {
        super("master", buildMasterConfig(browserProperties), profileRoot, browserProperties, loginLockManager);
        if (StringUtils.isBlank(masterProfileId)) {
            throw new IllegalArgumentException("master profileId is blank");
        }
        this.masterProfileId = masterProfileId;
        this.lockTimeoutMs = Math.max(0L, lockTimeoutMs);
        initializeMinWorkers();
    }

    private static WorkerPoolConfig buildMasterConfig(BrowserProperties browserProperties) {
        return WorkerPoolConfig.builder()
            .minWorkers(0)
            .maxWorkers(1)
            .queueTimeoutMs(browserProperties.getQueueOfferTimeoutMs())
            .build();
    }

    @Override
    protected String allocateProfileId() {
        return masterProfileId;
    }

    @Override
    protected RuntimeException createBusyException() {
        return new MasterProfileBrowserWorkerBusyException("master profile browser worker pool is busy");
    }

    @Override
    protected boolean shouldRetainWorkerAfterTask(BrowserWorker worker) {
        // Release lock immediately after each master task so manual login can start.
        return false;
    }

    @Override
    protected LoginLockManager.LoginLock acquireProfileLock(String profileId, Path userDataDir) {
        // Lock path is intentionally aligned with MasterLoginCommand.
        Path lockPath = userDataDir.resolve("browser.lock");
        LoginLockManager.LoginLock lock = loginLockManager.tryAcquire(
            lockPath,
            lockTimeoutMs,
            LOCK_RETRY_INTERVAL_MS
        );
        if (lock == null) {
            log.info(
                "master profile lock unavailable, profileId={}, lockPath={}, timeoutMs={}, retryIntervalMs={}",
                profileId,
                lockPath,
                lockTimeoutMs,
                LOCK_RETRY_INTERVAL_MS
            );
            // Preserve business semantics for upper layers.
            throw new MasterProfileLockedException();
        }
        return lock;
    }

}
