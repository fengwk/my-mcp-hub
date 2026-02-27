package fun.fengwk.mmh.core.service.browser.runtime;

import fun.fengwk.mmh.core.service.browser.BrowserProperties;
import fun.fengwk.mmh.core.service.browser.coordination.LoginLockManager;
import fun.fengwk.mmh.core.service.browser.coordination.ProfileIdValidator;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Worker manager with dual pools.
 *
 * @author fengwk
 */
@Component
public class BrowserWorkerManager {

    private final String masterProfileId;
    private final DefaultBrowserWorkerPool defaultPool;
    private final MasterBrowserWorkerPool masterPool;

    @Autowired
    public BrowserWorkerManager(
        BrowserProperties browserProperties,
        LoginLockManager loginLockManager,
        ProfileIdValidator profileIdValidator
    ) {
        WorkerPoolConfig defaultPoolConfig = buildDefaultPoolConfig(browserProperties);
        Path profileRoot = Paths.get(browserProperties.getMasterUserDataRoot()).toAbsolutePath().normalize();
        this.masterProfileId = profileIdValidator.normalizeProfileId(browserProperties.getDefaultProfileId());

        this.defaultPool = new DefaultBrowserWorkerPool(
            defaultPoolConfig,
            profileRoot,
            browserProperties,
            loginLockManager
        );
        this.masterPool = new MasterBrowserWorkerPool(
            profileRoot,
            this.masterProfileId,
            browserProperties,
            loginLockManager,
            browserProperties.getMasterProfileLockTimeoutMs()
        );
    }

    public <T> T executeDefault(BrowserTask<T> task) {
        return defaultPool.execute(task);
    }

    public <T> T executeMaster(String profileId, BrowserTask<T> task) {
        if (!masterProfileId.equals(profileId)) {
            throw new IllegalArgumentException("unsupported master profileId: " + profileId);
        }
        return masterPool.execute(task);
    }

    @PreDestroy
    public void shutdown() {
        defaultPool.shutdown();
        masterPool.shutdown();
    }

    private WorkerPoolConfig buildDefaultPoolConfig(BrowserProperties browserProperties) {
        return WorkerPoolConfig.builder()
            .minWorkers(browserProperties.getWorkerPoolMinSizePerProcess())
            .maxWorkers(browserProperties.getWorkerPoolMaxSizePerProcess())
            .queueTimeoutMs(browserProperties.getQueueOfferTimeoutMs())
            .build();
    }

}
