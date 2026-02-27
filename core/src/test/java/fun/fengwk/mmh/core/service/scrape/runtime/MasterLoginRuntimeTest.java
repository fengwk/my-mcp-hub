package fun.fengwk.mmh.core.service.scrape.runtime;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import fun.fengwk.mmh.core.service.browser.BrowserProperties;
import fun.fengwk.mmh.core.service.browser.coordination.ProfileIdValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

/**
 * @author fengwk
 */
public class MasterLoginRuntimeTest {

    @TempDir
    Path tempDir;

    @Test
    public void shouldResolveUserDataDirFromMasterUserDataRoot() {
        BrowserProperties browserProperties = new BrowserProperties();
        browserProperties.setDefaultProfileId("master");
        browserProperties.setMasterUserDataRoot(tempDir.resolve("browser-data").toString());
        ProfileIdValidator validator = new ProfileIdValidator(browserProperties);
        MasterLoginRuntime runtime = new MasterLoginRuntime(validator, browserProperties);

        Path userDataDir = runtime.resolveUserDataDir("master");

        assertThat(userDataDir).isEqualTo(tempDir.resolve("browser-data/master"));
        assertThat(Files.exists(userDataDir)).isTrue();
    }

    @Test
    public void shouldClosePlaywrightWhenOpenFailed() {
        BrowserProperties browserProperties = new BrowserProperties();
        browserProperties.setDefaultProfileId("master");
        browserProperties.setMasterUserDataRoot(tempDir.resolve("browser-data").toString());
        ProfileIdValidator validator = new ProfileIdValidator(browserProperties);

        Playwright playwright = mock(Playwright.class);
        BrowserType browserType = mock(BrowserType.class);
        when(playwright.chromium()).thenReturn(browserType);
        when(browserType.launchPersistentContext(any(Path.class), any(BrowserType.LaunchPersistentContextOptions.class)))
            .thenThrow(new RuntimeException("boom"));

        MasterLoginRuntime runtime = new MasterLoginRuntime(validator, browserProperties, () -> playwright);

        assertThatThrownBy(() -> runtime.open("master"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("failed to open master login runtime");
        verify(playwright).close();
    }

    @Test
    public void shouldApplyLoginArgsAndInitialPage() {
        BrowserProperties browserProperties = new BrowserProperties();
        Path chromePath = tempDir.resolve("chrome-bin");
        browserProperties.setUserAgent("UA");
        browserProperties.setAcceptLanguage("zh-CN,zh;q=0.9");
        browserProperties.setLocale("zh-CN");
        browserProperties.setTimezoneId("Asia/Shanghai");
        browserProperties.setExtraHeaders(Map.of("Sec-Fetch-Mode", "navigate"));
        browserProperties.setLaunchArgs(List.of("--disable-blink-features=AutomationControlled"));
        browserProperties.setBrowserChannel("chrome");
        browserProperties.setExecutablePath(chromePath.toString());
        browserProperties.setIgnoreDefaultArgs(List.of("--enable-automation"));
        browserProperties.setDefaultProfileId("master");
        browserProperties.setMasterUserDataRoot(tempDir.resolve("browser-data").toString());
        browserProperties.setMasterLoginArgs(List.of("--force-device-scale-factor=2"));
        browserProperties.setMasterLoginInitialPageUrl("https://example.com");
        browserProperties.setMasterLoginNavigateTimeoutMs(2000);
        ProfileIdValidator validator = new ProfileIdValidator(browserProperties);

        Playwright playwright = mock(Playwright.class);
        BrowserType browserType = mock(BrowserType.class);
        BrowserContext browserContext = mock(BrowserContext.class);
        Page page = mock(Page.class);
        when(playwright.chromium()).thenReturn(browserType);
        ArgumentCaptor<BrowserType.LaunchPersistentContextOptions> optionsCaptor =
            ArgumentCaptor.forClass(BrowserType.LaunchPersistentContextOptions.class);
        when(browserType.launchPersistentContext(any(Path.class), optionsCaptor.capture()))
            .thenReturn(browserContext);
        when(browserContext.pages()).thenReturn(List.of(page));

        MasterLoginRuntime runtime = new MasterLoginRuntime(validator, browserProperties, () -> playwright);
        MasterLoginRuntime.HeadedSession session = runtime.open("master");

        assertThat(session.context()).isEqualTo(browserContext);
        verify(page).navigate(eq("https://example.com"), any(Page.NavigateOptions.class));
        verify(browserContext, never()).newPage();
        BrowserType.LaunchPersistentContextOptions options = optionsCaptor.getValue();
        assertThat(options.args).contains("--force-device-scale-factor=2");
        assertThat(options.args).contains("--disable-blink-features=AutomationControlled");
        assertThat(options.viewportSize).isEmpty();
        assertThat(options.userAgent).isEqualTo("UA");
        assertThat(options.locale).isEqualTo("zh-CN");
        assertThat(options.timezoneId).isEqualTo("Asia/Shanghai");
        assertThat(options.extraHTTPHeaders).containsEntry("Sec-Fetch-Mode", "navigate");
        assertThat(options.extraHTTPHeaders).containsEntry("Accept-Language", "zh-CN,zh;q=0.9");
        assertThat(options.channel).isEqualTo("chrome");
        assertThat(options.executablePath).isEqualTo(chromePath);
        assertThat(options.ignoreDefaultArgs).contains("--enable-automation");
    }

    @Test
    public void shouldReuseAutoCreatedPageIfAvailable() {
        BrowserProperties browserProperties = new BrowserProperties();
        browserProperties.setDefaultProfileId("master");
        browserProperties.setMasterUserDataRoot(tempDir.resolve("browser-data").toString());
        browserProperties.setMasterLoginInitialPageUrl("https://example.com");
        ProfileIdValidator validator = new ProfileIdValidator(browserProperties);

        Playwright playwright = mock(Playwright.class);
        BrowserType browserType = mock(BrowserType.class);
        BrowserContext browserContext = mock(BrowserContext.class);
        Page page = mock(Page.class);
        when(playwright.chromium()).thenReturn(browserType);
        when(browserType.launchPersistentContext(any(Path.class), any(BrowserType.LaunchPersistentContextOptions.class)))
            .thenReturn(browserContext);
        when(browserContext.pages()).thenReturn(List.of(), List.of(page));

        MasterLoginRuntime runtime = new MasterLoginRuntime(validator, browserProperties, () -> playwright);
        runtime.open("master");

        verify(browserContext, never()).newPage();
        verify(page).navigate(eq("https://example.com"), any(Page.NavigateOptions.class));
    }

}
