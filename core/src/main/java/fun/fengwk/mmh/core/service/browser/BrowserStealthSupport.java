package fun.fengwk.mmh.core.service.browser;

import com.microsoft.playwright.BrowserContext;
import fun.fengwk.convention4j.common.lang.StringUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * Stealth script helper for browser context.
 *
 * @author fengwk
 */
@Slf4j
public final class BrowserStealthSupport {

    static final String DEFAULT_STEALTH_SCRIPT = """
        (() => {
          try {
            Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
          } catch (e) {}
          try {
            Object.defineProperty(navigator, 'languages', { get: () => ['en-US', 'en'] });
          } catch (e) {}
          try {
            Object.defineProperty(navigator, 'plugins', { get: () => [1, 2, 3, 4, 5] });
          } catch (e) {}
          try {
            window.chrome = window.chrome || { runtime: {} };
          } catch (e) {}
          try {
            const originalQuery = window.navigator.permissions && window.navigator.permissions.query;
            if (originalQuery) {
              window.navigator.permissions.query = (parameters) => (
                parameters && parameters.name === 'notifications'
                  ? Promise.resolve({ state: Notification.permission })
                  : originalQuery(parameters)
              );
            }
          } catch (e) {}
        })();
        """;

    private BrowserStealthSupport() {
    }

    public static void apply(BrowserContext context, BrowserProperties properties) {
        String script = properties.resolveStealthScript();
        if (StringUtils.isBlank(script)) {
            return;
        }
        try {
            context.addInitScript(script);
        } catch (Exception ex) {
            // Stealth script is best-effort and should not abort browser startup.
            log.warn("failed to apply stealth script, error={}", ex.getMessage());
        }
    }

}
