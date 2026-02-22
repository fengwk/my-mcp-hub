package fun.fengwk.mmh.core.service.scrape.runtime;

import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import fun.fengwk.mmh.core.service.browser.BrowserProperties;
import fun.fengwk.mmh.core.service.browser.coordination.ProfileIdValidator;
import fun.fengwk.mmh.core.service.scrape.ScrapeProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author fengwk
 */
public class MasterLoginRuntimeTest {

    @TempDir
    Path tempDir;

    @Test
    public void shouldResolveUserDataDirFromMasterUserDataRoot() {
        BrowserProperties browserProperties = new BrowserProperties();
        ScrapeProperties scrapeProperties = new ScrapeProperties();
        scrapeProperties.setDefaultProfileId("master");
        scrapeProperties.setMasterUserDataRoot(tempDir.resolve("browser-data").toString());
        ProfileIdValidator validator = new ProfileIdValidator(browserProperties, scrapeProperties);
        MasterLoginRuntime runtime = new MasterLoginRuntime(scrapeProperties, validator);

        Path userDataDir = runtime.resolveUserDataDir("master");

        assertThat(userDataDir).isEqualTo(tempDir.resolve("browser-data/master"));
        assertThat(Files.exists(userDataDir)).isTrue();
    }

    @Test
    public void shouldClosePlaywrightWhenOpenFailed() {
        BrowserProperties browserProperties = new BrowserProperties();
        ScrapeProperties scrapeProperties = new ScrapeProperties();
        scrapeProperties.setDefaultProfileId("master");
        scrapeProperties.setMasterUserDataRoot(tempDir.resolve("browser-data").toString());
        ProfileIdValidator validator = new ProfileIdValidator(browserProperties, scrapeProperties);

        Playwright playwright = mock(Playwright.class);
        BrowserType browserType = mock(BrowserType.class);
        when(playwright.chromium()).thenReturn(browserType);
        when(browserType.launchPersistentContext(any(Path.class), any(BrowserType.LaunchPersistentContextOptions.class)))
            .thenThrow(new RuntimeException("boom"));

        MasterLoginRuntime runtime = new MasterLoginRuntime(scrapeProperties, validator, () -> playwright);

        assertThatThrownBy(() -> runtime.open("master"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("failed to open master login runtime");
        verify(playwright).close();
    }

}
