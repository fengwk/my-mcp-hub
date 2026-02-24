package fun.fengwk.mmh.core.service.browser.coordination;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Login lock manager for headed login session.
 *
 * @author fengwk
 */
@Component
@RequiredArgsConstructor
public class LoginLockManager {

    private final ProfileIdValidator profileIdValidator;

    public LoginLock tryAcquire(String profileId) {
        try {
            Path lockPath = resolveLockPath(profileId);
            FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.WRITE);
            FileLock lock = tryLock(channel);
            if (lock == null) {
                channel.close();
                return null;
            }
            return new LoginLock(channel, lock);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to acquire login lock: " + ex.getMessage(), ex);
        }
    }

    public LoginLock tryAcquire(String profileId, long timeoutMs, long retryIntervalMs) {
        long normalizedTimeoutMs = Math.max(0L, timeoutMs);
        long normalizedRetryMs = Math.max(10L, retryIntervalMs);
        long deadline = System.currentTimeMillis() + normalizedTimeoutMs;
        do {
            LoginLock lock = tryAcquire(profileId);
            if (lock != null || normalizedTimeoutMs == 0L) {
                return lock;
            }
            sleep(normalizedRetryMs);
        } while (System.currentTimeMillis() <= deadline);
        return null;
    }

    private Path resolveLockPath(String profileId) {
        Path snapshotRoot = profileIdValidator.resolveSnapshotRoot();
        String normalized = profileIdValidator.normalizeProfileId(profileId);
        Path profileDir = profileIdValidator.resolveProfileDir(snapshotRoot, normalized);
        return profileDir.resolve("login.lock");
    }

    private FileLock tryLock(FileChannel channel) {
        try {
            return channel.tryLock();
        } catch (OverlappingFileLockException ex) {
            return null;
        } catch (Exception ex) {
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
