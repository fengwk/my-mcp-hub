package fun.fengwk.mmh.core.service.browser.runtime;

import fun.fengwk.convention4j.common.lang.StringUtils;
import fun.fengwk.mmh.core.service.browser.BrowserProperties;
import fun.fengwk.mmh.core.service.browser.BrowserStealthSupport;
import fun.fengwk.mmh.core.service.browser.coordination.LoginLockManager;
import fun.fengwk.mmh.core.service.scrape.runtime.MasterProfileLockedException;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Proxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base browser worker pool with unified worker management.
 *
 * <p>Lifecycle model:
 * <ul>
 *     <li>Create worker lazily (except preheated min workers).</li>
 *     <li>Borrow worker from queue -> execute task -> return worker to queue.</li>
 *     <li>Track all workers in {@code allWorkers} so shutdown can close both idle and in-flight workers.</li>
 * </ul>
 *
 * @author fengwk
 */
public abstract class BrowserWorkerPool {

    private static final Logger log = LoggerFactory.getLogger(BrowserWorkerPool.class);

    protected final String poolName;
    protected final WorkerPoolConfig config;
    protected final Path profileRoot;
    protected final BrowserProperties browserProperties;
    protected final LoginLockManager loginLockManager;

    /**
     * Idle worker queue.
     */
    private final BlockingQueue<BrowserWorker> availableWorkers;

    /**
     * Global worker registry for deterministic shutdown.
     */
    private final Set<BrowserWorker> allWorkers = ConcurrentHashMap.newKeySet();

    /**
     * Number of created workers (idle + busy).
     */
    private final AtomicInteger activeWorkerCount = new AtomicInteger(0);

    /**
     * Pool shutdown flag.
     */
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    protected BrowserWorkerPool(
        String poolName,
        WorkerPoolConfig config,
        Path profileRoot,
        BrowserProperties browserProperties,
        LoginLockManager loginLockManager
    ) {
        this.poolName = poolName;
        this.config = normalizeConfig(config);
        this.profileRoot = profileRoot.toAbsolutePath().normalize();
        this.browserProperties = browserProperties;
        this.loginLockManager = loginLockManager;
        this.availableWorkers = new LinkedBlockingQueue<>(this.config.getMaxWorkers());
    }

    /**
     * Initialize minimum workers. Must be called after construction.
     */
    protected void initializeMinWorkers() {
        for (int i = 0; i < config.getMinWorkers(); i++) {
            BrowserWorker worker = tryCreateWorker();
            if (worker == null || !availableWorkers.offer(worker)) {
                if (worker != null) {
                    closeWorkerAndReleaseSlot(worker);
                }
                throw new IllegalStateException("failed to initialize " + poolName + " worker pool");
            }
        }
    }

    public <T> T execute(BrowserTask<T> task) {
        if (shutdown.get()) {
            throw new IllegalStateException(poolName + " worker pool is shutdown");
        }

        String taskName = task == null ? "null" : task.getClass().getName();
        BrowserWorker worker = null;
        try {
            worker = acquireWorker();
            return worker.execute(task);
        } catch (RuntimeException ex) {
            if (isExpectedRuntimeException(ex)) {
                log.info(
                    "browser task expected failure, pool={}, task={}, workerProfile={}, error={}",
                    poolName,
                    taskName,
                    worker == null ? "" : worker.getProfileId(),
                    ex.getMessage()
                );
            } else {
                log.warn(
                    "browser task runtime failure, pool={}, task={}, workerProfile={}, error={}",
                    poolName,
                    taskName,
                    worker == null ? "" : worker.getProfileId(),
                    ex.getMessage(),
                    ex
                );
            }
            throw ex;
        } catch (Exception ex) {
            log.warn(
                "browser task checked failure, pool={}, task={}, workerProfile={}, error={}",
                poolName,
                taskName,
                worker == null ? "" : worker.getProfileId(),
                ex.getMessage(),
                ex
            );
            throw new IllegalStateException("browser worker execution failed: " + ex.getMessage(), ex);
        } finally {
            if (worker != null) {
                releaseWorker(worker);
            }
        }
    }

    public void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            log.info("shutting down {} worker pool", poolName);

            // First close all idle workers already in queue.
            BrowserWorker worker;
            while ((worker = availableWorkers.poll()) != null) {
                closeWorkerAndReleaseSlot(worker);
            }

            // Then close remaining workers that may still be in-flight.
            List<BrowserWorker> remainingWorkers = List.copyOf(allWorkers);
            for (BrowserWorker remainingWorker : remainingWorkers) {
                closeWorkerAndReleaseSlot(remainingWorker);
            }

