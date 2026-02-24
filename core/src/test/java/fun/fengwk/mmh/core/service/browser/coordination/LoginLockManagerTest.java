package fun.fengwk.mmh.core.service.browser.coordination;

import fun.fengwk.mmh.core.service.browser.BrowserProperties;
import fun.fengwk.mmh.core.service.scrape.ScrapeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author fengwk
 */
public class LoginLockManagerTest {

    @TempDir
    Path tempDir;

    private LoginLockManager loginLockManager;

    @BeforeEach
    public void setUp() throws Exception {
        BrowserProperties browserProperties = new BrowserProperties();
        Path snapshotRoot = tempDir.resolve("snapshots");
        browserProperties.setSnapshotRoot(snapshotRoot.toString());
        ScrapeProperties scrapeProperties = new ScrapeProperties();
        ProfileIdValidator profileIdValidator = new ProfileIdValidator(browserProperties, scrapeProperties);
        loginLockManager = new LoginLockManager(profileIdValidator);

        Path profileDir = snapshotRoot.resolve("master");
        Files.createDirectories(profileDir);
        Files.createFile(profileDir.resolve("login.lock"));
    }

    @Test
    public void shouldAcquireAndReleaseLock() {
        try (LoginLockManager.LoginLock lock = loginLockManager.tryAcquire("master")) {
            assertThat(lock).isNotNull();
        }

        try (LoginLockManager.LoginLock lock = loginLockManager.tryAcquire("master")) {
            assertThat(lock).isNotNull();
        }
    }

    @Test
    public void shouldFailWhenLockHeld() {
        try (LoginLockManager.LoginLock lock = loginLockManager.tryAcquire("master")) {
            assertThat(lock).isNotNull();
            LoginLockManager.LoginLock second = loginLockManager.tryAcquire("master");
            assertThat(second).isNull();
            if (second != null) {
                second.close();
            }
        }
    }

}
