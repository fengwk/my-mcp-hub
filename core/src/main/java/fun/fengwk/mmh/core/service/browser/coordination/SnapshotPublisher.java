package fun.fengwk.mmh.core.service.browser.coordination;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.microsoft.playwright.BrowserContext;

/**
 * Snapshot publisher facade.
 *
 * @author fengwk
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SnapshotPublisher {

    private final SnapshotStore snapshotStore;

    public void publishFromContext(String profileId, BrowserContext context) {
        StorageStateSnapshot snapshot = snapshotStore.readLatest(profileId);
        boolean success = snapshotStore.tryPublish(profileId, snapshot.getVersion(), context.storageState());
        if (!success) {
            log.debug("drop stale snapshot, profileId={}, baseVersion={}", profileId, snapshot.getVersion());
        }
    }

    public void tryPublish(String profileId, long baseVersion, String state) {
        boolean success = snapshotStore.tryPublish(profileId, baseVersion, state);
        if (!success) {
            log.debug("drop stale snapshot, profileId={}, baseVersion={}", profileId, baseVersion);
        }
    }

}
