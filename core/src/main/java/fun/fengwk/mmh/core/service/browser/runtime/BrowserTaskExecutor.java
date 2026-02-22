package fun.fengwk.mmh.core.service.browser.runtime;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import fun.fengwk.mmh.core.service.browser.BrowserProperties;
import fun.fengwk.mmh.core.service.browser.coordination.ProfileIdValidator;
import fun.fengwk.mmh.core.service.browser.coordination.SnapshotBootstrap;
import fun.fengwk.mmh.core.service.browser.coordination.SnapshotPublisher;
import fun.fengwk.mmh.core.service.browser.coordination.SnapshotStore;
import fun.fengwk.mmh.core.service.browser.coordination.StorageStateSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Executes generic browser tasks with shared snapshot synchronization.
 *
 * @author fengwk
 */
@Component
@RequiredArgsConstructor
public class BrowserTaskExecutor {

    private final BrowserProperties browserProperties;
    private final ProfileIdValidator profileIdValidator;
    private final SnapshotBootstrap snapshotBootstrap;
    private final SnapshotStore snapshotStore;
    private final SnapshotPublisher snapshotPublisher;
    private final BrowserWorkerManager browserWorkerManager;

    public <T> T execute(String profileId, BrowserTask<T> task) {
        String normalizedProfileId = profileIdValidator.normalizeProfileId(profileId);
        snapshotBootstrap.ensureInitialized(normalizedProfileId);
        StorageStateSnapshot snapshot = snapshotStore.readLatest(normalizedProfileId);

        return browserWorkerManager.execute(() ->
            doExecute(normalizedProfileId, snapshot, task)
        );
    }

    private <T> T doExecute(String profileId, StorageStateSnapshot snapshot, BrowserTask<T> task) throws Exception {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true)
            );
            try (BrowserContext context = browser.newContext(
                new Browser.NewContextOptions().setStorageStatePath(snapshot.getStatePath())
            )) {
                Page page = context.newPage();
                BrowserRuntimeContext runtimeContext = BrowserRuntimeContext.builder()
                    .profileId(profileId)
                    .baseVersion(snapshot.getVersion())
                    .browserContext(context)
                    .page(page)
                    .build();

                T result = task.execute(runtimeContext);
                if (browserProperties.isSnapshotRefreshOnRequestEnd()) {
                    snapshotPublisher.tryPublish(profileId, snapshot.getVersion(), context.storageState());
                }
                return result;
            } finally {
                browser.close();
            }
        }
    }

}
