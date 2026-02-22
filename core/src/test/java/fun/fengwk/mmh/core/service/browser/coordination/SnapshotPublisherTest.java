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
public class SnapshotPublisherTest {

    @Mock
    private SnapshotStore snapshotStore;

    @Test
    public void shouldDelegatePublishToSnapshotStore() {
        SnapshotPublisher snapshotPublisher = new SnapshotPublisher(snapshotStore);

        snapshotPublisher.tryPublish("master", 1L, "{}");

        verify(snapshotStore).tryPublish("master", 1L, "{}");
    }

}
