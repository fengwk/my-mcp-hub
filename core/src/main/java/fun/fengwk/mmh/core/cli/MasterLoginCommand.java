package fun.fengwk.mmh.core.cli;

import fun.fengwk.mmh.core.service.browser.coordination.LoginLockManager;
import fun.fengwk.mmh.core.service.browser.coordination.SnapshotBootstrap;
import fun.fengwk.mmh.core.service.browser.coordination.SnapshotPublisher;
import fun.fengwk.mmh.core.service.scrape.ScrapeProperties;
import fun.fengwk.mmh.core.service.scrape.runtime.MasterLoginRuntime;

import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manual login command runner.
 *
 * @author fengwk
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MasterLoginCommand implements ApplicationRunner {

    private final SnapshotBootstrap snapshotBootstrap;
    private final LoginLockManager loginLockManager;
    private final MasterLoginRuntime masterLoginRuntime;
    private final SnapshotPublisher snapshotPublisher;
    private final ScrapeProperties scrapeProperties;

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("open-browser")) {
            return;
        }
        String profileId = scrapeProperties.getDefaultProfileId();
        long refreshIntervalMs = resolveRefreshInterval();
        long timeoutMs = resolveTimeout();

        snapshotBootstrap.ensureInitialized(profileId);
        LoginLockManager.LoginLock loginLock = loginLockManager.tryAcquire(profileId);
        if (loginLock == null) {
            throw new IllegalStateException("login session already running for profileId=" + profileId);
        }

        try (loginLock; MasterLoginRuntime.HeadedSession session = masterLoginRuntime.open(profileId)) {
            AtomicBoolean finished = new AtomicBoolean(false);
            CountDownLatch closedSignal = new CountDownLatch(1);
            Runnable finishAndPublish = () -> {
                if (finished.compareAndSet(false, true)) {
                    tryPublishSnapshot(profileId, session);
                    closedSignal.countDown();
                }
            };
            Runnable finishIfNoPages = () -> {
                if (session.context().pages().isEmpty()) {
                    finishAndPublish.run();
                }
            };
            session.context().onClose(context -> finishAndPublish.run());
            session.context().onPage(page -> page.onClose(ignored -> finishIfNoPages.run()));
            for (Page page : session.context().pages()) {
                page.onClose(ignored -> finishIfNoPages.run());
            }
            log.info("master login started, profileId={}, refreshIntervalMs={}, timeoutMs={}",
                profileId,
                refreshIntervalMs,
                timeoutMs
            );
            long startedAt = System.currentTimeMillis();
            long loopSleepMs = refreshIntervalMs > 0 ? refreshIntervalMs : 1000;
            while (!Thread.currentThread().isInterrupted() && !finished.get()) {
                finishIfNoPages.run();
                if (refreshIntervalMs > 0) {
                    tryPublishSnapshot(profileId, session);
                }
                if (timeoutMs > 0 && System.currentTimeMillis() - startedAt >= timeoutMs) {
                    finishAndPublish.run();
                    break;
                }
                waitClosedOrTimeout(closedSignal, loopSleepMs);
            }
        }
        System.exit(0);
    }

    private long resolveRefreshInterval() {
        long refreshIntervalMs = scrapeProperties.getMasterLoginRefreshIntervalMs();
        return Math.max(0, refreshIntervalMs);
    }

    private long resolveTimeout() {
        long timeoutMs = scrapeProperties.getMasterLoginTimeoutMs();
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
        }
    }

    private void tryPublishSnapshot(String profileId, MasterLoginRuntime.HeadedSession session) {
        try {
            snapshotPublisher.publishFromContext(profileId, session.context());
        } catch (Exception ex) {
            log.warn("publish snapshot failed, profileId={}, error={}", profileId, ex.getMessage());
        }
    }

}
