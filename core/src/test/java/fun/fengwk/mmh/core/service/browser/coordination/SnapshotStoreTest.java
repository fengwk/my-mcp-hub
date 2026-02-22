package fun.fengwk.mmh.core.service.browser.coordination;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.fengwk.mmh.core.service.browser.BrowserProperties;
import fun.fengwk.mmh.core.service.scrape.ScrapeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author fengwk
 */
public class SnapshotStoreTest {

    @TempDir
    Path tempDir;

    private SnapshotStore snapshotStore;

    @BeforeEach
    public void setUp() {
        BrowserProperties browserProperties = new BrowserProperties();
        browserProperties.setSnapshotRoot(tempDir.resolve("snapshots").toString());
        browserProperties.setSnapshotPublishLockTimeoutMs(500);
        ScrapeProperties scrapeProperties = new ScrapeProperties();
        ProfileIdValidator profileIdValidator = new ProfileIdValidator(browserProperties, scrapeProperties);
        snapshotStore = new SnapshotStore(browserProperties, profileIdValidator, new ObjectMapper());
    }

    @Test
    public void shouldInitializeSnapshotFiles() {
        snapshotStore.ensureInitialized("master");

        Path profileDir = tempDir.resolve("snapshots/master");
        assertThat(Files.exists(profileDir.resolve("state.json"))).isTrue();
        assertThat(Files.exists(profileDir.resolve("meta.json"))).isTrue();
        assertThat(Files.exists(profileDir.resolve("login.lock"))).isTrue();
        assertThat(Files.exists(profileDir.resolve("publish.lock"))).isTrue();
    }

    @Test
    public void shouldReadLatestSnapshotAfterInitialization() {
        snapshotStore.ensureInitialized("master");

        StorageStateSnapshot snapshot = snapshotStore.readLatest("master");

        assertThat(snapshot.getProfileId()).isEqualTo("master");
        assertThat(snapshot.getVersion()).isEqualTo(0L);
        assertThat(snapshot.getStatePath().toString()).endsWith("state.json");
    }

    @Test
    public void shouldPublishSnapshotWithCas() throws Exception {
        snapshotStore.ensureInitialized("master");

        boolean success = snapshotStore.tryPublish("master", 0L, "{\"cookies\":[1],\"origins\":[]}");

        assertThat(success).isTrue();
        StorageStateSnapshot snapshot = snapshotStore.readLatest("master");
        assertThat(snapshot.getVersion()).isEqualTo(1L);
        assertThat(Files.readString(snapshot.getStatePath())).contains("cookies");
    }

    @Test
    public void shouldRejectStaleSnapshotPublish() {
        snapshotStore.ensureInitialized("master");
        snapshotStore.tryPublish("master", 0L, "{\"cookies\":[1],\"origins\":[]}");

        boolean success = snapshotStore.tryPublish("master", 0L, "{\"cookies\":[],\"origins\":[1]}");

        assertThat(success).isFalse();
    }

    @Test
    public void shouldKeepInterruptedFlagWhenPublishInterrupted() throws Exception {
        snapshotStore.ensureInitialized("master");
        Path lockPath = tempDir.resolve("snapshots/master/publish.lock");

        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            Thread.currentThread().interrupt();
            try {
                boolean success = snapshotStore.tryPublish("master", 0L, "{\"cookies\":[],\"origins\":[]}");
                assertThat(success).isFalse();
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
            } finally {
                Thread.interrupted();
            }
        }
    }

}
