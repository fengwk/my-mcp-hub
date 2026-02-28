package fun.fengwk.mmh.core.service.scrape.support;

import fun.fengwk.convention4j.common.http.client.HttpClientFactory;
import fun.fengwk.convention4j.common.http.client.HttpClientUtils;
import fun.fengwk.convention4j.common.http.client.HttpSendResult;
import fun.fengwk.convention4j.common.http.client.ProxySelectorAdapter;
import fun.fengwk.convention4j.common.lang.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared HTTP helper for scrape runtime.
 *
 * @author fengwk
 */
@Slf4j
public final class ScrapeHttpUtils {

    private static final int DEFAULT_PROBE_TIMEOUT_MS = 3000;
    private static final int MIN_PROBE_TIMEOUT_MS = 500;
    private static final int MAX_RETRY_TIMEOUT_MS = 15000;

    private static final String DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final HttpClient SCRAPE_HTTP_CLIENT = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(15))
        .proxy(new ProxySelectorAdapter(HttpClientFactory.getDefaultConfigurableListableProxies()))
        .build();

    private ScrapeHttpUtils() {
    }

    public static int resolveProbeTimeoutMs(int configuredTimeoutMs) {
        int timeoutMs = configuredTimeoutMs > 0 ? configuredTimeoutMs : DEFAULT_PROBE_TIMEOUT_MS;
        return Math.max(MIN_PROBE_TIMEOUT_MS, timeoutMs);
    }

    public static Integer tryFetchStatusCodeWithRetry(String url, int configuredTimeoutMs) {
        int timeoutMs = resolveProbeTimeoutMs(configuredTimeoutMs);
        Integer statusCode = tryFetchStatusCode(url, timeoutMs);
        if (statusCode != null) {
            return statusCode;
        }

        int retryTimeoutMs = nextRetryTimeoutMs(timeoutMs);
        if (retryTimeoutMs <= timeoutMs) {
            return null;
        }
        return tryFetchStatusCode(url, retryTimeoutMs);
    }

    public static HttpBytesResponse tryFetchBytesWithRetry(String url, int configuredTimeoutMs) {
        int timeoutMs = resolveProbeTimeoutMs(configuredTimeoutMs);
        HttpBytesResponse response = tryFetchBytes(url, timeoutMs);
        if (response != null) {
            return response;
        }

        int retryTimeoutMs = nextRetryTimeoutMs(timeoutMs);
        if (retryTimeoutMs <= timeoutMs) {
            return null;
        }
        return tryFetchBytes(url, retryTimeoutMs);
    }

    private static Integer tryFetchStatusCode(String url, int timeoutMs) {
        HttpRequest request;
        try {
            request = buildGetRequest(url, timeoutMs);
        } catch (Exception ex) {
            log.debug("build status request failed, url={}, timeoutMs={}, error={}", url, timeoutMs, ex.getMessage());
            return null;
        }

        try (HttpSendResult result = HttpClientUtils.send(SCRAPE_HTTP_CLIENT, request)) {
            if (result == null || result.hasError()) {
                return null;
            }
            return result.getStatusCode();
        }
    }

    private static HttpBytesResponse tryFetchBytes(String url, int timeoutMs) {
        HttpRequest request;
        try {
            request = buildGetRequest(url, timeoutMs);
        } catch (Exception ex) {
            log.debug("build bytes request failed, url={}, timeoutMs={}, error={}", url, timeoutMs, ex.getMessage());
            return null;
        }

        try (HttpSendResult result = HttpClientUtils.send(SCRAPE_HTTP_CLIENT, request)) {
            if (result == null || result.hasError()) {
                return null;
            }

            return new HttpBytesResponse(
                result.getStatusCode(),
                toFirstValueHeaders(result.getHeaders()),
                result.tryParseBodyBytes()
            );
        }
    }

    private static HttpRequest buildGetRequest(String url, int timeoutMs) {
        if (StringUtils.isBlank(url)) {
            throw new IllegalArgumentException("url is blank");
        }

        return HttpRequest.newBuilder(URI.create(url))
            .GET()
            .header("Accept", "*/*")
            .header("User-Agent", DEFAULT_USER_AGENT)
            .timeout(Duration.ofMillis(timeoutMs))
            .build();
    }

    private static Map<String, String> toFirstValueHeaders(Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }

        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            List<String> values = entry.getValue();
            if (values == null || values.isEmpty()) {
                continue;
            }
            String key = entry.getKey();
            if (StringUtils.isBlank(key)) {
                continue;
            }
            normalized.put(key.toLowerCase(Locale.ROOT), values.get(0));
        }
        return normalized;
    }

    private static int nextRetryTimeoutMs(int timeoutMs) {
        if (timeoutMs >= MAX_RETRY_TIMEOUT_MS) {
            return timeoutMs;
        }
        return Math.min(MAX_RETRY_TIMEOUT_MS, timeoutMs * 2);
    }

    public record HttpBytesResponse(int statusCode, Map<String, String> headers, byte[] body) {
    }

}
