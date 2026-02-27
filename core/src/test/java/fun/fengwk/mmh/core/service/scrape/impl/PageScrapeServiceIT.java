package fun.fengwk.mmh.core.service.scrape.impl;

import fun.fengwk.mmh.core.CoreAutoConfiguration;
import fun.fengwk.mmh.core.service.scrape.PageScrapeService;
import fun.fengwk.mmh.core.service.scrape.model.ScrapeRequest;
import fun.fengwk.mmh.core.service.scrape.model.ScrapeResponse;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * @author fengwk
 */
@Slf4j
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = CoreAutoConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PageScrapeServiceIT {

    private static final Path TEMP_DIR = createTempDir();
    private static final String SIMPLE_HTML = """
        <!doctype html>
        <html>
        <head><title>simple</title></head>
        <body>
          <main id="main">
            <h1>Simple Title</h1>
            <p>Simple paragraph.</p>
            <a href="https://example.com/a">Link A</a>
          </main>
        </body>
        </html>
        """;
    private static final String LINKS_HTML = """
        <!doctype html>
        <html>
        <body>
          <main id="main">
            <a href="https://example.com/a">Link A</a>
            <a href="https://example.com/b">Link B</a>
          </main>
        </body>
        </html>
        """;
    private static final String DYNAMIC_HTML = """
        <!doctype html>
        <html>
        <body>
          <main id="main">
            <div id="static">static</div>
            <script>
              setTimeout(function () {
                var el = document.createElement('div');
                el.id = 'delayed';
                el.textContent = 'delayed';
                document.getElementById('main').appendChild(el);
              }, 50);
            </script>
          </main>
        </body>
        </html>
        """;
    private static final byte[] IMAGE_PNG_BYTES = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
    );

    @Autowired
    private PageScrapeService pageScrapeService;

    private HttpServer server;
    private String baseUrl;
    private ExecutorService executor;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.mcp.server.enabled", () -> "false");
        registry.add("spring.ai.mcp.server.annotation-scanner.enabled", () -> "false");
        registry.add("mmh.browser.default-profile-id", () -> "it");
        registry.add("mmh.scrape.navigate-timeout-ms", () -> "5000");
    }

    @BeforeAll
    public void setUp() throws Exception {
        assumeTrue(isPlaywrightAvailable(), "Playwright browser not installed");
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/simple", exchange -> respond(exchange, SIMPLE_HTML));
        server.createContext("/links", exchange -> respond(exchange, LINKS_HTML));
        server.createContext("/dynamic", exchange -> respond(exchange, DYNAMIC_HTML));
        server.createContext("/image", exchange -> respondBinary(exchange, "image/png", IMAGE_PNG_BYTES));
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterAll
    public void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    public void shouldScrapeHtmlContent() {
        ScrapeResponse response = pageScrapeService.scrape(ScrapeRequest.builder()
            .url(url("/simple"))
            .format("html")
            .build());

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContent()).contains("<main id=\"main\">");
        log.info("scrape html content:\n{}", response.getContent());
    }

    @Test
    public void shouldScrapeMainContentOnly() {
        ScrapeResponse response = pageScrapeService.scrape(ScrapeRequest.builder()
            .url(url("/simple"))
            .format("html")
            .onlyMainContent(true)
            .build());

        String content = response.getContent();
        assertThat(content).startsWith("<main");
        assertThat(content).doesNotContain("<html");
        log.info("scrape main content:\n{}", content);
    }

    @Test
    public void shouldScrapeLinks() {
        ScrapeResponse response = pageScrapeService.scrape(ScrapeRequest.builder()
            .url(url("/links"))
            .format("links")
            .build());

        assertThat(response.getLinks()).containsExactly(
            "https://example.com/a",
            "https://example.com/b"
        );
        log.info("scrape links: {}", response.getLinks());
    }

    @Test
    public void shouldScrapeMarkdown() {
        ScrapeResponse response = pageScrapeService.scrape(ScrapeRequest.builder()
            .url(url("/simple"))
            .format("markdown")
            .onlyMainContent(true)
            .build());

        assertThat(response.getContent()).contains("Simple Title");
        log.info("scrape markdown:\n{}", response.getContent());
    }

    @Test
    public void shouldWaitForDynamicContent() {
        ScrapeResponse response = pageScrapeService.scrape(ScrapeRequest.builder()
            .url(url("/dynamic"))
            .format("html")
            .onlyMainContent(true)
            .waitFor(200)
            .build());

        assertThat(response.getContent()).contains("delayed");
        log.info("scrape dynamic html:\n{}", response.getContent());
    }

    @Test
    public void shouldScrapeScreenshot() {
        ScrapeResponse response = pageScrapeService.scrape(ScrapeRequest.builder()
            .url(url("/simple"))
            .format("screenshot")
            .build());

        assertThat(response.getScreenshotMime()).isEqualTo("image/png");
        assertThat(response.getScreenshotBase64()).startsWith("data:image/png;base64,");
        log.info("scrape screenshot size={}", response.getScreenshotBase64().length());
    }

    @Test
    public void shouldReturnDirectMediaDataUriRegardlessOfFormat() {
        ScrapeResponse response = pageScrapeService.scrape(ScrapeRequest.builder()
            .url(url("/image"))
            .format("markdown")
            .build());

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getFormat()).isEqualTo("media");
        assertThat(response.getScreenshotMime()).isEqualTo("image/png");
        assertThat(response.getScreenshotBase64()).startsWith("data:image/png;base64,");
        log.info("scrape direct media size={}", response.getScreenshotBase64().length());
    }

    private String url(String path) {
        return baseUrl + path;
    }

    private void respond(HttpExchange exchange, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(payload);
        }
    }

    private void respondBinary(HttpExchange exchange, String contentType, byte[] payload) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(payload);
        }
    }

    private boolean isPlaywrightAvailable() {
        try (Playwright playwright = Playwright.create()) {
            try (Browser browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true)
            )) {
                return true;
            }
        } catch (Exception ex) {
            log.warn("Playwright is not available: {}", ex.getMessage());
            return false;
        }
    }

    private static Path createTempDir() {
        try {
            return Files.createTempDirectory("mmh-scrape-it-");
        } catch (IOException ex) {
            throw new IllegalStateException("failed to create temp dir", ex);
        }
    }

}
