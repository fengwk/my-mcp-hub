package fun.fengwk.mmh.core.service.browser.runtime;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import fun.fengwk.mmh.core.service.browser.BrowserProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.nio.file.Paths;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Worker manager with blocking queue and per-worker browser.
 *
 * @author fengwk
 */
@Slf4j
@Component
public class BrowserWorkerManager {

    private final BrowserProperties browserProperties;
    private final BrowserSessionFactory browserSessionFactory;
    private final BlockingQueue<TaskHolder<?>> queue;
    private final List<Thread> workerThreads = new CopyOnWriteArrayList<>();
    private final AtomicInteger workerIdGen = new AtomicInteger(1);
    private final AtomicInteger workerCount = new AtomicInteger(0);
    private final AtomicInteger idleWorkers = new AtomicInteger(0);
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    @Autowired
    public BrowserWorkerManager(BrowserProperties browserProperties) {
        this(browserProperties, defaultBrowserSessionFactory(browserProperties));
    }

    BrowserWorkerManager(BrowserProperties browserProperties, BrowserSessionFactory browserSessionFactory) {
        this.browserProperties = browserProperties;
        this.browserSessionFactory = browserSessionFactory;
        int capacity = Math.max(1, browserProperties.getRequestQueueCapacity());
        this.queue = new LinkedBlockingQueue<>(capacity);
        startMinWorkers();
    }

