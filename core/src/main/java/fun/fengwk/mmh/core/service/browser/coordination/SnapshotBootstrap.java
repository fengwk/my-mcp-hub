package fun.fengwk.mmh.core.service.browser.coordination;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Snapshot space bootstrap entry.
 *
 * @author fengwk
 */
@Component
@RequiredArgsConstructor
public class SnapshotBootstrap {

    private final SnapshotStore snapshotStore;

    public void ensureInitialized(String profileId) {
        snapshotStore.ensureInitialized(profileId);
    }

}
