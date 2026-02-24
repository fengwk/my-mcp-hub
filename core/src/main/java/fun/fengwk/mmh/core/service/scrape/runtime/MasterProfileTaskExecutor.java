package fun.fengwk.mmh.core.service.scrape.runtime;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Proxy;
import fun.fengwk.convention4j.common.lang.StringUtils;
import fun.fengwk.mmh.core.service.browser.BrowserProperties;
import fun.fengwk.mmh.core.service.browser.BrowserStealthSupport;
import fun.fengwk.mmh.core.service.browser.coordination.LoginLockManager;
import fun.fengwk.mmh.core.service.browser.runtime.BrowserRuntimeContext;
import fun.fengwk.mmh.core.service.browser.runtime.BrowserTask;
import fun.fengwk.mmh.core.service.scrape.ScrapeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Executes tasks with master profile in a persistent context.
 *
 * @author fengwk
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MasterProfileTaskExecutor {

    private static final long LOCK_RETRY_INTERVAL_MS = 100;

    private final LoginLockManager loginLockManager;
    private final MasterLoginRuntime masterLoginRuntime;
    private final BrowserProperties browserProperties;
    private final ScrapeProperties scrapeProperties;

    public <T> T execute(String profileId, BrowserTask<T> task) {
        long lockTimeoutMs = Math.max(0L, scrapeProperties.getMasterProfileLockTimeoutMs());
        LoginLockManager.LoginLock lock = loginLockManager.tryAcquire(
            profileId,
            lockTimeoutMs,
            LOCK_RETRY_INTERVAL_MS
        );
        if (lock == null) {
            throw new MasterProfileLockedException("master profile locked");
        }
        try (lock) {
            return doExecute(profileId, task);
        }
    }

    private <T> T doExecute(String profileId, BrowserTask<T> task) {
        try (Playwright playwright = Playwright.create()) {
            Path userDataDir = masterLoginRuntime.resolveUserDataDir(profileId);
            BrowserType.LaunchPersistentContextOptions options = buildContextOptions();
            applyLaunchOptions(options);
            try (BrowserContext context = playwright.chromium().launchPersistentContext(userDataDir, options)) {
                BrowserStealthSupport.apply(context, browserProperties);
                Page page = context.newPage();
                BrowserRuntimeContext runtimeContext = BrowserRuntimeContext.builder()
                    .profileId(profileId)
                    .baseVersion(0L)
                    .browserContext(context)
                    .page(page)
                    .build();
                return task.execute(runtimeContext);
            }
        } catch (Exception ex) {
            log.warn("master profile task failed, profileId={}, error={}", profileId, ex.getMessage());
            throw new IllegalStateException("master profile task failed: " + ex.getMessage(), ex);
        }
    }

    private BrowserType.LaunchPersistentContextOptions buildContextOptions() {
        BrowserType.LaunchPersistentContextOptions options = new BrowserType.LaunchPersistentContextOptions()
            .setHeadless(true)
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
            Proxy proxy = new Proxy(browserProperties.getProxyServer());
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

    private void applyLaunchOptions(BrowserType.LaunchPersistentContextOptions options) {
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

}
