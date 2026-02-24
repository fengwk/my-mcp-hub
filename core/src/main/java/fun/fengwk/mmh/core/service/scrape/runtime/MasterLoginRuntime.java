package fun.fengwk.mmh.core.service.scrape.runtime;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import fun.fengwk.convention4j.common.lang.StringUtils;
import fun.fengwk.mmh.core.service.browser.BrowserProperties;
import fun.fengwk.mmh.core.service.browser.BrowserStealthSupport;
import fun.fengwk.mmh.core.service.browser.coordination.ProfileIdValidator;
import fun.fengwk.mmh.core.service.scrape.ScrapeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Runtime for manual headed login session.
 *
 * @author fengwk
 */
@Slf4j
@Component
public class MasterLoginRuntime {

    private final ScrapeProperties scrapeProperties;
    private final ProfileIdValidator profileIdValidator;
    private final BrowserProperties browserProperties;
    private final PlaywrightFactory playwrightFactory;

    @Autowired
    public MasterLoginRuntime(
        ScrapeProperties scrapeProperties,
        ProfileIdValidator profileIdValidator,
        BrowserProperties browserProperties
    ) {
        this(scrapeProperties, profileIdValidator, browserProperties, Playwright::create);
    }

    MasterLoginRuntime(
        ScrapeProperties scrapeProperties,
        ProfileIdValidator profileIdValidator,
        BrowserProperties browserProperties,
        PlaywrightFactory playwrightFactory
    ) {
        this.scrapeProperties = scrapeProperties;
        this.profileIdValidator = profileIdValidator;
        this.browserProperties = browserProperties;
        this.playwrightFactory = playwrightFactory;
    }

    public HeadedSession open(String profileId) {
        Playwright playwright = null;
        try {
            Path userDataDir = resolveUserDataDir(profileId);
            playwright = playwrightFactory.create();
            BrowserType.LaunchPersistentContextOptions options = buildContextOptions();
            if (browserProperties.isIgnoreAllDefaultArgs()) {
                options.setIgnoreAllDefaultArgs(true);
            } else if (browserProperties.getIgnoreDefaultArgs() != null
                && !browserProperties.getIgnoreDefaultArgs().isEmpty()) {
                options.setIgnoreDefaultArgs(browserProperties.getIgnoreDefaultArgs());
            }
            if (scrapeProperties.getMasterLoginArgs() != null && !scrapeProperties.getMasterLoginArgs().isEmpty()) {
                List<String> args = new java.util.ArrayList<>();
                if (browserProperties.getLaunchArgs() != null && !browserProperties.getLaunchArgs().isEmpty()) {
                    args.addAll(browserProperties.getLaunchArgs());
                }
                args.addAll(scrapeProperties.getMasterLoginArgs());
                options.setArgs(args);
            } else if (browserProperties.getLaunchArgs() != null && !browserProperties.getLaunchArgs().isEmpty()) {
                options.setArgs(browserProperties.getLaunchArgs());
            }
            if (StringUtils.isNotBlank(browserProperties.getBrowserChannel())) {
                options.setChannel(browserProperties.getBrowserChannel());
            }
            if (StringUtils.isNotBlank(browserProperties.getExecutablePath())) {
                options.setExecutablePath(Paths.get(browserProperties.getExecutablePath()));
            }
            BrowserContext context = playwright.chromium().launchPersistentContext(userDataDir, options);
            BrowserStealthSupport.apply(context, browserProperties);
            openInitialPageIfNeeded(context);
            return new HeadedSession(playwright, context);
        } catch (Exception ex) {
            if (playwright != null) {
                try {
                    playwright.close();
                } catch (Exception closeEx) {
                    ex.addSuppressed(closeEx);
                }
            }
            log.warn("master login runtime open failed, profileId={}, error={}", profileId, ex.getMessage());
            throw new IllegalStateException("failed to open master login runtime: " + ex.getMessage(), ex);
        }
    }

