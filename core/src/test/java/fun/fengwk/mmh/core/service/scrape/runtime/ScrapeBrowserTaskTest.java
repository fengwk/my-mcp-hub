package fun.fengwk.mmh.core.service.scrape.runtime;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import fun.fengwk.mmh.core.service.browser.runtime.BrowserRuntimeContext;
import fun.fengwk.mmh.core.service.scrape.ScrapeProperties;
import fun.fengwk.mmh.core.service.scrape.model.ScrapeFormat;
import fun.fengwk.mmh.core.service.scrape.model.ScrapeRequest;
import fun.fengwk.mmh.core.service.scrape.model.ScrapeResponse;
import fun.fengwk.mmh.core.service.scrape.parser.HtmlMainContentCleaner;
import fun.fengwk.mmh.core.service.scrape.parser.LinkExtractor;
import fun.fengwk.mmh.core.service.scrape.parser.MarkdownPostProcessor;
import fun.fengwk.mmh.core.service.scrape.parser.MarkdownRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author fengwk
 */
@ExtendWith(MockitoExtension.class)
public class ScrapeBrowserTaskTest {

    @Mock
    private Page page;

    @Mock
    private HtmlMainContentCleaner htmlMainContentCleaner;

    @Mock
    private MarkdownRenderer markdownRenderer;

    @Mock
    private MarkdownPostProcessor markdownPostProcessor;

    @Mock
    private LinkExtractor linkExtractor;

    @Test
    public void shouldUseSmartWaitAndReturnHtml() {
        ScrapeRequest request = ScrapeRequest.builder()
            .url("https://example.com")
            .format("html")
            .onlyMainContent(true)
            .build();
        ScrapeProperties properties = new ScrapeProperties();
        BrowserRuntimeContext context = BrowserRuntimeContext.builder().page(page).build();

        when(page.content()).thenReturn("<html><body>raw</body></html>");
        when(htmlMainContentCleaner.clean("<html><body>raw</body></html>")).thenReturn("<main>cleaned</main>");

        ScrapeBrowserTask task = new ScrapeBrowserTask(
            request,
            ScrapeFormat.HTML,
            properties,
            htmlMainContentCleaner,
            markdownRenderer,
            markdownPostProcessor,
            linkExtractor
        );

        ScrapeResponse response = task.execute(context);

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContent()).isEqualTo("<main>cleaned</main>");
        verify(page).waitForLoadState(LoadState.NETWORKIDLE);
        verify(page, never()).waitForTimeout(anyDouble());
    }

    @Test
    public void shouldUseFixedWaitWhenWaitForProvided() {
        ScrapeRequest request = ScrapeRequest.builder()
            .url("https://example.com")
            .format("html")
            .waitFor(120)
            .build();
        ScrapeProperties properties = new ScrapeProperties();
        BrowserRuntimeContext context = BrowserRuntimeContext.builder().page(page).build();

        when(page.content()).thenReturn("<html><body>raw</body></html>");

        ScrapeBrowserTask task = new ScrapeBrowserTask(
            request,
            ScrapeFormat.HTML,
            properties,
            htmlMainContentCleaner,
            markdownRenderer,
            markdownPostProcessor,
            linkExtractor
        );

        task.execute(context);

        verify(page).waitForTimeout(120);
        verify(page, never()).waitForLoadState(LoadState.NETWORKIDLE);
    }

    @Test
    public void shouldFallbackMarkdownWhenMainContentResultEmpty() {
        ScrapeRequest request = ScrapeRequest.builder()
            .url("https://example.com")
            .format("markdown")
            .onlyMainContent(true)
            .build();
        ScrapeProperties properties = new ScrapeProperties();
        BrowserRuntimeContext context = BrowserRuntimeContext.builder().page(page).build();

        when(page.content()).thenReturn("<html><body>raw</body></html>");
        when(htmlMainContentCleaner.clean("<html><body>raw</body></html>")).thenReturn("<main></main>");
        when(markdownRenderer.render("<main></main>")).thenReturn("   ");
        when(markdownPostProcessor.process("   ")).thenReturn("");
        when(markdownRenderer.render("<html><body>raw</body></html>")).thenReturn("raw");
        when(markdownPostProcessor.process("raw")).thenReturn("raw");

        ScrapeBrowserTask task = new ScrapeBrowserTask(
            request,
            ScrapeFormat.MARKDOWN,
            properties,
            htmlMainContentCleaner,
            markdownRenderer,
            markdownPostProcessor,
            linkExtractor
        );

        ScrapeResponse response = task.execute(context);

        assertThat(response.getContent()).isEqualTo("raw");
    }

    @Test
    public void shouldReturnLinksWhenFormatLinks() {
        ScrapeRequest request = ScrapeRequest.builder()
            .url("https://example.com")
            .format("links")
            .onlyMainContent(false)
            .build();
        ScrapeProperties properties = new ScrapeProperties();
        BrowserRuntimeContext context = BrowserRuntimeContext.builder().page(page).build();

        when(page.content()).thenReturn("<html><body>raw</body></html>");
        when(linkExtractor.extract("<html><body>raw</body></html>", "https://example.com"))
            .thenReturn(List.of("https://a.com"));

        ScrapeBrowserTask task = new ScrapeBrowserTask(
            request,
            ScrapeFormat.LINKS,
            properties,
            htmlMainContentCleaner,
            markdownRenderer,
            markdownPostProcessor,
            linkExtractor
        );

        ScrapeResponse response = task.execute(context);

        assertThat(response.getLinks()).containsExactly("https://a.com");
        verify(htmlMainContentCleaner, never()).clean(any());
    }

}
