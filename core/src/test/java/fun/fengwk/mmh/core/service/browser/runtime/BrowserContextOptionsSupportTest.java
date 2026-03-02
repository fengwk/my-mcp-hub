package fun.fengwk.mmh.core.service.browser.runtime;

import com.microsoft.playwright.BrowserType;
import fun.fengwk.mmh.core.service.browser.BrowserProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author fengwk
 */
public class BrowserContextOptionsSupportTest {

    @Test
    public void shouldApplyTypedSettingsEvenWhenArgsContainConflicts() {
        BrowserProperties.BrowserProfileProperties profile = new BrowserProperties.BrowserProfileProperties();
        profile.setHeadless(false);
        profile.setUserAgent("ua-from-config");
        profile.setProxyServer("http://proxy-from-config:8080");
        profile.setLocale("zh-CN");
        profile.setAcceptLanguage("zh-CN,zh;q=0.9");

        BrowserType.LaunchPersistentContextOptions options = BrowserContextOptionsSupport.buildContextOptions(
            profile,
            profile.isHeadless(),
            List.of(
                "--headless=new",
                "--user-agent=ua-from-args",
                "--proxy-server=http://proxy-from-args:9090",
                "--lang=en-US"
            ),
            false
        );

        assertThat(options.headless).isFalse();
        assertThat(options.userAgent).isEqualTo("ua-from-config");
        assertThat(options.proxy).isNotNull();
        assertThat(options.proxy.server).isEqualTo("http://proxy-from-config:8080");
        assertThat(options.locale).isEqualTo("zh-CN");
        assertThat(options.extraHTTPHeaders).containsEntry("Accept-Language", "zh-CN,zh;q=0.9");
    }

    @Test
    public void shouldTrimBlankLaunchArgs() {
        BrowserProperties.BrowserProfileProperties profile = new BrowserProperties.BrowserProfileProperties();

        BrowserType.LaunchPersistentContextOptions options = BrowserContextOptionsSupport.buildContextOptions(
            profile,
            true,
            List.of("  ", "--disable-gpu", "\t", " --lang=zh-CN "),
            false
        );

        assertThat(options.headless).isTrue();
        assertThat(options.args).containsExactly("--disable-gpu", "--lang=zh-CN");
    }

    @Test
    public void shouldApplyMasterLoginDefaults() {
        BrowserProperties.BrowserProfileProperties profile = new BrowserProperties.BrowserProfileProperties();
        profile.setHeadless(true);

        BrowserType.LaunchPersistentContextOptions options = BrowserContextOptionsSupport.buildContextOptions(
            profile,
            false,
            List.of("--headless=new"),
            true
        );

        assertThat(options.headless).isFalse();
        assertThat(options.viewportSize).isEmpty();
    }

}
