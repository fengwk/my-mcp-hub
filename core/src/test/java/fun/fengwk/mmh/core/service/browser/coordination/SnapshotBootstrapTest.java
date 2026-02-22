package fun.fengwk.mmh.core.service.browser.coordination;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

/**
 * @author fengwk
 */
@ExtendWith(MockitoExtension.class)
public class SnapshotBootstrapTest {

    @Mock
    private SnapshotStore snapshotStore;

    @Test
    public void shouldDelegateInitializationToSnapshotStore() {
        SnapshotBootstrap snapshotBootstrap = new SnapshotBootstrap(snapshotStore);

        snapshotBootstrap.ensureInitialized("master");

        verify(snapshotStore).ensureInitialized("master");
    }

}