    public <T> T execute(BrowserSessionTask<T> task) {
        TaskHolder<T> holder = new TaskHolder<>(task);
        try {
            boolean offered = queue.offer(
                holder,
                browserProperties.getQueueOfferTimeoutMs(),
                TimeUnit.MILLISECONDS
            );
            if (!offered) {
                log.warn("worker queue full, size={}, capacity={}", queue.size(), browserProperties.getRequestQueueCapacity());
                throw new IllegalStateException("worker pool is busy");
            }
            ensureWorkerCapacity();
            return holder.future.get();
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("worker execution interrupted");
            throw new IllegalStateException("browser worker execution interrupted", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            log.warn("worker execution failed", cause);
            throw new IllegalStateException("browser worker execution failed: " + cause.getMessage(), cause);
        } catch (Exception ex) {
            log.warn("worker execution failed", ex);
            throw new IllegalStateException("browser worker execution failed: " + ex.getMessage(), ex);
        }
    }

    private void startMinWorkers() {
        int minSize = normalizeMinWorkerSize();
        for (int i = 0; i < minSize; i++) {
            spawnWorker();
        }
    }

    private void ensureWorkerCapacity() {
        if (shutdown.get()) {
            return;
        }
        if (idleWorkers.get() > 0) {
            return;
        }
        int maxSize = normalizeMaxWorkerSize();
        while (workerCount.get() < maxSize && idleWorkers.get() == 0 && queue.size() > 0) {
            spawnWorker();
        }
    }

    private void spawnWorker() {
        int maxSize = normalizeMaxWorkerSize();
        int current = workerCount.get();
        if (current >= maxSize) {
            return;
        }
        if (!workerCount.compareAndSet(current, current + 1)) {
            return;
        }
        Thread thread = new Thread(new Worker(workerIdGen.getAndIncrement()));
        thread.setName("mmh-browser-worker-" + thread.getId());
        thread.setDaemon(true);
        workerThreads.add(thread);
        thread.start();
    }

    private int normalizeMinWorkerSize() {
        return Math.max(browserProperties.getWorkerPoolMinSizePerProcess(), 1);
    }

    private int normalizeMaxWorkerSize() {
        return Math.max(browserProperties.getWorkerPoolMaxSizePerProcess(), normalizeMinWorkerSize());
    }

    private long normalizeRefreshIntervalMs() {
        return Math.max(1L, browserProperties.getWorkerRefreshIntervalMs());
    }

    private boolean shouldTerminate(long lastTaskAt) {
        long idleTtlMs = browserProperties.getWorkerIdleTtlMs();
        if (idleTtlMs <= 0) {
            return false;
        }
        if (System.currentTimeMillis() - lastTaskAt < idleTtlMs) {
            return false;
        }
        return workerCount.get() > normalizeMinWorkerSize();
    }

    @PreDestroy
    public void shutdown() {
        if (!shutdown.compareAndSet(false, true)) {
            return;
        }
        List<TaskHolder<?>> pending = new ArrayList<>();
        queue.drainTo(pending);
        for (TaskHolder<?> holder : pending) {
            holder.future.completeExceptionally(new IllegalStateException("worker pool is shutting down"));
        }
        for (Thread thread : workerThreads) {
            thread.interrupt();
        }
    }

    private static class TaskHolder<T> {

        private final BrowserSessionTask<T> task;
        private final CompletableFuture<T> future = new CompletableFuture<>();

        private TaskHolder(BrowserSessionTask<T> task) {
            this.task = task;
        }

    }

    private class Worker implements Runnable {

        private final int workerId;
        private long lastTaskAt = System.currentTimeMillis();
        private BrowserSession browserSession;

        private Worker(int workerId) {
            this.workerId = workerId;
        }

        @Override
        public void run() {
            try {
                initBrowser();
                loop();
            } catch (Exception ex) {
                log.warn("worker terminated unexpectedly, id={}", workerId, ex);
            } finally {
                closeBrowser();
                workerCount.decrementAndGet();
                workerThreads.remove(Thread.currentThread());
            }
        }

        private void initBrowser() {
            browserSession = browserSessionFactory.create();
        }

        private void loop() throws Exception {
            long refreshIntervalMs = normalizeRefreshIntervalMs();
            while (!shutdown.get()) {
                TaskHolder<?> holder;
                idleWorkers.incrementAndGet();
                try {
                    // Polling interval also gates idle checks and worker retirement.
                    holder = queue.poll(refreshIntervalMs, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                } finally {
                    idleWorkers.decrementAndGet();
                }
                if (holder == null) {
                    if (shouldTerminate(lastTaskAt)) {
                        return;
                    }
                    continue;
                }
                lastTaskAt = System.currentTimeMillis();
                executeTask(holder);
            }
        }

        @SuppressWarnings("unchecked")
        private <T> void executeTask(TaskHolder<T> holder) {
            try {
                T result = holder.task.execute(browserSession.browser());
                holder.future.complete(result);
            } catch (Exception ex) {
                log.warn("worker task failed, id={}, error={}", workerId, ex.getMessage(), ex);
                holder.future.completeExceptionally(ex);
            }
        }

        private void closeBrowser() {
            if (browserSession != null) {
                try {
                    browserSession.close();
                } catch (Exception ex) {
                    log.debug("failed to close browser, id={}, error={}", workerId, ex.getMessage());
                }
            }
        }

    }

    private static BrowserSessionFactory defaultBrowserSessionFactory(BrowserProperties browserProperties) {
        return () -> {
            Playwright playwright = Playwright.create();
            BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(true);
            if (browserProperties != null) {
                if (browserProperties.isIgnoreAllDefaultArgs()) {
                    options.setIgnoreAllDefaultArgs(true);
                } else if (browserProperties.getIgnoreDefaultArgs() != null
                    && !browserProperties.getIgnoreDefaultArgs().isEmpty()) {
                    options.setIgnoreDefaultArgs(browserProperties.getIgnoreDefaultArgs());
                }
                if (browserProperties.getLaunchArgs() != null && !browserProperties.getLaunchArgs().isEmpty()) {
                    options.setArgs(browserProperties.getLaunchArgs());
                }
                if (browserProperties.getBrowserChannel() != null && !browserProperties.getBrowserChannel().isBlank()) {
                    options.setChannel(browserProperties.getBrowserChannel());
                }
                if (browserProperties.getExecutablePath() != null && !browserProperties.getExecutablePath().isBlank()) {
                    options.setExecutablePath(Paths.get(browserProperties.getExecutablePath()));
                }
            }
            Browser browser = playwright.chromium().launch(options);
            return new BrowserSession(playwright, browser);
        };
    }

    interface BrowserSessionFactory {

        BrowserSession create();

    }

    static class BrowserSession implements AutoCloseable {

        private final Playwright playwright;
        private final Browser browser;

        BrowserSession(Playwright playwright, Browser browser) {
            this.playwright = playwright;
            this.browser = browser;
        }

        private Browser browser() {
            return browser;
        }

        @Override
        public void close() {
            try {
                browser.close();
            } finally {
                playwright.close();
            }
        }

    }

}
