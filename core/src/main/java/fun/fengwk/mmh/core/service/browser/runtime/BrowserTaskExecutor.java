package fun.fengwk.mmh.core.service.browser.runtime;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.Proxy;
import fun.fengwk.convention4j.common.lang.StringUtils;
import fun.fengwk.mmh.core.service.browser.BrowserProperties;
import fun.fengwk.mmh.core.service.browser.BrowserStealthSupport;
import fun.fengwk.mmh.core.service.browser.coordination.ProfileIdValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Executes generic browser tasks with isolated contexts.
 *
 * @author fengwk
 */
@Component
@RequiredArgsConstructor
public class BrowserTaskExecutor {

    private final BrowserProperties browserProperties;
    private final ProfileIdValidator profileIdValidator;
    private final BrowserWorkerManager browserWorkerManager;

    public <T> T execute(String profileId, BrowserTask<T> task) {
        String normalizedProfileId = profileIdValidator.normalizeProfileId(profileId);
        return browserWorkerManager.execute(browser ->
            // Each task gets a fresh isolated context with no state sharing.
            doExecute(browser, normalizedProfileId, task)
        );
    }

    private <T> T doExecute(
        com.microsoft.playwright.Browser browser,
        String profileId,
        BrowserTask<T> task
    ) throws Exception {
        try (BrowserContext context = browser.newContext(buildContextOptions())) {
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
    }

    private com.microsoft.playwright.Browser.NewContextOptions buildContextOptions() {
        com.microsoft.playwright.Browser.NewContextOptions options =
            new com.microsoft.playwright.Browser.NewContextOptions();

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

        String proxyServer = browserProperties.getProxyServer();
        if (StringUtils.isNotBlank(proxyServer)) {
            Proxy proxy = new Proxy(proxyServer);
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

}
