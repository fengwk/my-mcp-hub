package fun.fengwk.mmh.core.facade.search.searxng;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SearxngClient tests.
 *
 * @author fengwk
 */
class SearxngClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldSendGetRequestWithQueryParams() throws Exception {
        AtomicReference<String> methodRef = new AtomicReference<>();
        AtomicReference<URI> uriRef = new AtomicReference<>();

        server = startServer(exchange -> {
            methodRef.set(exchange.getRequestMethod());
            uriRef.set(exchange.getRequestURI());
            writeJson(exchange, 200, "{\"ok\":true}");
        });

        SearxngProperties props = new SearxngProperties();
        props.setBaseUrl(baseUrl(server));
        props.setTimeoutMs(1000);
        props.setMethod("GET");
        SearxngClient client = new SearxngClient(props);

        Map<String, String> params = new HashMap<>();
        params.put("q", "spring ai");
        params.put("pageno", "2");

        SearxngClientResponse response = client.search(params);

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).contains("ok");
        assertThat(methodRef.get()).isEqualTo("GET");
        Map<String, String> query = parseQuery(uriRef.get().getRawQuery());
        assertThat(query.get("q")).isEqualTo("spring ai");
        assertThat(query.get("pageno")).isEqualTo("2");
        assertThat(query.get("format")).isEqualTo("json");
    }

    @Test
    void shouldSendPostRequestWithFormBody() throws Exception {
        AtomicReference<String> methodRef = new AtomicReference<>();
        AtomicReference<String> bodyRef = new AtomicReference<>();

        server = startServer(exchange -> {
            methodRef.set(exchange.getRequestMethod());
            bodyRef.set(readBody(exchange));
            writeJson(exchange, 200, "{\"ok\":true}");
        });

        SearxngProperties props = new SearxngProperties();
        props.setBaseUrl(baseUrl(server));
        props.setTimeoutMs(1000);
        props.setMethod("POST");
        SearxngClient client = new SearxngClient(props);

        Map<String, String> params = new HashMap<>();
        params.put("q", "spring ai");

        SearxngClientResponse response = client.search(params);

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(methodRef.get()).isEqualTo("POST");
        Map<String, String> form = parseQuery(bodyRef.get());
        assertThat(form.get("q")).isEqualTo("spring ai");
        assertThat(form.get("format")).isEqualTo("json");
    }

    private static HttpServer startServer(HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/search", handler);
        server.start();
        return server;
    }

    private static String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void writeJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream input = exchange.getRequestBody()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return map;
        }
        for (String part : query.split("&")) {
            String[] kv = part.split("=", 2);
            String key = decode(kv[0]);
            String value = kv.length > 1 ? decode(kv[1]) : "";
            map.put(key, value);
        }
        return map;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

}
