package fun.fengwk.mmh.core.service.browser.coordination;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Login lock manager for headed login session.
 *
 * @author fengwk
 */
@Component
@Slf4j
public class LoginLockManager {

    public LoginLock tryAcquire(Path lockPath) {
        try {
            ensureParentDirectories(lockPath);
            FileChannel channel = FileChannel.open(lockPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
            );
            FileLock lock = tryLock(channel, lockPath);
            if (lock == null) {
                channel.close();
                return null;
            }
            return new LoginLock(channel, lock);
        } catch (Exception ex) {
            log.warn("failed to acquire login lock, lockPath={}, error={}", lockPath, ex.getMessage(), ex);
            throw new IllegalStateException("failed to acquire login lock: " + ex.getMessage(), ex);
        }
    }

    public LoginLock tryAcquire(Path lockPath, long timeoutMs, long retryIntervalMs) {
        long normalizedTimeoutMs = Math.max(0L, timeoutMs);
        long normalizedRetryMs = Math.max(10L, retryIntervalMs);
        long deadline = System.currentTimeMillis() + normalizedTimeoutMs;
        do {
            LoginLock lock = tryAcquire(lockPath);
            if (lock != null || normalizedTimeoutMs == 0L) {
                return lock;
            }
            sleep(normalizedRetryMs);
        } while (System.currentTimeMillis() <= deadline);
        log.info(
            "login lock acquire timed out, lockPath={}, timeoutMs={}, retryIntervalMs={}",
            lockPath,
            normalizedTimeoutMs,
            normalizedRetryMs
        );
        return null;
    }

    private void ensureParentDirectories(Path lockPath) throws Exception {
        Path normalizedLockPath = lockPath.toAbsolutePath().normalize();
        Path parent = normalizedLockPath.getParent();
        if (parent == null) {
            log.warn("invalid lock path, path={}", lockPath);
            throw new IllegalArgumentException("invalid lock path");
        }
        Files.createDirectories(parent);
    }

    private FileLock tryLock(FileChannel channel, Path lockPath) {
        try {
            return channel.tryLock();
        } catch (OverlappingFileLockException ex) {
            log.debug("login lock already held in current process, lockPath={}", lockPath);
            return null;
        } catch (Exception ex) {
            log.warn("failed to try lock file, lockPath={}, error={}", lockPath, ex.getMessage(), ex);
            return null;
        }
    }

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.info("sleep interrupted while waiting for login lock, millis={}", millis);
        }
    }

    public static class LoginLock implements AutoCloseable {

        private final FileChannel channel;
        private final FileLock lock;

        private LoginLock(FileChannel channel, FileLock lock) {
            this.channel = channel;
            this.lock = lock;
        }

        @Override
        public void close() {
            try {
                lock.release();
            } catch (Exception ex) {
                // Ignore release exception.
            }
            try {
                channel.close();
            } catch (Exception ex) {
                // Ignore close exception.
            }
        }

    }

}
