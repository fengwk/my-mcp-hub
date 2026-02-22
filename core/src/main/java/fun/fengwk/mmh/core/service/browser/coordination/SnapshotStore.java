package fun.fengwk.mmh.core.service.browser.coordination;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.fengwk.mmh.core.service.browser.BrowserProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.UUID;

/**
 * Snapshot read/write and CAS publish.
 *
 * @author fengwk
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SnapshotStore {

    private static final String INITIAL_STATE = "{\"cookies\":[],\"origins\":[]}";

    private final BrowserProperties browserProperties;
    private final ProfileIdValidator profileIdValidator;
    private final ObjectMapper objectMapper;

    public void ensureInitialized(String profileId) {
        try {
            Path snapshotRoot = profileIdValidator.resolveSnapshotRoot();
            String normalized = profileIdValidator.normalizeProfileId(profileId);
            Path profileDir = profileIdValidator.resolveProfileDir(snapshotRoot, normalized);
            Path tmpDir = profileDir.resolve("tmp");
            Path statePath = profileDir.resolve("state.json");
            Path metaPath = profileDir.resolve("meta.json");
            Path loginLockPath = profileDir.resolve("login.lock");
            Path publishLockPath = profileDir.resolve("publish.lock");

            Files.createDirectories(tmpDir);
            writeAtomicallyIfAbsent(statePath, INITIAL_STATE);

            SnapshotMetadata metadata = new SnapshotMetadata();
            metadata.setProfileId(normalized);
            metadata.setVersion(0L);
            metadata.setUpdatedAt(Instant.now().toString());
            metadata.setWriterId("bootstrap");
            writeAtomicallyIfAbsent(metaPath, objectMapper.writeValueAsString(metadata));

            createFileIfAbsent(loginLockPath);
            createFileIfAbsent(publishLockPath);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to initialize snapshot: " + ex.getMessage(), ex);
        }
    }

    public StorageStateSnapshot readLatest(String profileId) {
        try {
            Path profileDir = profileIdValidator.resolveProfileDir(
                profileIdValidator.resolveSnapshotRoot(),
                profileIdValidator.normalizeProfileId(profileId)
            );
            SnapshotMetadata metadata = readMeta(profileDir.resolve("meta.json"));
            return StorageStateSnapshot.builder()
                .profileId(metadata.getProfileId())
                .version(metadata.getVersion())
                .statePath(profileDir.resolve("state.json"))
                .build();
        } catch (Exception ex) {
            throw new IllegalStateException("failed to read snapshot: " + ex.getMessage(), ex);
        }
    }

    public boolean tryPublish(String profileId, long baseVersion, String state) {
        Path profileDir = profileIdValidator.resolveProfileDir(
            profileIdValidator.resolveSnapshotRoot(),
            profileIdValidator.normalizeProfileId(profileId)
        );
        Path lockPath = profileDir.resolve("publish.lock");
        long deadline = System.currentTimeMillis() + browserProperties.getSnapshotPublishLockTimeoutMs();

        while (System.currentTimeMillis() <= deadline) {
            try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.WRITE);
                 FileLock ignored = tryFileLock(channel)) {
                if (ignored == null) {
                    Thread.sleep(20);
                    continue;
                }

                Path metaPath = profileDir.resolve("meta.json");
                SnapshotMetadata metadata = readMeta(metaPath);
                if (metadata.getVersion() != baseVersion) {
                    return false;
                }

                writeAtomically(profileDir.resolve("state.json"), state);
                metadata.setVersion(baseVersion + 1);
                metadata.setUpdatedAt(Instant.now().toString());
                metadata.setWriterId("worker");
                writeAtomically(metaPath, objectMapper.writeValueAsString(metadata));
                return true;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.warn("publish snapshot interrupted, profileId={}", profileId);
                return false;
            } catch (Exception ex) {
                log.warn("publish snapshot failed, profileId={}, error={}", profileId, ex.getMessage());
                return false;
            }
        }

        return false;
    }

    private SnapshotMetadata readMeta(Path metaPath) throws Exception {
        String metaJson = Files.readString(metaPath);
        return objectMapper.readValue(metaJson, SnapshotMetadata.class);
    }

    private void writeAtomically(Path targetPath, String content) throws Exception {
        Path tmpDir = targetPath.getParent().resolve("tmp");
        Files.createDirectories(tmpDir);
        Path tmpPath = tmpDir.resolve(targetPath.getFileName() + "." + UUID.randomUUID() + ".tmp");
        Files.writeString(tmpPath, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        try {
            Files.move(tmpPath, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ex) {
            Files.move(tmpPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void writeAtomicallyIfAbsent(Path targetPath, String content) throws Exception {
        Path tmpDir = targetPath.getParent().resolve("tmp");
        Files.createDirectories(tmpDir);
        Path tmpPath = tmpDir.resolve(targetPath.getFileName() + "." + UUID.randomUUID() + ".tmp");
        Files.writeString(tmpPath, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        try {
            Files.move(tmpPath, targetPath, StandardCopyOption.ATOMIC_MOVE);
        } catch (FileAlreadyExistsException ex) {
            Files.deleteIfExists(tmpPath);
        } catch (AtomicMoveNotSupportedException ex) {
            try {
                Files.move(tmpPath, targetPath);
            } catch (FileAlreadyExistsException existsEx) {
                Files.deleteIfExists(tmpPath);
            }
        }
    }

    private void createFileIfAbsent(Path path) throws Exception {
        try {
            Files.createFile(path);
        } catch (FileAlreadyExistsException ex) {
        }
    }

    private static FileLock tryFileLock(FileChannel channel) {
        try {
            return channel.tryLock();
        } catch (OverlappingFileLockException ex) {
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    @Data
    public static class SnapshotMetadata {

        private String profileId;
        private long version;
        private String updatedAt;
        private String writerId;

    }

}
