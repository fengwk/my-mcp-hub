package fun.fengwk.mmh.core.facade.search.searxng;

import fun.fengwk.convention4j.common.http.HttpUtils;
import fun.fengwk.convention4j.common.http.client.HttpClientUtils;
import fun.fengwk.convention4j.common.http.client.HttpSendResult;
import fun.fengwk.convention4j.common.lang.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * SearXNG HTTP client.
 *
 * @author fengwk
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearxngClient {

    private static final String SEARCH_PATH = "/search";

    private final SearxngProperties properties;

    public SearxngClientResponse search(Map<String, String> params) {
        // Build request parameters with default format.
        Map<String, String> form = new LinkedHashMap<>();
        if (params != null) {
            form.putAll(params);
        }
        form.putIfAbsent("format", "json");

        String method = properties.getMethod();
        boolean useGet = "GET".equalsIgnoreCase(method);
        String formBody = buildFormBody(form);

        URI uri = buildSearchUri(useGet ? formBody : null);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(uri)
            .timeout(Duration.ofMillis(properties.getTimeoutMs()))
            .header("Accept", "application/json");

        if (useGet) {
            builder.GET();
        } else {
            builder.header("Content-Type", "application/x-www-form-urlencoded")
                .POST(BodyPublishers.ofString(formBody));
        }

        HttpRequest request = builder.build();
        try (HttpSendResult result = HttpClientUtils.send(request)) {
            String body = null;
            Throwable error = result.getError();
            try {
                body = result.parseBodyString(StandardCharsets.UTF_8);
            } catch (IOException ex) {
                error = error == null ? ex : error;
            }

            return SearxngClientResponse.builder()
                .statusCode(result.getStatusCode())
                .headers(result.getHeaders())
                .body(body)
                .error(error)
                .build();
        }
    }

    private URI buildSearchUri(String queryString) {
        String baseUrl = StringUtils.isBlank(properties.getBaseUrl())
            ? ""
            : properties.getBaseUrl().trim();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        if (StringUtils.isBlank(queryString)) {
            return URI.create(baseUrl + SEARCH_PATH);
        }
        return URI.create(baseUrl + SEARCH_PATH + "?" + queryString);
    }

    private String buildFormBody(Map<String, String> form) {
        StringJoiner joiner = new StringJoiner("&");
        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String key = HttpUtils.encodeUrlComponent(entry.getKey(), StandardCharsets.UTF_8);
            String value = HttpUtils.encodeUrlComponent(entry.getValue(), StandardCharsets.UTF_8);
            joiner.add(key + "=" + value);
        }
        return joiner.toString();
    }

}
