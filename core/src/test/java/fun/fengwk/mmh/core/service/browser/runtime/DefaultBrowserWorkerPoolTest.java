package fun.fengwk.mmh.core.service.browser.runtime;

import fun.fengwk.mmh.core.service.browser.BrowserProperties;
import fun.fengwk.mmh.core.service.browser.coordination.LoginLockManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * @author fengwk
 */
public class DefaultBrowserWorkerPoolTest {

    @TempDir
    Path tempDir;

    private TestableDefaultBrowserWorkerPool workerPool;

    @AfterEach
    public void tearDown() {
        if (workerPool != null) {
            workerPool.shutdown();
        }
    }

    @Test
    public void shouldGenerateSlaveProfileIdWithProcessPrefix() {
        workerPool = createPool(tempDir.resolve("profiles"));

        long pid = ProcessHandle.current().pid();
        String first = workerPool.nextProfileId();
        String second = workerPool.nextProfileId();

        assertThat(first).isEqualTo("slave_" + pid + "_1");
        assertThat(second).isEqualTo("slave_" + pid + "_2");
    }

    @Test
    public void shouldCleanupZombieSlaveProfilesOnStartup() throws Exception {
        Path profileRoot = tempDir.resolve("profiles");
        Path zombieProfile = profileRoot.resolve("slave_9223372036854775807_1");
        Path liveProfile = profileRoot.resolve("slave_" + ProcessHandle.current().pid() + "_2");
        Path unmanagedProfile = profileRoot.resolve("master");

        Files.createDirectories(zombieProfile);
        Files.createDirectories(liveProfile);
        Files.createDirectories(unmanagedProfile);
        Files.writeString(zombieProfile.resolve("marker.txt"), "zombie");
        Files.writeString(liveProfile.resolve("marker.txt"), "live");
        Files.writeString(unmanagedProfile.resolve("marker.txt"), "master");

        workerPool = createPool(profileRoot);

        assertThat(Files.exists(zombieProfile)).isFalse();
        assertThat(Files.exists(liveProfile)).isTrue();
        assertThat(Files.exists(unmanagedProfile)).isTrue();
    }

    private TestableDefaultBrowserWorkerPool createPool(Path profileRoot) {
        WorkerPoolConfig config = WorkerPoolConfig.builder()
            .minWorkers(0)
            .maxWorkers(1)
            .queueTimeoutMs(100)
            .build();
        BrowserProperties browserProperties = new BrowserProperties();
        LoginLockManager loginLockManager = mock(LoginLockManager.class);
        return new TestableDefaultBrowserWorkerPool(config, profileRoot, browserProperties, loginLockManager);
    }

    private static class TestableDefaultBrowserWorkerPool extends DefaultBrowserWorkerPool {

        private TestableDefaultBrowserWorkerPool(
            WorkerPoolConfig config,
            Path profileRoot,
            BrowserProperties browserProperties,
            LoginLockManager loginLockManager
        ) {
            super(config, profileRoot, browserProperties, loginLockManager);
        }

        private String nextProfileId() {
            return allocateProfileId();
        }

    }

}
