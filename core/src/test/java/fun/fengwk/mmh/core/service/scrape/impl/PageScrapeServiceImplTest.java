package fun.fengwk.mmh.core.service.scrape.impl;

import fun.fengwk.mmh.core.service.browser.runtime.BrowserTaskExecutor;
import fun.fengwk.mmh.core.service.scrape.ScrapeProperties;
import fun.fengwk.mmh.core.service.scrape.model.ScrapeRequest;
import fun.fengwk.mmh.core.service.scrape.model.ScrapeResponse;
import fun.fengwk.mmh.core.service.scrape.parser.HtmlMainContentCleaner;
import fun.fengwk.mmh.core.service.scrape.parser.LinkExtractor;
import fun.fengwk.mmh.core.service.scrape.parser.MarkdownPostProcessor;
import fun.fengwk.mmh.core.service.scrape.parser.MarkdownRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        scrapeProperties.setDefaultProfileId("master");
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
        verify(browserTaskExecutor, never()).execute(any(), any());
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
    public void shouldReturn400WhenWaitForOutOfRange() {
        ScrapeResponse response = pageScrapeService.scrape(
            ScrapeRequest.builder().url("https://example.com").waitFor(60001).build()
        );

        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getError()).isEqualTo("waitFor out of range");
    }

    @Test
    public void shouldUseDefaultProfileId() {
        ScrapeResponse expected = ScrapeResponse.builder().statusCode(200).format("html").content("ok").build();
        when(browserTaskExecutor.execute(eq("master"), any())).thenReturn(expected);

        ScrapeResponse response = pageScrapeService.scrape(
            ScrapeRequest.builder()
                .url("https://example.com")
                .format("html")
                .build()
        );

        assertThat(response).isEqualTo(expected);
        verify(browserTaskExecutor).execute(eq("master"), any());
    }

    @Test
    public void shouldReturn500WhenExecutorThrows() {
        when(browserTaskExecutor.execute(eq("master"), any())).thenThrow(new RuntimeException("boom"));

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
