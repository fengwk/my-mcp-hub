package fun.fengwk.mmh.core.service.browser.runtime;

import fun.fengwk.mmh.core.service.browser.BrowserProperties;
import fun.fengwk.mmh.core.service.browser.coordination.LoginLockManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Default browser worker pool with auto-allocated slave profiles.
 *
 * <p>Profile lifecycle:
 * <ul>
 *     <li>Runtime profiles are named {@code slave_{pid}_{n}} to avoid cross-process collisions.</li>
 *     <li>On startup, orphan profiles from dead processes are cleaned proactively.</li>
 *     <li>On worker close, managed slave directories are deleted.</li>
 * </ul>
 *
 * @author fengwk
 */
public class DefaultBrowserWorkerPool extends BrowserWorkerPool {

    private static final Logger log = LoggerFactory.getLogger(DefaultBrowserWorkerPool.class);

    /**
     * Managed transient profile format: slave_{pid}_{sequence}.
     */
    private static final Pattern MANAGED_SLAVE_PROFILE_PATTERN = Pattern.compile("^slave_(\\d+)_(\\d+)$");

    /**
     * Current process id used in generated profile name.
     */
    private static final long CURRENT_PROCESS_ID = ProcessHandle.current().pid();

    private final AtomicInteger slaveCounter = new AtomicInteger(1);

    public DefaultBrowserWorkerPool(
        WorkerPoolConfig config,
        Path profileRoot,
        BrowserProperties browserProperties,
        LoginLockManager loginLockManager
    ) {
        super("default", config, profileRoot, browserProperties, loginLockManager);
        cleanupZombieSlaveProfiles();
        initializeMinWorkers();
    }

    @Override
    protected String allocateProfileId() {
        // Sequence is process-local, pid provides cross-process uniqueness.
        return "slave_" + CURRENT_PROCESS_ID + "_" + slaveCounter.getAndIncrement();
    }

    @Override
    protected RuntimeException createBusyException() {
        return new DefaultBrowserWorkerBusyException("default browser worker pool is busy");
    }

    @Override
    protected boolean shouldCleanupProfileDir(String profileId) {
        return isManagedSlaveProfile(profileId);
    }

    @Override
    protected boolean resolveHeadlessMode() {
        return browserProperties.isSlaveHeadless();
    }

    private void cleanupZombieSlaveProfiles() {
        try {
            Files.createDirectories(profileRoot);
        } catch (Exception ex) {
            log.error("failed to initialize profile root, profileRoot={}, error={}", profileRoot, ex.getMessage(), ex);
            throw new IllegalStateException("failed to initialize profile root: " + ex.getMessage(), ex);
        }

        // Only scan top-level profile directories, never recurse into unrelated paths.
        try (Stream<Path> stream = Files.list(profileRoot)) {
            stream
                .filter(Files::isDirectory)
                .forEach(this::cleanupZombieSlaveProfile);
        } catch (Exception ex) {
            log.error("failed to scan profile root for zombie cleanup, profileRoot={}, error={}", profileRoot, ex.getMessage(), ex);
            throw new IllegalStateException("failed to cleanup zombie slave profiles: " + ex.getMessage(), ex);
        }
    }

    private void cleanupZombieSlaveProfile(Path profileDir) {
        String profileName = profileDir.getFileName().toString();
        Matcher matcher = MANAGED_SLAVE_PROFILE_PATTERN.matcher(profileName);
        // Never touch non-managed directories (e.g. master, user custom dirs).
        if (!matcher.matches()) {
            return;
        }

        long ownerPid = Long.parseLong(matcher.group(1));
        // Safety check: keep profiles owned by current process or any alive process.
        if (ownerPid == CURRENT_PROCESS_ID || isProcessAlive(ownerPid)) {
            return;
        }

        // Only dead-process managed profile can be cleaned.
        try (Stream<Path> stream = Files.walk(profileDir)) {
            stream
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ex) {
                        throw new IllegalStateException("failed to delete path: " + path, ex);
                    }
                });
            log.info("cleaned zombie slave profile dir: {}", profileDir);
        } catch (Exception ex) {
            log.error(
                "failed to cleanup zombie slave profile, profileName={}, ownerPid={}, profileDir={}, error={}",
                profileName,
                ownerPid,
                profileDir,
                ex.getMessage(),
                ex
            );
            throw new IllegalStateException("failed to cleanup zombie profile " + profileName + ": " + ex.getMessage(), ex);
        }
    }

    private boolean isProcessAlive(long pid) {
        return ProcessHandle.of(pid)
            .map(ProcessHandle::isAlive)
            .orElse(false);
    }

    private boolean isManagedSlaveProfile(String profileId) {
        // Keep cleanup scope strict and explicit.
        return MANAGED_SLAVE_PROFILE_PATTERN.matcher(profileId).matches();
    }

}
