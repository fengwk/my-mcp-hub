package fun.fengwk.mmh.core.service.browser.runtime;

import fun.fengwk.mmh.core.service.browser.BrowserProperties;
import fun.fengwk.mmh.core.service.browser.coordination.ProfileIdValidator;
import fun.fengwk.mmh.core.service.browser.coordination.SnapshotBootstrap;
import fun.fengwk.mmh.core.service.browser.coordination.SnapshotPublisher;
import fun.fengwk.mmh.core.service.browser.coordination.SnapshotStore;
import fun.fengwk.mmh.core.service.browser.coordination.StorageStateSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author fengwk
 */
@ExtendWith(MockitoExtension.class)
public class BrowserTaskExecutorTest {

    @Mock
    private ProfileIdValidator profileIdValidator;

    @Mock
    private SnapshotBootstrap snapshotBootstrap;

    @Mock
    private SnapshotStore snapshotStore;

    @Mock
    private SnapshotPublisher snapshotPublisher;

    @Mock
    private BrowserWorkerManager browserWorkerManager;

    @SuppressWarnings("unchecked")
    @Test
    public void shouldPrepareSnapshotAndDelegateToWorkerManager() {
        BrowserProperties browserProperties = new BrowserProperties();
        BrowserTaskExecutor executor = new BrowserTaskExecutor(
            browserProperties,
            profileIdValidator,
            snapshotBootstrap,
            snapshotStore,
            snapshotPublisher,
            browserWorkerManager
        );
        StorageStateSnapshot snapshot = StorageStateSnapshot.builder()
            .profileId("master")
            .version(2L)
            .statePath(Paths.get("/tmp/master/state.json"))
            .build();

        when(profileIdValidator.normalizeProfileId("master")).thenReturn("master");
        when(snapshotStore.readLatest("master")).thenReturn(snapshot);
        when(browserWorkerManager.execute(any())).thenReturn("ok");

        String result = executor.execute("master", context -> "ignored");

        assertThat(result).isEqualTo("ok");
        verify(snapshotBootstrap).ensureInitialized("master");
        verify(snapshotStore).readLatest("master");
        verify(browserWorkerManager).execute(any());
    }

}