    public Path resolveUserDataDir(String profileId) {
        try {
            String normalizedProfileId = profileIdValidator.normalizeProfileId(profileId);
            Path rootDir = Paths.get(scrapeProperties.getMasterUserDataRoot()).toAbsolutePath().normalize();
            Path userDataDir = rootDir.resolve(normalizedProfileId).normalize();
            if (!userDataDir.startsWith(rootDir)) {
                throw new IllegalArgumentException("invalid user data dir path");
            }
            Files.createDirectories(userDataDir);
            return userDataDir;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("failed to resolve user data dir: " + ex.getMessage(), ex);
        }
    }

    public record HeadedSession(Playwright playwright, BrowserContext context) implements AutoCloseable {

        @Override
        public void close() {
            try {
                context.close();
            } finally {
                playwright.close();
            }
        }

    }

    private void openInitialPageIfNeeded(BrowserContext context) {
        String initialPageUrl = scrapeProperties.getMasterLoginInitialPageUrl();
        if (StringUtils.isBlank(initialPageUrl)) {
            return;
        }
        Page page = resolveInitialPage(context);
        page.navigate(initialPageUrl,
            new Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                .setTimeout((double) scrapeProperties.getNavigateTimeoutMs())
        );
    }

    private Page resolveInitialPage(BrowserContext context) {
        long waitDeadline = System.currentTimeMillis() + 500;
        while (System.currentTimeMillis() < waitDeadline) {
            if (!context.pages().isEmpty()) {
                return context.pages().get(0);
            }
            sleep(50);
        }
        if (!context.pages().isEmpty()) {
            return context.pages().get(0);
        }
        return context.newPage();
    }

    private BrowserType.LaunchPersistentContextOptions buildContextOptions() {
        BrowserType.LaunchPersistentContextOptions options = new BrowserType.LaunchPersistentContextOptions()
            .setHeadless(false)
            .setViewportSize(null);

        String userAgent = resolveUserAgent();
        if (StringUtils.isNotBlank(userAgent)) {
            options.setUserAgent(userAgent);
        }
        if (StringUtils.isNotBlank(browserProperties.getLocale())) {
            options.setLocale(browserProperties.getLocale());
        }
        if (StringUtils.isNotBlank(browserProperties.getTimezoneId())) {
            options.setTimezoneId(browserProperties.getTimezoneId());
        }

        Map<String, String> headers = new HashMap<>();
        if (browserProperties.getExtraHeaders() != null) {
            browserProperties.getExtraHeaders().forEach((key, value) -> {
                if (StringUtils.isNotBlank(key) && StringUtils.isNotBlank(value)) {
                    headers.put(key, value);
                }
            });
        }
        if (StringUtils.isNotBlank(browserProperties.getAcceptLanguage())) {
            headers.putIfAbsent("Accept-Language", browserProperties.getAcceptLanguage());
        }
        if (!headers.isEmpty()) {
            options.setExtraHTTPHeaders(headers);
        }

        if (StringUtils.isNotBlank(browserProperties.getProxyServer())) {
            com.microsoft.playwright.options.Proxy proxy =
                new com.microsoft.playwright.options.Proxy(browserProperties.getProxyServer());
            if (StringUtils.isNotBlank(browserProperties.getProxyUsername())) {
                proxy.setUsername(browserProperties.getProxyUsername());
            }
            if (StringUtils.isNotBlank(browserProperties.getProxyPassword())) {
                proxy.setPassword(browserProperties.getProxyPassword());
            }
            options.setProxy(proxy);
        }

        return options;
    }

    private String resolveUserAgent() {
        if (StringUtils.isNotBlank(browserProperties.getUserAgent())) {
            return browserProperties.getUserAgent();
        }
        List<String> pool = browserProperties.getUserAgents();
        if (pool == null || pool.isEmpty()) {
            return "";
        }
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
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

    @FunctionalInterface
    interface PlaywrightFactory {

        Playwright create();

    }

}
