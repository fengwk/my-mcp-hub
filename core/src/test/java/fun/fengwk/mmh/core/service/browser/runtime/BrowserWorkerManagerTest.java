package fun.fengwk.mmh.core.service.browser.runtime;

import fun.fengwk.mmh.core.configuration.MmhProperties;
import fun.fengwk.mmh.core.service.browser.BrowserProperties;
import fun.fengwk.mmh.core.service.browser.coordination.LoginLockManager;
import fun.fengwk.mmh.core.service.browser.coordination.ProfileIdValidator;
import fun.fengwk.mmh.core.service.scrape.runtime.MasterProfileLockedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author fengwk
 */
public class BrowserWorkerManagerTest {

    @TempDir
    Path tempDir;

    private BrowserWorkerManager manager;

    @AfterEach
    public void tearDown() {
        if (manager != null) {
            manager.shutdown();
        }
    }

    private MmhProperties createMmhProperties() {
        MmhProperties mmhProperties = new MmhProperties();
        mmhProperties.setConfigPath(tempDir.toString());
        return mmhProperties;
    }

    @Test
    public void shouldExecuteDefaultTaskSuccessfully() {
        MmhProperties mmhProperties = createMmhProperties();
        BrowserProperties properties = new BrowserProperties();
        properties.setWorkerPoolMinSize(1);
        properties.setWorkerPoolMaxSize(1);
        properties.setQueueOfferTimeoutMs(100);
        properties.setDefaultProfileId("master");

        LoginLockManager loginLockManager = mock(LoginLockManager.class);
        when(loginLockManager.tryAcquire(any(Path.class), anyLong(), anyLong()))
            .thenReturn(mock(LoginLockManager.LoginLock.class));

        manager = new BrowserWorkerManager(mmhProperties, properties, loginLockManager, new ProfileIdValidator(properties));

        String result = manager.executeDefault(context -> "ok");

        assertThat(result).isEqualTo("ok");
    }

    @Test
    public void shouldPropagateDefaultTaskFailure() {
        MmhProperties mmhProperties = createMmhProperties();
        BrowserProperties properties = new BrowserProperties();
        properties.setWorkerPoolMinSize(1);
        properties.setWorkerPoolMaxSize(1);
        properties.setQueueOfferTimeoutMs(100);
        properties.setDefaultProfileId("master");

        LoginLockManager loginLockManager = mock(LoginLockManager.class);
        when(loginLockManager.tryAcquire(any(Path.class), anyLong(), anyLong()))
            .thenReturn(mock(LoginLockManager.LoginLock.class));

        manager = new BrowserWorkerManager(mmhProperties, properties, loginLockManager, new ProfileIdValidator(properties));

        assertThatThrownBy(() -> manager.executeDefault(context -> {
            throw new RuntimeException("boom");
        }))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("boom");
    }

    @Test
    public void shouldPropagateMasterProfileLockedException() {
        MmhProperties mmhProperties = createMmhProperties();
        BrowserProperties properties = new BrowserProperties();
        properties.setWorkerPoolMinSize(0);
        properties.setWorkerPoolMaxSize(1);
        properties.setQueueOfferTimeoutMs(100);
        properties.setDefaultProfileId("master");
        properties.setMasterProfileLockTimeoutMs(0);

        LoginLockManager loginLockManager = mock(LoginLockManager.class);
        when(loginLockManager.tryAcquire(any(Path.class), anyLong(), anyLong())).thenReturn(null);

        manager = new BrowserWorkerManager(mmhProperties, properties, loginLockManager, new ProfileIdValidator(properties));

        assertThatThrownBy(() -> manager.executeMaster("master", context -> "ok"))
            .isInstanceOf(MasterProfileLockedException.class)
            .hasMessage(MasterProfileLockedException.DEFAULT_MESSAGE);
    }

}
