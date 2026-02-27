package fun.fengwk.mmh.core.service.browser.runtime;

import fun.fengwk.mmh.core.service.browser.coordination.LoginLockManager;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Browser worker that encapsulates browser context and execution state.
 *
 * <p>Each worker owns exactly one persistent browser context and executes tasks sequentially.
 * Close is idempotent and releases resources in strict order.
 *
 * @author fengwk
 */
public class BrowserWorker {

    private static final Logger log = LoggerFactory.getLogger(BrowserWorker.class);

    private final String profileId;
    private final Path userDataDir;
    private final boolean deleteUserDataDirOnClose;
    private final Playwright playwright;
    private final BrowserContext browserContext;
    private final LoginLockManager.LoginLock profileLock;
    private volatile boolean closed = false;

    public BrowserWorker(
        String profileId,
        Path userDataDir,
        boolean deleteUserDataDirOnClose,
        Playwright playwright,
        BrowserContext browserContext,
        LoginLockManager.LoginLock profileLock
    ) {
        this.profileId = profileId;
        this.userDataDir = userDataDir;
        this.deleteUserDataDirOnClose = deleteUserDataDirOnClose;
        this.playwright = playwright;
        this.browserContext = browserContext;
        this.profileLock = profileLock;
    }

    public String getProfileId() {
        return profileId;
    }

    public boolean isClosed() {
        return closed;
    }

    public <T> T execute(BrowserTask<T> task) throws Exception {
        if (closed) {
            throw new IllegalStateException("worker is closed");
        }

        // Use one page per task to isolate navigation state while reusing the context.
        try (Page page = browserContext.newPage()) {
            BrowserRuntimeContext runtimeContext = BrowserRuntimeContext.builder()
                .profileId(profileId)
                .baseVersion(0L)
                .browserContext(browserContext)
                .page(page)
                .build();
            return task.execute(runtimeContext);
        }
    }

    public void shutdown() {
        close();
    }

    public void close() {
        // Idempotent close to handle concurrent shutdown/release paths safely.
        if (closed) {
            return;
        }
        closed = true;
        
        try {
            if (browserContext != null) {
                browserContext.close();
            }
        } catch (Exception ex) {
            if (isExpectedCloseException(ex)) {
                log.debug("browser context already closed for profile {}, skip close", profileId);
            } else {
                log.warn("failed to close browser context for profile {}", profileId, ex);
            }
        }

        try {
            if (playwright != null) {
                playwright.close();
            }
        } catch (Exception ex) {
            if (isExpectedCloseException(ex)) {
                log.debug("playwright already closed for profile {}, skip close", profileId);
            } else {
                log.warn("failed to close playwright for profile {}", profileId, ex);
            }
        }

        try {
            if (profileLock != null) {
                profileLock.close();
            }
        } catch (Exception ex) {
            log.warn("failed to release profile lock for profile {}", profileId, ex);
        }

        if (deleteUserDataDirOnClose) {
            // Default slave profiles are transient and should not survive process/workflow end.
            deleteUserDataDirQuietly();
        }
    }

    private void deleteUserDataDirQuietly() {
        if (userDataDir == null || !Files.exists(userDataDir)) {
            return;
        }
        // Delete children first, then root directory.
        try (Stream<Path> stream = Files.walk(userDataDir)) {
            stream
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ex) {
                        throw new IllegalStateException("failed to delete path: " + path, ex);
                    }
                });
        } catch (Exception ex) {
            log.warn("failed to cleanup user data dir for profile {}, dir={}", profileId, userDataDir, ex);
        }
    }

    private boolean isExpectedCloseException(Exception ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        String exceptionName = ex.getClass().getSimpleName();
        return "TargetClosedError".equals(exceptionName)
            || message.contains("target page, context or browser has been closed")
            || message.contains("channel has been closed")
            || message.contains("connection closed");
    }

}
