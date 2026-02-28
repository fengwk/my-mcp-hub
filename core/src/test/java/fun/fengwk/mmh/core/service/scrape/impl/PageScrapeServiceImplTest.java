package fun.fengwk.mmh.core.service.scrape.impl;

import fun.fengwk.mmh.core.service.browser.runtime.BrowserTaskExecutor;
import fun.fengwk.mmh.core.service.browser.runtime.ProfileType;
import fun.fengwk.mmh.core.service.scrape.ScrapeProperties;
import fun.fengwk.mmh.core.service.scrape.model.ScrapeRequest;
import fun.fengwk.mmh.core.service.scrape.model.ScrapeResponse;
import fun.fengwk.mmh.core.service.scrape.parser.HtmlMainContentCleaner;
import fun.fengwk.mmh.core.service.scrape.parser.LinkExtractor;
import fun.fengwk.mmh.core.service.scrape.parser.MarkdownPostProcessor;
import fun.fengwk.mmh.core.service.scrape.parser.MarkdownRenderer;
import fun.fengwk.mmh.core.service.scrape.runtime.MasterProfileLockedException;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author fengwk
 */
@ExtendWith(MockitoExtension.class)
public class PageScrapeServiceImplTest {

    @Mock
    private BrowserTaskExecutor browserTaskExecutor;

    @Mock
    private HtmlMainContentCleaner htmlMainContentCleaner;

    @Mock
    private MarkdownRenderer markdownRenderer;

    @Mock
    private MarkdownPostProcessor markdownPostProcessor;

    @Mock
    private LinkExtractor linkExtractor;

    private PageScrapeServiceImpl pageScrapeService;

    @BeforeEach
    void setUp() {
        ScrapeProperties scrapeProperties = new ScrapeProperties();
        pageScrapeService = new PageScrapeServiceImpl(
            browserTaskExecutor,
            scrapeProperties,
            htmlMainContentCleaner,
            markdownRenderer,
            markdownPostProcessor,
            linkExtractor
        );
    }

