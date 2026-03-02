package fun.fengwk.mmh.core.service.browser.runtime;

import fun.fengwk.convention4j.common.lang.StringUtils;
import fun.fengwk.mmh.core.service.browser.BrowserProperties;

import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.options.Proxy;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Shared builder for Playwright persistent context options.
 *
 * @author fengwk
 */
final class BrowserContextOptionsSupport {

    private BrowserContextOptionsSupport() {
    }

    static BrowserType.LaunchPersistentContextOptions buildContextOptions(
        BrowserProperties.BrowserProfileProperties profileProperties,
        boolean headless,
        List<String> launchArgs,
        boolean viewportNull
    ) {
        BrowserProperties.BrowserProfileProperties profile = profileProperties == null
            ? new BrowserProperties.BrowserProfileProperties()
            : profileProperties;
        List<String> normalizedLaunchArgs = normalizeLaunchArgs(launchArgs);

        BrowserType.LaunchPersistentContextOptions options = new BrowserType.LaunchPersistentContextOptions()
            .setHeadless(headless);
        if (viewportNull) {
            options.setViewportSize(null);
        }

        if (profile.isIgnoreAllDefaultArgs()) {
            options.setIgnoreAllDefaultArgs(true);
        } else if (profile.getIgnoreDefaultArgs() != null && !profile.getIgnoreDefaultArgs().isEmpty()) {
            options.setIgnoreDefaultArgs(profile.getIgnoreDefaultArgs());
        }

        if (!normalizedLaunchArgs.isEmpty()) {
            options.setArgs(normalizedLaunchArgs);
        }

        if (StringUtils.isNotBlank(profile.getBrowserChannel())) {
            options.setChannel(profile.getBrowserChannel());
        }

        if (StringUtils.isNotBlank(profile.getExecutablePath())) {
            options.setExecutablePath(Paths.get(profile.getExecutablePath()));
        }

        String userAgent = resolveUserAgent(profile);
        if (StringUtils.isNotBlank(userAgent)) {
            options.setUserAgent(userAgent);
        }

        if (StringUtils.isNotBlank(profile.getLocale())) {
            options.setLocale(profile.getLocale());
        }
        if (StringUtils.isNotBlank(profile.getTimezoneId())) {
            options.setTimezoneId(profile.getTimezoneId());
        }

        Map<String, String> headers = new HashMap<>();
        if (profile.getExtraHeaders() != null) {
            profile.getExtraHeaders().forEach((key, value) -> {
                if (StringUtils.isNotBlank(key) && StringUtils.isNotBlank(value)) {
                    headers.put(key, value);
                }
            });
        }
        if (StringUtils.isNotBlank(profile.getAcceptLanguage())) {
            headers.putIfAbsent("Accept-Language", profile.getAcceptLanguage());
        }
        if (!headers.isEmpty()) {
            options.setExtraHTTPHeaders(headers);
        }

        if (StringUtils.isNotBlank(profile.getProxyServer())) {
            Proxy proxy = new Proxy(profile.getProxyServer());
            if (StringUtils.isNotBlank(profile.getProxyUsername())) {
                proxy.setUsername(profile.getProxyUsername());
            }
            if (StringUtils.isNotBlank(profile.getProxyPassword())) {
                proxy.setPassword(profile.getProxyPassword());
            }
            options.setProxy(proxy);
        }

        return options;
    }

    static List<String> normalizeLaunchArgs(List<String> launchArgs) {
        if (launchArgs == null || launchArgs.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String arg : launchArgs) {
            if (StringUtils.isBlank(arg)) {
                continue;
            }
            normalized.add(arg.trim());
        }
        return normalized;
    }

    private static String resolveUserAgent(BrowserProperties.BrowserProfileProperties profile) {
        if (StringUtils.isNotBlank(profile.getUserAgent())) {
            return profile.getUserAgent();
        }
        List<String> userAgents = profile.getUserAgents();
        if (userAgents == null || userAgents.isEmpty()) {
            return "";
        }
        return userAgents.get(ThreadLocalRandom.current().nextInt(userAgents.size()));
    }

}
