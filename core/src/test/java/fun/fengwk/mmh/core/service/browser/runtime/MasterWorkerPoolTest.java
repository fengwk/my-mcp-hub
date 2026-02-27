package fun.fengwk.mmh.core.service.browser.runtime;

import fun.fengwk.mmh.core.service.browser.BrowserProperties;
import fun.fengwk.mmh.core.service.browser.coordination.LoginLockManager;
import fun.fengwk.mmh.core.service.scrape.runtime.MasterProfileLockedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author fengwk
 */
public class MasterWorkerPoolTest {

    @TempDir
    Path tempDir;

    private MasterBrowserWorkerPool masterWorkerPool;

    @AfterEach
    public void tearDown() {
        if (masterWorkerPool != null) {
            masterWorkerPool.shutdown();
        }
    }

    @Test
    public void shouldThrowMasterProfileLockedWhenLockUnavailable() throws IOException {
        Path browserDataDir = tempDir.resolve("browser-data");
        String masterProfileId = "custom-master";
        Path masterProfileDir = browserDataDir.resolve(masterProfileId);
        Files.createDirectories(masterProfileDir);

        LoginLockManager loginLockManager = mock(LoginLockManager.class);
        when(loginLockManager.tryAcquire(any(Path.class), anyLong(), anyLong())).thenReturn(null);

        BrowserProperties browserProperties = new BrowserProperties();

        masterWorkerPool = new MasterBrowserWorkerPool(
            browserDataDir,
            masterProfileId,
            browserProperties,
            loginLockManager,
            0
        );

        assertThatThrownBy(() -> masterWorkerPool.execute(context -> "ok"))
            .isInstanceOf(MasterProfileLockedException.class)
            .hasMessage(MasterProfileLockedException.DEFAULT_MESSAGE);

        ArgumentCaptor<Path> lockPathCaptor = ArgumentCaptor.forClass(Path.class);
        verify(loginLockManager).tryAcquire(lockPathCaptor.capture(), anyLong(), anyLong());

        Path expectedLockPath = masterProfileDir.resolve("browser.lock").toAbsolutePath().normalize();
        assertThat(lockPathCaptor.getValue().toAbsolutePath().normalize()).isEqualTo(expectedLockPath);
    }

    @Test
    public void shouldUseConfiguredQueueTimeoutForMasterPool() {
        BrowserProperties browserProperties = new BrowserProperties();
        browserProperties.setQueueOfferTimeoutMs(1500);

        masterWorkerPool = new MasterBrowserWorkerPool(
            tempDir.resolve("browser-data"),
            "master",
            browserProperties,
            mock(LoginLockManager.class),
            0
        );

        assertThat(masterWorkerPool.config.getQueueTimeoutMs()).isEqualTo(1500);
    }

}
