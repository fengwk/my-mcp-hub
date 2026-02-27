package fun.fengwk.mmh.core.cli;

import fun.fengwk.mmh.core.service.browser.coordination.LoginLockManager;
import fun.fengwk.mmh.core.service.browser.BrowserProperties;
import fun.fengwk.mmh.core.service.scrape.runtime.MasterLoginRuntime;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manual login command runner.
 *
 * @author fengwk
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MasterLoginCommand implements ApplicationRunner {

    private final LoginLockManager loginLockManager;
    private final MasterLoginRuntime masterLoginRuntime;
    private final BrowserProperties browserProperties;

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("open-browser")) {
            return;
        }
        String profileId = browserProperties.getDefaultProfileId();
        long refreshIntervalMs = resolveRefreshInterval();
        long timeoutMs = resolveTimeout();

        Path userDataDir = masterLoginRuntime.resolveUserDataDir(profileId);
        Path lockPath = userDataDir.resolve("browser.lock");
        LoginLockManager.LoginLock loginLock = loginLockManager.tryAcquire(lockPath);
        if (loginLock == null) {
            log.warn("login session already running, profileId={}, lockPath={}", profileId, lockPath);
            System.exit(1);
            return;
        }

        try (loginLock; MasterLoginRuntime.HeadedSession session = masterLoginRuntime.open(profileId)) {
            AtomicBoolean finished = new AtomicBoolean(false);
            AtomicBoolean closeSignalDetected = new AtomicBoolean(false);
            AtomicInteger previousUserPageCount = new AtomicInteger(-1);
            AtomicInteger previousForegroundPageCount = new AtomicInteger(-1);
            AtomicInteger noForegroundRounds = new AtomicInteger(0);
            CountDownLatch closedSignal = new CountDownLatch(1);
            Runnable finishAndSignal = () -> {
                if (finished.compareAndSet(false, true)) {
                    closedSignal.countDown();
                }
            };
            Runnable evaluateExit = () -> {
                PageSnapshot snapshot = collectPageSnapshot(session.context());
                int userPageCount = snapshot.userPageUrls().size();
                int foregroundPageCount = snapshot.foregroundPageUrls().size();

                int previousUsers = previousUserPageCount.getAndSet(userPageCount);
                int previousForegrounds = previousForegroundPageCount.getAndSet(foregroundPageCount);
                if (previousUsers >= 0 && userPageCount < previousUsers) {
                    closeSignalDetected.set(true);
                }
                if (previousForegrounds > 0 && foregroundPageCount == 0) {
                    closeSignalDetected.set(true);
                }

                if (foregroundPageCount == 0) {
                    int rounds = noForegroundRounds.incrementAndGet();
                    if (closeSignalDetected.get() && rounds >= 2) {
                        finishAndSignal.run();
                        return;
                    }
                    log.debug(
                        "master login waiting pages close, profileId={}, userPages={}, foregroundPages={}, closeSignal={}, noForegroundRounds={}",
                        profileId,
                        snapshot.userPageUrls(),
                        snapshot.foregroundPageUrls(),
                        closeSignalDetected.get(),
                        rounds
                    );
                } else {
                    noForegroundRounds.set(0);
                    log.debug("master login foreground pages, profileId={}, pages={}", profileId, snapshot.foregroundPageUrls());
                }
            };
            session.context().onClose(context -> {
                closeSignalDetected.set(true);
                finishAndSignal.run();
            });
            session.context().onPage(page -> page.onClose(ignored -> {
                closeSignalDetected.set(true);
                evaluateExit.run();
            }));
            for (Page page : session.context().pages()) {
                page.onClose(ignored -> {
                    closeSignalDetected.set(true);
                    evaluateExit.run();
                });
            }

            // Prime counters with initial page state.
            evaluateExit.run();
            if (finished.get()) {
                return;
            }

            log.info("master login started, profileId={}, userDataDir={}, refreshIntervalMs={}, timeoutMs={}",
                profileId,
                userDataDir,
                refreshIntervalMs,
                timeoutMs
            );
            long startedAt = System.currentTimeMillis();
            long loopSleepMs = refreshIntervalMs > 0 ? refreshIntervalMs : 1000;
            while (!Thread.currentThread().isInterrupted() && !finished.get()) {
                evaluateExit.run();
                if (finished.get()) {
                    break;
                }
                if (timeoutMs > 0 && System.currentTimeMillis() - startedAt >= timeoutMs) {
                    finishAndSignal.run();
                    break;
                }
                waitClosedOrTimeout(closedSignal, loopSleepMs);
            }
        }
        System.exit(0);
    }

    private PageSnapshot collectPageSnapshot(BrowserContext context) {
        List<String> userPages = new ArrayList<>();
        List<String> foregroundPages = new ArrayList<>();
        try {
            for (Page page : context.pages()) {
                if (page == null || page.isClosed()) {
                    continue;
                }
                String url = safePageUrl(page);
                if (isBackgroundPage(url)) {
                    continue;
                }
                userPages.add(url);
                if (isPageVisible(page)) {
                    foregroundPages.add(url);
                }
            }
            return new PageSnapshot(userPages, foregroundPages);
        } catch (Exception ex) {
            log.info("master login context unavailable, treat as closed, error={}", ex.getMessage());
            return new PageSnapshot(List.of(), List.of());
        }
    }

    private String safePageUrl(Page page) {
        try {
            return page.url();
        } catch (Exception ex) {
            return "";
        }
    }

    private boolean isBackgroundPage(String url) {
        if (url == null) {
            return true;
        }
        String normalized = url.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return true;
        }
        return normalized.startsWith("chrome-extension://")
            || normalized.startsWith("devtools://");
    }

    private boolean isPageVisible(Page page) {
        try {
            Object result = page.evaluate("() => document.visibilityState");
            return "visible".equalsIgnoreCase(String.valueOf(result));
        } catch (Exception ex) {
            return false;
        }
    }

    private long resolveRefreshInterval() {
        long refreshIntervalMs = browserProperties.getMasterLoginRefreshIntervalMs();
        return Math.max(0, refreshIntervalMs);
    }

    private long resolveTimeout() {
        long timeoutMs = browserProperties.getMasterLoginTimeoutMs();
        return timeoutMs < 0 ? 0 : timeoutMs;
    }

    private void waitClosedOrTimeout(CountDownLatch closedSignal, long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            closedSignal.await(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.info("master login wait interrupted, millis={}", millis);
        }
    }

    private record PageSnapshot(List<String> userPageUrls, List<String> foregroundPageUrls) {

    }

}
