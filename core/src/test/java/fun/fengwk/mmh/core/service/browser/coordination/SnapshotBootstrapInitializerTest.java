package fun.fengwk.mmh.core.service.browser.coordination;

import fun.fengwk.mmh.core.service.scrape.ScrapeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * @author fengwk
 */
public class SnapshotBootstrapInitializerTest {

    @Test
    public void shouldInitializeDefaultProfile() {
        SnapshotBootstrap snapshotBootstrap = mock(SnapshotBootstrap.class);
        ScrapeProperties scrapeProperties = new ScrapeProperties();
        scrapeProperties.setDefaultProfileId("master");
        SnapshotBootstrapInitializer initializer = new SnapshotBootstrapInitializer(snapshotBootstrap, scrapeProperties);

        initializer.run(new DefaultApplicationArguments(new String[] {}));

        verify(snapshotBootstrap).ensureInitialized("master");
    }

    @Test
    public void shouldSkipWhenDefaultProfileBlank() {
        SnapshotBootstrap snapshotBootstrap = mock(SnapshotBootstrap.class);
        ScrapeProperties scrapeProperties = new ScrapeProperties();
        scrapeProperties.setDefaultProfileId(" ");
        SnapshotBootstrapInitializer initializer = new SnapshotBootstrapInitializer(snapshotBootstrap, scrapeProperties);

        initializer.run(new DefaultApplicationArguments(new String[] {}));

        verify(snapshotBootstrap, never()).ensureInitialized(" ");
    }

}
