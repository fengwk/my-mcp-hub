package fun.fengwk.mmh.core.service.browser.runtime;

import fun.fengwk.mmh.core.service.browser.BrowserProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Worker manager with bounded parallelism.
 *
 * @author fengwk
 */
@Component
public class BrowserWorkerManager {

    private static final int MAX_SUBMIT_RETRIES = 2;

    private final BrowserProperties browserProperties;

    private final AtomicInteger threadIdGen = new AtomicInteger(1);
    private final AtomicInteger activeTaskCount = new AtomicInteger(0);
    private final AtomicLong lastActiveAt = new AtomicLong(System.currentTimeMillis());
    private final ScheduledExecutorService idleReaper;

    private volatile ThreadPoolExecutor executorService;
    private volatile Semaphore workerPermits;
    private volatile int maxWorkerSize;

    public BrowserWorkerManager(BrowserProperties browserProperties) {
        this.browserProperties = browserProperties;
        this.idleReaper = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("mmh-browser-worker-reaper");
            thread.setDaemon(true);
            return thread;
        });
        startIdleReaper();
    }

    public <T> T execute(Callable<T> task) {
        try {
            for (int attempt = 1; attempt <= MAX_SUBMIT_RETRIES; attempt++) {
                WorkerHandle workerHandle = acquireWorkerHandle();
                activeTaskCount.incrementAndGet();
                touchActiveTime();
                try {
                    Future<T> future = workerHandle.executorService.submit(task);
                    return future.get();
                } catch (RejectedExecutionException ex) {
                    if (attempt < MAX_SUBMIT_RETRIES && isStaleExecutor(workerHandle.executorService)) {
                        continue;
                    }
                    throw new IllegalStateException("worker pool is busy", ex);
                } finally {
                    activeTaskCount.decrementAndGet();
                    touchActiveTime();
                    workerHandle.workerPermits.release();
                }
            }
            throw new IllegalStateException("worker pool is busy");
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("browser worker execution interrupted", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("browser worker execution failed: " + ex.getMessage(), ex);
        }
    }

    private WorkerHandle acquireWorkerHandle() throws InterruptedException {
        ThreadPoolExecutor currentExecutor;
        Semaphore currentPermits;
        synchronized (this) {
            ensureInitialized();
            currentExecutor = executorService;
            currentPermits = workerPermits;
        }
        if (currentExecutor == null || currentPermits == null) {
            throw new IllegalStateException("worker pool is busy");
        }
        boolean acquired = currentPermits.tryAcquire(
            browserProperties.getQueueOfferTimeoutMs(),
            TimeUnit.MILLISECONDS
        );
        if (!acquired) {
            throw new IllegalStateException("worker pool is busy");
        }
        return new WorkerHandle(currentExecutor, currentPermits);
    }

    private boolean isStaleExecutor(ThreadPoolExecutor executor) {
        return executor.isShutdown() || executorService != executor;
    }

    private void ensureInitialized() {
        if (executorService != null) {
            return;
        }
        synchronized (this) {
            if (executorService != null) {
                return;
            }
            int minWorkerSize = normalizeMinWorkerSize();
            int maxWorkerSize = normalizeMaxWorkerSize(minWorkerSize);
            executorService = new ThreadPoolExecutor(
                minWorkerSize,
                maxWorkerSize,
                60L,
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("mmh-browser-worker-" + threadIdGen.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                }
            );
            executorService.allowCoreThreadTimeOut(false);
            workerPermits = new Semaphore(maxWorkerSize);
            this.maxWorkerSize = maxWorkerSize;
        }
    }

    private void startIdleReaper() {
        long idleTtlMs = browserProperties.getWorkerIdleTtlMs();
        if (idleTtlMs <= 0) {
            return;
        }
        long checkIntervalMs = Math.max(1000L, Math.min(idleTtlMs, 10000L));
        idleReaper.scheduleWithFixedDelay(
            this::releaseIdleWorkerPool,
            checkIntervalMs,
            checkIntervalMs,
            TimeUnit.MILLISECONDS
        );
    }

    private void releaseIdleWorkerPool() {
        long idleTtlMs = browserProperties.getWorkerIdleTtlMs();
        if (idleTtlMs <= 0) {
            return;
        }
        ThreadPoolExecutor currentExecutor = executorService;
        Semaphore currentPermits = workerPermits;
        if (currentExecutor == null || currentPermits == null) {
            return;
        }
        if (activeTaskCount.get() > 0) {
            return;
        }
        if (currentPermits.availablePermits() != maxWorkerSize) {
            return;
        }
        if (System.currentTimeMillis() - lastActiveAt.get() < idleTtlMs) {
            return;
        }

        synchronized (this) {
            if (executorService == null || workerPermits == null) {
                return;
            }
            if (activeTaskCount.get() > 0) {
                return;
            }
            if (workerPermits.availablePermits() != maxWorkerSize) {
                return;
            }
            if (System.currentTimeMillis() - lastActiveAt.get() < idleTtlMs) {
                return;
            }
            executorService.shutdownNow();
            executorService = null;
            workerPermits = null;
            maxWorkerSize = 0;
        }
    }

    private int normalizeMinWorkerSize() {
        return Math.max(browserProperties.getWorkerPoolMinSizePerProcess(), 1);
    }

    private int normalizeMaxWorkerSize(int minWorkerSize) {
        return Math.max(browserProperties.getWorkerPoolMaxSizePerProcess(), minWorkerSize);
    }

    private void touchActiveTime() {
        lastActiveAt.set(System.currentTimeMillis());
    }

    private record WorkerHandle(ThreadPoolExecutor executorService, Semaphore workerPermits) {}

    @PreDestroy
    public void shutdown() {
        ExecutorService current = executorService;
        if (current != null) {
            current.shutdownNow();
        }
        idleReaper.shutdownNow();
    }

}
