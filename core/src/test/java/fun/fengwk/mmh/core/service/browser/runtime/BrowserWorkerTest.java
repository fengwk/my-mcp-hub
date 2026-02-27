package fun.fengwk.mmh.core.service.browser.runtime;

import fun.fengwk.mmh.core.service.browser.coordination.LoginLockManager;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author fengwk
 */
public class BrowserWorkerTest {

    @TempDir
    Path tempDir;

    @Test
    public void shouldDeleteUserDataDirWhenCleanupEnabled() throws Exception {
        Path userDataDir = tempDir.resolve("slave_1_1");
        Path nestedFile = userDataDir.resolve("nested/data.txt");
        Files.createDirectories(nestedFile.getParent());
        Files.writeString(nestedFile, "test");

        BrowserContext browserContext = mock(BrowserContext.class);
        Playwright playwright = mock(Playwright.class);
        LoginLockManager.LoginLock loginLock = mock(LoginLockManager.LoginLock.class);
        BrowserWorker worker = new BrowserWorker(
            "slave_1_1",
            userDataDir,
            true,
            playwright,
            browserContext,
            loginLock
        );

        worker.close();
        worker.close();

        assertThat(Files.exists(userDataDir)).isFalse();
        verify(browserContext, times(1)).close();
        verify(playwright, times(1)).close();
        verify(loginLock, times(1)).close();
    }

    @Test
    public void shouldKeepUserDataDirWhenCleanupDisabled() throws Exception {
        Path userDataDir = tempDir.resolve("master");
        Path nestedFile = userDataDir.resolve("data.txt");
        Files.createDirectories(userDataDir);
        Files.writeString(nestedFile, "test");

        BrowserContext browserContext = mock(BrowserContext.class);
        Playwright playwright = mock(Playwright.class);
        LoginLockManager.LoginLock loginLock = mock(LoginLockManager.LoginLock.class);
        BrowserWorker worker = new BrowserWorker(
            "master",
            userDataDir,
            false,
            playwright,
            browserContext,
            loginLock
        );

        worker.close();

        assertThat(Files.exists(userDataDir)).isTrue();
        assertThat(Files.exists(nestedFile)).isTrue();
    }

}