            log.info("{} worker pool shutdown completed", poolName);
        }
    }

    /**
     * Allocate profile ID for a new worker.
     *
     * <p>Implementations should return a deterministic, collision-safe id in current deployment model.
     */
    protected abstract String allocateProfileId();

    /**
     * Create exception when pool is busy.
     */
    protected abstract RuntimeException createBusyException();

    /**
     * Acquire profile lock when needed.
     *
     * <p>Default pool returns {@code null}; master pool uses cross-process lock.
     */
    protected LoginLockManager.LoginLock acquireProfileLock(String profileId, Path userDataDir) {
        return null;
    }

    /**
     * Whether profile directory should be deleted when worker closes.
     *
     * <p>Default pool cleans transient slave directories, master profile must be retained.
     */
    protected boolean shouldCleanupProfileDir(String profileId) {
        return false;
    }

    /**
     * Whether this pool should launch browser contexts in headless mode.
     */
    protected boolean resolveHeadlessMode() {
        return true;
    }

    /**
     * Whether worker should be retained in idle queue after each task.
     *
     * <p>Default pool keeps warm workers. Master pool can override to release
     * lock and context immediately after task completion.
     */
    protected boolean shouldRetainWorkerAfterTask(BrowserWorker worker) {
        return true;
    }

    private BrowserWorker acquireWorker() {
        // Fast path: reuse an idle worker.
        BrowserWorker worker = availableWorkers.poll();
        if (worker != null) {
            return worker;
        }

        // Try to scale out if capacity allows.
        worker = tryCreateWorker();
        if (worker != null) {
            return worker;
        }

        // Capacity reached: wait for a returned worker.
        try {
            worker = availableWorkers.poll(config.getQueueTimeoutMs(), TimeUnit.MILLISECONDS);
            if (worker == null) {
                log.info(
                    "worker acquire timeout, pool={}, timeoutMs={}, activeWorkers={}, idleWorkers={}",
                    poolName,
                    config.getQueueTimeoutMs(),
                    activeWorkerCount.get(),
                    availableWorkers.size()
                );
                throw createBusyException();
            }
            return worker;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("worker acquire interrupted, pool={}", poolName, ex);
            throw new IllegalStateException("interrupted while waiting for worker", ex);
        }
    }

    private void releaseWorker(BrowserWorker worker) {
        // During shutdown, worker should be closed instead of returning to queue.
        if (shutdown.get()) {
            closeWorkerAndReleaseSlot(worker);
            return;
        }

        // Some pools (e.g. master) should release worker resources after each task.
        if (!shouldRetainWorkerAfterTask(worker)) {
            closeWorkerAndReleaseSlot(worker);
            return;
        }

        // Queue full should not happen under normal invariants, still handled defensively.
        if (!availableWorkers.offer(worker)) {
            closeWorkerAndReleaseSlot(worker);
        }
    }

    private BrowserWorker tryCreateWorker() {
        // Reserve slot first to guarantee maxWorkers boundary under concurrency.
        if (!reserveWorkerSlot()) {
            return null;
        }
        try {
            return createWorker();
        } catch (RuntimeException ex) {
            if (isExpectedRuntimeException(ex)) {
                log.debug("create worker expected failure, pool={}, error={}", poolName, ex.getMessage());
            } else {
                log.warn("create worker runtime failure, pool={}, error={}", poolName, ex.getMessage(), ex);
            }
            releaseWorkerSlot();
            throw ex;
        } catch (Exception ex) {
            log.warn("create worker checked failure, pool={}, error={}", poolName, ex.getMessage(), ex);
            releaseWorkerSlot();
            throw new IllegalStateException("failed to create " + poolName + " worker: " + ex.getMessage(), ex);
        }
    }

    private BrowserWorker createWorker() throws Exception {
        String profileId = allocateProfileId();
        Path userDataDir = resolveUserDataDir(profileId);
        LoginLockManager.LoginLock profileLock = acquireProfileLock(profileId, userDataDir);

        Playwright playwright = null;
        BrowserContext browserContext = null;
        try {
            playwright = Playwright.create();
            browserContext = playwright.chromium().launchPersistentContext(userDataDir, buildContextOptions());
            BrowserStealthSupport.apply(browserContext, browserProperties);

            BrowserWorker worker = new BrowserWorker(
                profileId,
                userDataDir,
                shouldCleanupProfileDir(profileId),
                playwright,
                browserContext,
                profileLock
            );
            allWorkers.add(worker);
            log.debug("created {} worker: {}", poolName, profileId);
            return worker;
        } catch (Exception ex) {
            log.warn(
                "create worker failed, pool={}, profileId={}, userDataDir={}, error={}",
                poolName,
                profileId,
                userDataDir,
                ex.getMessage(),
                ex
            );
            // Creation failure must release all partially initialized resources.
            closeQuietly(browserContext);
            closeQuietly(playwright);
            closeQuietly(profileLock);
            throw ex;
        }
    }

    private WorkerPoolConfig normalizeConfig(WorkerPoolConfig rawConfig) {
        int normalizedMinWorkers = Math.max(0, rawConfig.getMinWorkers());
        int normalizedMaxWorkers = Math.max(1, rawConfig.getMaxWorkers());
        if (normalizedMaxWorkers < normalizedMinWorkers) {
            normalizedMaxWorkers = normalizedMinWorkers;
        }
        long normalizedQueueTimeoutMs = Math.max(1L, rawConfig.getQueueTimeoutMs());

        return WorkerPoolConfig.builder()
            .minWorkers(normalizedMinWorkers)
            .maxWorkers(normalizedMaxWorkers)
            .queueTimeoutMs(normalizedQueueTimeoutMs)
            .build();
    }

    private boolean reserveWorkerSlot() {
        // Lock-free CAS loop to enforce global max worker count.
        while (true) {
            int currentCount = activeWorkerCount.get();
            if (currentCount >= config.getMaxWorkers()) {
                return false;
            }
            if (activeWorkerCount.compareAndSet(currentCount, currentCount + 1)) {
                return true;
            }
        }
    }

    private void releaseWorkerSlot() {
        activeWorkerCount.decrementAndGet();
    }

    private Path resolveUserDataDir(String profileId) throws Exception {
        Path userDataDir = profileRoot.resolve(profileId).toAbsolutePath().normalize();
        // Prevent path traversal or escaping profile root.
        if (!userDataDir.startsWith(profileRoot)) {
            log.warn("invalid user data dir path detected, pool={}, profileId={}, path={}", poolName, profileId, userDataDir);
            throw new IllegalArgumentException("invalid user data dir path: " + userDataDir);
        }
        Files.createDirectories(userDataDir);
        return userDataDir;
    }

    private boolean isExpectedRuntimeException(RuntimeException ex) {
        return ex instanceof DefaultBrowserWorkerBusyException
            || ex instanceof MasterProfileBrowserWorkerBusyException
            || ex instanceof MasterProfileLockedException;
    }

    private BrowserType.LaunchPersistentContextOptions buildContextOptions() {
        BrowserType.LaunchPersistentContextOptions options = new BrowserType.LaunchPersistentContextOptions()
            .setHeadless(resolveHeadlessMode());

        if (browserProperties.isIgnoreAllDefaultArgs()) {
            options.setIgnoreAllDefaultArgs(true);
        } else if (browserProperties.getIgnoreDefaultArgs() != null
            && !browserProperties.getIgnoreDefaultArgs().isEmpty()) {
            options.setIgnoreDefaultArgs(browserProperties.getIgnoreDefaultArgs());
        }

        if (browserProperties.getLaunchArgs() != null && !browserProperties.getLaunchArgs().isEmpty()) {
            options.setArgs(browserProperties.getLaunchArgs());
        }

        if (StringUtils.isNotBlank(browserProperties.getBrowserChannel())) {
            options.setChannel(browserProperties.getBrowserChannel());
        }

        if (StringUtils.isNotBlank(browserProperties.getExecutablePath())) {
            options.setExecutablePath(Paths.get(browserProperties.getExecutablePath()));
        }

        String userAgent = resolveUserAgent();
        if (StringUtils.isNotBlank(userAgent)) {
            options.setUserAgent(userAgent);
        }
        if (StringUtils.isNotBlank(browserProperties.getLocale())) {
            options.setLocale(browserProperties.getLocale());
        }
        if (StringUtils.isNotBlank(browserProperties.getTimezoneId())) {
            options.setTimezoneId(browserProperties.getTimezoneId());
        }

        Map<String, String> headers = new HashMap<>();
        if (browserProperties.getExtraHeaders() != null) {
            browserProperties.getExtraHeaders().forEach((key, value) -> {
                if (StringUtils.isNotBlank(key) && StringUtils.isNotBlank(value)) {
                    headers.put(key, value);
                }
            });
        }
        if (StringUtils.isNotBlank(browserProperties.getAcceptLanguage())) {
            headers.putIfAbsent("Accept-Language", browserProperties.getAcceptLanguage());
        }
        if (!headers.isEmpty()) {
            options.setExtraHTTPHeaders(headers);
        }

        if (StringUtils.isNotBlank(browserProperties.getProxyServer())) {
            Proxy proxy = new Proxy(browserProperties.getProxyServer());
            if (StringUtils.isNotBlank(browserProperties.getProxyUsername())) {
                proxy.setUsername(browserProperties.getProxyUsername());
            }
            if (StringUtils.isNotBlank(browserProperties.getProxyPassword())) {
                proxy.setPassword(browserProperties.getProxyPassword());
            }
            options.setProxy(proxy);
        }

        return options;
    }

    private String resolveUserAgent() {
        if (StringUtils.isNotBlank(browserProperties.getUserAgent())) {
            return browserProperties.getUserAgent();
        }
        List<String> userAgents = browserProperties.getUserAgents();
        if (userAgents == null || userAgents.isEmpty()) {
            return "";
        }
        return userAgents.get(ThreadLocalRandom.current().nextInt(userAgents.size()));
    }

    private void closeWorkerAndReleaseSlot(BrowserWorker worker) {
        // Idempotent close guard: only first caller removes and closes the worker.
        if (!allWorkers.remove(worker)) {
            return;
        }
        try {
            worker.shutdown();
        } finally {
            releaseWorkerSlot();
        }
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ex) {
            log.debug("close resource failed, pool={}", poolName, ex);
        }
    }

}