    @Test
    public void shouldReturn400WhenUrlBlank() {
        ScrapeResponse response = pageScrapeService.scrape(
            ScrapeRequest.builder().url(" ").build()
        );

        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getError()).isEqualTo("url is blank");
        verify(browserTaskExecutor, never()).execute(any(ProfileType.class), any());
    }

    @Test
    public void shouldReturn400WhenProtocolUnsupported() {
        ScrapeResponse response = pageScrapeService.scrape(
            ScrapeRequest.builder().url("ftp://example.com").build()
        );

        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getError()).isEqualTo("unsupported url protocol");
    }

    @Test
    public void shouldReturn400WhenFormatUnsupported() {
        ScrapeResponse response = pageScrapeService.scrape(
            ScrapeRequest.builder().url("https://example.com").format("pdf").build()
        );

        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getError()).contains("unsupported format");
    }

    @Test
    public void shouldReturn400WhenProfileModeUnsupported() {
        ScrapeResponse response = pageScrapeService.scrape(
            ScrapeRequest.builder().url("https://example.com").profileMode("unknown").build()
        );

        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getError()).contains("unsupported profileMode");
        verify(browserTaskExecutor, never()).execute(any(ProfileType.class), any());
    }

    @Test
    public void shouldReturn400WhenWaitForOutOfRange() {
        ScrapeResponse response = pageScrapeService.scrape(
            ScrapeRequest.builder().url("https://example.com").waitFor(60001).build()
        );

        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getError()).isEqualTo("waitFor out of range");
    }

    @Test
    public void shouldUseQuickMediaPathForDefaultProfile() throws Exception {
        byte[] pdfBytes = "%PDF-1.4\nmmh\n".getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/sample.pdf", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/pdf");
            exchange.sendResponseHeaders(200, pdfBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(pdfBytes);
            }
        });
        server.start();

        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/sample.pdf";
        try {
            ScrapeResponse response = pageScrapeService.scrape(
                ScrapeRequest.builder().url(url).format("markdown").build()
            );

            assertThat(response.getStatusCode()).isEqualTo(200);
            assertThat(response.getFormat()).isEqualTo("media");
            assertThat(response.getScreenshotMime()).isEqualTo("application/pdf");
            assertThat(response.getScreenshotBase64()).isEqualTo(
                "data:application/pdf;base64," + Base64.getEncoder().encodeToString(pdfBytes)
            );
            verify(browserTaskExecutor, never()).execute(any(ProfileType.class), any());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void shouldFallbackToBrowserWhenQuickMediaPathDoesNotMatch() throws Exception {
        byte[] htmlBytes = "<html><body>not-media</body></html>".getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/sample.pdf", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, htmlBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(htmlBytes);
            }
        });
        server.start();

        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/sample.pdf";
        ScrapeResponse expected = ScrapeResponse.builder().statusCode(200).format("html").content("ok").build();
        when(browserTaskExecutor.execute(eq(ProfileType.DEFAULT), any())).thenReturn(expected);

        try {
            ScrapeResponse response = pageScrapeService.scrape(
                ScrapeRequest.builder().url(url).format("html").build()
            );

            assertThat(response).isEqualTo(expected);
            verify(browserTaskExecutor).execute(eq(ProfileType.DEFAULT), any());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void shouldUseDefaultProfileId() {
        ScrapeResponse expected = ScrapeResponse.builder().statusCode(200).format("html").content("ok").build();
        when(browserTaskExecutor.execute(eq(ProfileType.DEFAULT), any())).thenReturn(expected);

        ScrapeResponse response = pageScrapeService.scrape(
            ScrapeRequest.builder()
                .url("https://example.com")
                .format("html")
                .build()
        );

        assertThat(response).isEqualTo(expected);
        verify(browserTaskExecutor).execute(eq(ProfileType.DEFAULT), any());
    }

    @Test
    public void shouldTrimUrlBeforeExecute() {
        ScrapeResponse expected = ScrapeResponse.builder().statusCode(200).format("html").content("ok").build();
        when(browserTaskExecutor.execute(eq(ProfileType.DEFAULT), any())).thenReturn(expected);

        ScrapeResponse response = pageScrapeService.scrape(
            ScrapeRequest.builder()
                .url("  https://example.com  ")
                .format("html")
                .build()
        );

        assertThat(response).isEqualTo(expected);
        verify(browserTaskExecutor).execute(eq(ProfileType.DEFAULT), any());
    }

    @Test
    public void shouldUseMasterProfileWhenRequested() {
        ScrapeResponse expected = ScrapeResponse.builder().statusCode(200).format("html").content("ok").build();
        when(browserTaskExecutor.execute(eq(ProfileType.MASTER), any())).thenReturn(expected);

        ScrapeResponse response = pageScrapeService.scrape(
            ScrapeRequest.builder()
                .url("https://example.com")
                .format("html")
                .profileMode("master")
                .build()
        );

        assertThat(response).isEqualTo(expected);
        verify(browserTaskExecutor).execute(eq(ProfileType.MASTER), any());
    }

    @Test
    public void shouldReturn500WhenMasterProfileLocked() {
        when(browserTaskExecutor.execute(eq(ProfileType.MASTER), any()))
            .thenThrow(new MasterProfileLockedException());

        ScrapeResponse response = pageScrapeService.scrape(
            ScrapeRequest.builder()
                .url("https://example.com")
                .format("html")
                .profileMode("master")
                .build()
        );

        assertThat(response.getStatusCode()).isEqualTo(500);
        assertThat(response.getError()).contains("Master profile is in use");
    }

    @Test
    public void shouldReturn500WhenExecutorThrows() {
        when(browserTaskExecutor.execute(eq(ProfileType.DEFAULT), any()))
            .thenThrow(new RuntimeException("boom"));

        ScrapeResponse response = pageScrapeService.scrape(
            ScrapeRequest.builder()
                .url("https://example.com")
                .format("html")
                .build()
        );

        assertThat(response.getStatusCode()).isEqualTo(500);
        assertThat(response.getError()).contains("boom");
    }

}
