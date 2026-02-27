package fun.fengwk.mmh.core.service.browser.coordination;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author fengwk
 */
public class LoginLockManagerTest {

    @TempDir
    Path tempDir;

    private LoginLockManager loginLockManager;
    private Path profileLockPath;

    @BeforeEach
    public void setUp() {
        loginLockManager = new LoginLockManager();
        profileLockPath = tempDir.resolve("browser-data").resolve("master").resolve("browser.lock");
    }

    @Test
    public void shouldAcquireAndReleaseLock() {
        try (LoginLockManager.LoginLock lock = loginLockManager.tryAcquire(profileLockPath)) {
            assertThat(lock).isNotNull();
        }

        try (LoginLockManager.LoginLock lock = loginLockManager.tryAcquire(profileLockPath)) {
            assertThat(lock).isNotNull();
        }
    }

    @Test
    public void shouldFailWhenLockHeld() {
        try (LoginLockManager.LoginLock lock = loginLockManager.tryAcquire(profileLockPath)) {
            assertThat(lock).isNotNull();
            LoginLockManager.LoginLock second = loginLockManager.tryAcquire(profileLockPath);
            assertThat(second).isNull();
            if (second != null) {
                second.close();
            }
        }
    }

    @Test
    public void shouldAcquireLockByPath() {
        Path lockPath = tempDir.resolve("custom").resolve("runtime.lock");
        try (LoginLockManager.LoginLock lock = loginLockManager.tryAcquire(lockPath)) {
            assertThat(lock).isNotNull();
        }

        try (LoginLockManager.LoginLock lock = loginLockManager.tryAcquire(lockPath)) {
            assertThat(lock).isNotNull();
        }
    }

}
