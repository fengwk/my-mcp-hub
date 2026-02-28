package fun.fengwk.mmh.core.service.scrape.runtime;

import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.sun.net.httpserver.HttpServer;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.RequestOptions;
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

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    @Mock
    private APIRequestContext apiRequestContext;

    @Mock
    private APIResponse apiResponse;

    @Mock
    private Response navigateResponse;

    @Mock
    private Frame mainFrame;

    @Mock
    private Frame childFrame;

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
        when(page.request()).thenReturn(apiRequestContext);
        when(apiRequestContext.get(eq("https://example.com"), any(RequestOptions.class))).thenReturn(apiResponse);
        when(apiResponse.ok()).thenReturn(false);
        when(htmlMainContentCleaner.clean(
            "<html><body>raw</body></html>",
            "https://example.com",
            true,
            properties.isStripChromeTags(),
            properties.isRemoveBase64Images()
        ))
            .thenReturn("<main>cleaned</main>");

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
        verify(page, atLeastOnce()).waitForLoadState(eq(LoadState.NETWORKIDLE), any(Page.WaitForLoadStateOptions.class));
        verify(page, atLeastOnce()).waitForTimeout(anyDouble());
        verify(apiResponse).dispose();
    }

    @Test
    public void shouldTreatLengthChangeWithinThresholdAsStable() {
        ScrapeRequest request = ScrapeRequest.builder()
            .url("https://example.com")
            .format("html")
            .build();
        ScrapeProperties properties = new ScrapeProperties();
        properties.setStabilityCheckIntervalMs(100);
        properties.setStabilityThreshold(3);
        properties.setStabilityLengthChangeThreshold(0.1D);
        BrowserRuntimeContext context = BrowserRuntimeContext.builder().page(page).build();

        String text100 = "A".repeat(100);
        String text108 = "A".repeat(108);
        String text110 = "A".repeat(110);
        String text112 = "A".repeat(112);
        when(page.content()).thenReturn(
            "<html><body>" + text100 + "</body></html>",
            "<html><body>" + text108 + "</body></html>",
            "<html><body>" + text110 + "</body></html>",
            "<html><body>" + text112 + "</body></html>",
            "<html><body>" + text112 + "</body></html>"
        );
        when(page.request()).thenReturn(apiRequestContext);
        when(apiRequestContext.get(eq("https://example.com"), any(RequestOptions.class))).thenReturn(apiResponse);
        when(apiResponse.ok()).thenReturn(false);
        when(htmlMainContentCleaner.clean(
            anyString(),
            eq("https://example.com"),
            eq(false),
            eq(properties.isStripChromeTags()),
            eq(properties.isRemoveBase64Images())
        ))
            .thenReturn("<html><body>stable</body></html>");

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
        verify(page, atLeastOnce()).waitForLoadState(eq(LoadState.NETWORKIDLE), any(Page.WaitForLoadStateOptions.class));
        verify(page, times(3)).waitForTimeout(anyDouble());
        verify(apiResponse).dispose();
    }

    @Test
    public void shouldSkipPostNavigateWaitWhenStatusIsError() {
        ScrapeRequest request = ScrapeRequest.builder()
            .url("https://example.com/not-found")
            .format("html")
            .build();
        ScrapeProperties properties = new ScrapeProperties();
        BrowserRuntimeContext context = BrowserRuntimeContext.builder().page(page).build();

        when(page.request()).thenReturn(apiRequestContext);
        when(apiRequestContext.get(eq("https://example.com/not-found"), any(RequestOptions.class))).thenReturn(apiResponse);
        when(apiResponse.ok()).thenReturn(false);
        when(page.navigate(eq("https://example.com/not-found"), any(Page.NavigateOptions.class))).thenReturn(navigateResponse);
        when(navigateResponse.status()).thenReturn(404);
        when(page.content()).thenReturn("<html><body>Not Found</body></html>");
        when(htmlMainContentCleaner.clean(
            "<html><body>Not Found</body></html>",
            "https://example.com/not-found",
            false,
            properties.isStripChromeTags(),
            properties.isRemoveBase64Images()
        ))
            .thenReturn("<html><body>Not Found</body></html>");

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
        assertThat(response.getContent()).contains("Not Found");
        verify(page, never()).waitForLoadState(eq(LoadState.NETWORKIDLE), any(Page.WaitForLoadStateOptions.class));
        verify(page, never()).waitForTimeout(anyDouble());
        verify(apiResponse).dispose();
    }

    @Test
    public void shouldTreatEmptyContentAsStable() {
        ScrapeRequest request = ScrapeRequest.builder()
            .url("https://example.com/empty")
            .format("html")
            .build();
        ScrapeProperties properties = new ScrapeProperties();
        properties.setStabilityCheckIntervalMs(100);
        properties.setStabilityThreshold(3);
        properties.setStabilityLengthChangeThreshold(0.1D);
        BrowserRuntimeContext context = BrowserRuntimeContext.builder().page(page).build();

        when(page.content()).thenReturn(
            "<html><body></body></html>",
            "<html><body></body></html>",
            "<html><body></body></html>",
            "<html><body></body></html>",
            "<html><body></body></html>"
        );
        when(page.request()).thenReturn(apiRequestContext);
        when(apiRequestContext.get(eq("https://example.com/empty"), any(RequestOptions.class))).thenReturn(apiResponse);
        when(apiResponse.ok()).thenReturn(false);
        when(htmlMainContentCleaner.clean(
            anyString(),
            eq("https://example.com/empty"),
            eq(false),
            eq(properties.isStripChromeTags()),
            eq(properties.isRemoveBase64Images())
        ))
            .thenReturn("");

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
        assertThat(response.getContent()).isEqualTo("");
        verify(page, times(3)).waitForTimeout(anyDouble());
        verify(apiResponse).dispose();
    }

    @Test
    public void shouldSkipUnstableChangeAndKeepComparingWithAnchor() {
        ScrapeRequest request = ScrapeRequest.builder()
            .url("https://example.com")
            .format("html")
            .build();
        ScrapeProperties properties = new ScrapeProperties();
        properties.setStabilityCheckIntervalMs(100);
        properties.setStabilityThreshold(3);
        properties.setStabilityLengthChangeThreshold(0.1D);
        BrowserRuntimeContext context = BrowserRuntimeContext.builder().page(page).build();

        when(page.content()).thenReturn(
            "<html><body>" + "A".repeat(100) + "</body></html>",
            "<html><body>" + "A".repeat(140) + "</body></html>",
            "<html><body>" + "A".repeat(110) + "</body></html>",
            "<html><body>" + "A".repeat(112) + "</body></html>",
            "<html><body>" + "A".repeat(113) + "</body></html>",
            "<html><body>" + "A".repeat(113) + "</body></html>"
        );
        when(page.request()).thenReturn(apiRequestContext);
        when(apiRequestContext.get(eq("https://example.com"), any(RequestOptions.class))).thenReturn(apiResponse);
        when(apiResponse.ok()).thenReturn(false);
        when(htmlMainContentCleaner.clean(
            anyString(),
            eq("https://example.com"),
            eq(false),
            eq(properties.isStripChromeTags()),
            eq(properties.isRemoveBase64Images())
        ))
            .thenReturn("<html><body>stable</body></html>");

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
        verify(page, times(4)).waitForTimeout(anyDouble());
        verify(apiResponse).dispose();
    }

    @Test
    public void shouldKeepWaitingWhenLengthChangeExceedsThreshold() {
        ScrapeRequest request = ScrapeRequest.builder()
            .url("https://example.com")
            .format("html")
            .build();
        ScrapeProperties properties = new ScrapeProperties();
        properties.setStabilityMaxWaitMs(350);
        properties.setStabilityCheckIntervalMs(100);
        properties.setStabilityThreshold(3);
        properties.setStabilityLengthChangeThreshold(0.1D);
        BrowserRuntimeContext context = BrowserRuntimeContext.builder().page(page).build();

        when(page.content()).thenReturn(
            "<html><body>" + "A".repeat(100) + "</body></html>",
            "<html><body>" + "A".repeat(130) + "</body></html>",
            "<html><body>" + "A".repeat(160) + "</body></html>",
            "<html><body>" + "A".repeat(190) + "</body></html>",
            "<html><body>" + "A".repeat(220) + "</body></html>"
        );
        when(page.request()).thenReturn(apiRequestContext);
        when(apiRequestContext.get(eq("https://example.com"), any(RequestOptions.class))).thenReturn(apiResponse);
        when(apiResponse.ok()).thenReturn(false);
        when(htmlMainContentCleaner.clean(
            anyString(),
            eq("https://example.com"),
            eq(false),
            eq(properties.isStripChromeTags()),
            eq(properties.isRemoveBase64Images())
        ))
            .thenReturn("<html><body>raw</body></html>");
        doAnswer(invocation -> {
            long sleepMillis = ((Double) invocation.getArgument(0)).longValue();
            Thread.sleep(sleepMillis);
            return null;
        }).when(page).waitForTimeout(anyDouble());

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
        verify(page, atLeastOnce()).waitForTimeout(anyDouble());
        verify(apiResponse).dispose();
    }

    @Test
    public void shouldCapSmartWaitDurationToAvoidLongBlocking() {
        ScrapeRequest request = ScrapeRequest.builder()
            .url("https://example.com")
            .format("html")
            .build();
        ScrapeProperties properties = new ScrapeProperties();
        properties.setNavigateTimeoutMs(5000);
        properties.setStabilityCheckIntervalMs(100);
        properties.setStabilityMaxWaitMs(300);
        properties.setDirectMediaProbeTimeoutMs(100);
        BrowserRuntimeContext context = BrowserRuntimeContext.builder().page(page).build();

        AtomicInteger counter = new AtomicInteger(0);
        when(page.content()).thenAnswer(invocation -> "<html><body>dynamic-" + counter.incrementAndGet() + "</body></html>");
        when(page.request()).thenReturn(apiRequestContext);
        when(apiRequestContext.get(eq("https://example.com"), any(RequestOptions.class))).thenReturn(apiResponse);
        when(apiResponse.ok()).thenReturn(false);
        when(htmlMainContentCleaner.clean(
            anyString(),
            eq("https://example.com"),
            eq(false),
            eq(properties.isStripChromeTags()),
            eq(properties.isRemoveBase64Images())
        ))
            .thenReturn("<html><body>raw</body></html>");
        doAnswer(invocation -> {
            long sleepMillis = ((Double) invocation.getArgument(0)).longValue();
            Thread.sleep(sleepMillis);
            return null;
        }).when(page).waitForTimeout(anyDouble());

        ScrapeBrowserTask task = new ScrapeBrowserTask(
            request,
            ScrapeFormat.HTML,
            properties,
            htmlMainContentCleaner,
            markdownRenderer,
            markdownPostProcessor,
            linkExtractor
        );

        long startAt = System.currentTimeMillis();
        ScrapeResponse response = task.execute(context);
        long elapsedMs = System.currentTimeMillis() - startAt;

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(elapsedMs).isLessThan(2500L);
        verify(page, atLeastOnce()).waitForTimeout(anyDouble());
        verify(apiResponse).dispose();
    }

    @Test
    public void shouldSupportPercentageStyleLengthThresholdConfiguration() {
        ScrapeRequest request = ScrapeRequest.builder()
            .url("https://example.com")
            .format("html")
            .build();
        ScrapeProperties properties = new ScrapeProperties();
        properties.setStabilityCheckIntervalMs(100);
        properties.setStabilityMaxWaitMs(900);
        properties.setStabilityThreshold(3);
        properties.setStabilityLengthChangeThreshold(10D);
        BrowserRuntimeContext context = BrowserRuntimeContext.builder().page(page).build();

        when(page.content()).thenReturn(
            "<html><body>" + "A".repeat(100) + "</body></html>",
            "<html><body>" + "A".repeat(108) + "</body></html>",
            "<html><body>" + "A".repeat(110) + "</body></html>",
            "<html><body>" + "A".repeat(111) + "</body></html>",
            "<html><body>" + "A".repeat(111) + "</body></html>"
        );
        when(page.request()).thenReturn(apiRequestContext);
        when(apiRequestContext.get(eq("https://example.com"), any(RequestOptions.class))).thenReturn(apiResponse);
        when(apiResponse.ok()).thenReturn(false);
        when(htmlMainContentCleaner.clean(
            anyString(),
            eq("https://example.com"),
            eq(false),
            eq(properties.isStripChromeTags()),
            eq(properties.isRemoveBase64Images())
        ))
            .thenReturn("<html><body>raw</body></html>");

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
        verify(page, times(3)).waitForTimeout(anyDouble());
        verify(apiResponse).dispose();
    }

    @Test
    public void shouldFallbackToNetworkIdleWhenSmartWaitDisabled() {
        ScrapeRequest request = ScrapeRequest.builder()
            .url("https://example.com")
            .format("html")
            .build();
        ScrapeProperties properties = new ScrapeProperties();
        properties.setSmartWaitEnabled(false);
        BrowserRuntimeContext context = BrowserRuntimeContext.builder().page(page).build();

        when(page.content()).thenReturn("<html><body>raw</body></html>");
        when(page.request()).thenReturn(apiRequestContext);
        when(apiRequestContext.get(eq("https://example.com"), any(RequestOptions.class))).thenReturn(apiResponse);
        when(apiResponse.ok()).thenReturn(false);
        when(htmlMainContentCleaner.clean(
            "<html><body>raw</body></html>",
            "https://example.com",
            false,
            properties.isStripChromeTags(),
            properties.isRemoveBase64Images()
        ))
            .thenReturn("<html><body>raw</body></html>");

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

        verify(page).waitForLoadState(eq(LoadState.NETWORKIDLE), any(Page.WaitForLoadStateOptions.class));
        verify(page, never()).waitForTimeout(anyDouble());
        verify(apiResponse).dispose();
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
        when(page.request()).thenReturn(apiRequestContext);
        when(apiRequestContext.get(eq("https://example.com"), any(RequestOptions.class))).thenReturn(apiResponse);
        when(apiResponse.ok()).thenReturn(false);
        when(htmlMainContentCleaner.clean(
            "<html><body>raw</body></html>",
            "https://example.com",
            false,
            properties.isStripChromeTags(),
            properties.isRemoveBase64Images()
        ))
            .thenReturn("<html><body>raw</body></html>");

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
        verify(apiResponse).dispose();
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
        when(page.request()).thenReturn(apiRequestContext);
        when(apiRequestContext.get(eq("https://example.com"), any(RequestOptions.class))).thenReturn(apiResponse);
        when(apiResponse.ok()).thenReturn(false);
        when(htmlMainContentCleaner.clean(
            "<html><body>raw</body></html>",
            "https://example.com",
            true,
            properties.isStripChromeTags(),
            properties.isRemoveBase64Images()
        ))
            .thenReturn("<main></main>");
        when(htmlMainContentCleaner.clean(
            "<html><body>raw</body></html>",
            "https://example.com",
            false,
            properties.isStripChromeTags(),
            properties.isRemoveBase64Images()
        ))
            .thenReturn("<html><body>raw</body></html>");
        when(markdownRenderer.render("<main></main>", "https://example.com")).thenReturn("   ");
        when(markdownPostProcessor.process("   ")).thenReturn("");
        when(markdownRenderer.render("<html><body>raw</body></html>", "https://example.com")).thenReturn("raw");
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
        verify(apiResponse).dispose();
    }

    @Test
    public void shouldMergeFrameMarkdownWhenFrameContainsDocument() {
        ScrapeRequest request = ScrapeRequest.builder()
            .url("https://example.com")
            .format("markdown")
            .onlyMainContent(false)
            .build();
        ScrapeProperties properties = new ScrapeProperties();
        BrowserRuntimeContext context = BrowserRuntimeContext.builder().page(page).build();

        String mainHtml = "<html><body><h1>Main</h1></body></html>";
        String frameHtml = "<html><body><article>Doc Body</article></body></html>";
        String frameUrl = "https://office.netease.com/app/open?pageId=1";

        when(page.content()).thenReturn(mainHtml);
        when(page.request()).thenReturn(apiRequestContext);
        when(apiRequestContext.get(eq("https://example.com"), any(RequestOptions.class))).thenReturn(apiResponse);
        when(apiResponse.ok()).thenReturn(false);
        when(page.mainFrame()).thenReturn(mainFrame);
        when(page.frames()).thenReturn(List.of(mainFrame, childFrame));
        when(childFrame.isDetached()).thenReturn(false);
        when(childFrame.parentFrame()).thenReturn(mainFrame);
        when(childFrame.url()).thenReturn(frameUrl);
        when(childFrame.content()).thenReturn(frameHtml);

        when(htmlMainContentCleaner.clean(
            mainHtml,
            "https://example.com",
            false,
            properties.isStripChromeTags(),
            properties.isRemoveBase64Images()
        )).thenReturn("<h1>Main</h1>");
        when(htmlMainContentCleaner.clean(
            frameHtml,
            frameUrl,
            false,
            properties.isStripChromeTags(),
            properties.isRemoveBase64Images()
        )).thenReturn("<article>Doc Body</article>");

        when(markdownRenderer.render("<h1>Main</h1>", "https://example.com")).thenReturn("Main");
        when(markdownPostProcessor.process("Main")).thenReturn("Main");
        when(markdownRenderer.render("<article>Doc Body</article>", frameUrl)).thenReturn("Doc Body");
        when(markdownPostProcessor.process("Doc Body")).thenReturn("Doc Body");

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

        assertThat(response.getContent()).contains("Main");
        assertThat(response.getContent()).contains("## Embedded Frame Contents");
        assertThat(response.getContent()).contains("### Frame 1: " + frameUrl);
        assertThat(response.getContent()).contains("Doc Body");
        verify(apiResponse).dispose();
    }

    @Test
    public void shouldBuildHierarchicalMarkdownForNestedFrames() {
        Frame nestedFrame = mock(Frame.class);
        Frame siblingFrame = mock(Frame.class);

        ScrapeRequest request = ScrapeRequest.builder()
            .url("https://example.com")
            .format("markdown")
            .onlyMainContent(false)
            .build();
        ScrapeProperties properties = new ScrapeProperties();
        BrowserRuntimeContext context = BrowserRuntimeContext.builder().page(page).build();

        String mainHtml = "<html><body><h1>Main</h1></body></html>";
        String parentFrameHtml = "<html><body><article>Parent Body</article></body></html>";
        String nestedFrameHtml = "<html><body><article>Child Body</article></body></html>";
        String siblingFrameHtml = "<html><body><article>Sibling Body</article></body></html>";

        String parentFrameUrl = "https://office.netease.com/app/open?pageId=parent";
        String nestedFrameUrl = "https://office.netease.com/app/open?pageId=child";
        String siblingFrameUrl = "https://office.netease.com/app/open?pageId=sibling";

        when(page.content()).thenReturn(mainHtml);
        when(page.request()).thenReturn(apiRequestContext);
        when(apiRequestContext.get(eq("https://example.com"), any(RequestOptions.class))).thenReturn(apiResponse);
        when(apiResponse.ok()).thenReturn(false);

        when(page.mainFrame()).thenReturn(mainFrame);
        when(page.frames()).thenReturn(List.of(mainFrame, childFrame, nestedFrame, siblingFrame));

        when(childFrame.isDetached()).thenReturn(false);
        when(childFrame.parentFrame()).thenReturn(mainFrame);
        when(childFrame.url()).thenReturn(parentFrameUrl);
        when(childFrame.content()).thenReturn(parentFrameHtml);

        when(nestedFrame.isDetached()).thenReturn(false);
        when(nestedFrame.parentFrame()).thenReturn(childFrame);
        when(nestedFrame.url()).thenReturn(nestedFrameUrl);
        when(nestedFrame.content()).thenReturn(nestedFrameHtml);

        when(siblingFrame.isDetached()).thenReturn(false);
        when(siblingFrame.parentFrame()).thenReturn(mainFrame);
        when(siblingFrame.url()).thenReturn(siblingFrameUrl);
        when(siblingFrame.content()).thenReturn(siblingFrameHtml);

        when(htmlMainContentCleaner.clean(
            mainHtml,
            "https://example.com",
            false,
            properties.isStripChromeTags(),
            properties.isRemoveBase64Images()
        )).thenReturn("<h1>Main</h1>");
        when(htmlMainContentCleaner.clean(
            parentFrameHtml,
            parentFrameUrl,
            false,
            properties.isStripChromeTags(),
            properties.isRemoveBase64Images()
        )).thenReturn("<article>Parent Body</article>");
        when(htmlMainContentCleaner.clean(
            nestedFrameHtml,
            nestedFrameUrl,
            false,
            properties.isStripChromeTags(),
            properties.isRemoveBase64Images()
        )).thenReturn("<article>Child Body</article>");
        when(htmlMainContentCleaner.clean(
            siblingFrameHtml,
            siblingFrameUrl,
            false,
            properties.isStripChromeTags(),
            properties.isRemoveBase64Images()
        )).thenReturn("<article>Sibling Body</article>");

        when(markdownRenderer.render("<h1>Main</h1>", "https://example.com")).thenReturn("Main");
        when(markdownPostProcessor.process("Main")).thenReturn("Main");

        when(markdownRenderer.render("<article>Parent Body</article>", parentFrameUrl)).thenReturn("Parent Body");
        when(markdownPostProcessor.process("Parent Body")).thenReturn("Parent Body");

        when(markdownRenderer.render("<article>Child Body</article>", nestedFrameUrl)).thenReturn("Child Body");
        when(markdownPostProcessor.process("Child Body")).thenReturn("Child Body");

        when(markdownRenderer.render("<article>Sibling Body</article>", siblingFrameUrl)).thenReturn("Sibling Body");
        when(markdownPostProcessor.process("Sibling Body")).thenReturn("Sibling Body");

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

        String markdown = response.getContent();
        assertThat(markdown).contains("## Embedded Frame Contents");
        assertThat(markdown).contains("### Frame 1: " + parentFrameUrl);
        assertThat(markdown).contains("#### Frame 1.1: " + nestedFrameUrl);
        assertThat(markdown).contains("### Frame 2: " + siblingFrameUrl);

        int parentIndex = markdown.indexOf("### Frame 1: " + parentFrameUrl);
        int nestedIndex = markdown.indexOf("#### Frame 1.1: " + nestedFrameUrl);
        int siblingIndex = markdown.indexOf("### Frame 2: " + siblingFrameUrl);
        assertThat(parentIndex).isGreaterThanOrEqualTo(0);
        assertThat(nestedIndex).isGreaterThan(parentIndex);
        assertThat(siblingIndex).isGreaterThan(nestedIndex);
        verify(apiResponse).dispose();
    }

    @Test
    public void shouldFilterUiLikeFrameWhenOnlyMainContentTrue() {
        ScrapeRequest request = ScrapeRequest.builder()
            .url("https://example.com")
            .format("markdown")
            .onlyMainContent(true)
            .build();
        ScrapeProperties properties = new ScrapeProperties();
        BrowserRuntimeContext context = BrowserRuntimeContext.builder().page(page).build();

        String mainHtml = "<html><body><h1>Main</h1></body></html>";
        String frameHtml = "<html><body><div>toolbar</div></body></html>";
        String frameUrl = "https://example.com/frame";
        String frameMarkdown = "插入\n正文\n16\n退出\n流程图";

        when(page.content()).thenReturn(mainHtml);
        when(page.request()).thenReturn(apiRequestContext);
        when(apiRequestContext.get(eq("https://example.com"), any(RequestOptions.class))).thenReturn(apiResponse);
        when(apiResponse.ok()).thenReturn(false);
        when(page.mainFrame()).thenReturn(mainFrame);
        when(page.frames()).thenReturn(List.of(mainFrame, childFrame));
        when(childFrame.isDetached()).thenReturn(false);
        when(childFrame.parentFrame()).thenReturn(mainFrame);
        when(childFrame.url()).thenReturn(frameUrl);
        when(childFrame.content()).thenReturn(frameHtml);

        when(htmlMainContentCleaner.clean(
            mainHtml,
            "https://example.com",
            true,
            properties.isStripChromeTags(),
            properties.isRemoveBase64Images()
        )).thenReturn("<h1>Main</h1>");
        when(htmlMainContentCleaner.clean(
            mainHtml,
            "https://example.com",
            false,
            properties.isStripChromeTags(),
            properties.isRemoveBase64Images()
        )).thenReturn("<h1>Main</h1>");
        when(htmlMainContentCleaner.clean(
            frameHtml,
            frameUrl,
            true,
            properties.isStripChromeTags(),
            properties.isRemoveBase64Images()
        )).thenReturn("<div>toolbar</div>");
        when(htmlMainContentCleaner.clean(
            frameHtml,
            frameUrl,
            false,
            properties.isStripChromeTags(),
            properties.isRemoveBase64Images()
        )).thenReturn("<div>toolbar</div>");

        when(markdownRenderer.render("<h1>Main</h1>", "https://example.com")).thenReturn("Main");
        when(markdownPostProcessor.process("Main")).thenReturn("Main");
        when(markdownRenderer.render("<div>toolbar</div>", frameUrl)).thenReturn(frameMarkdown);
        when(markdownPostProcessor.process(frameMarkdown)).thenReturn(frameMarkdown);

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

        assertThat(response.getContent()).isEqualTo("Main");
        assertThat(response.getContent()).doesNotContain("Embedded Frame Contents");
        verify(apiResponse).dispose();
    }

    @Test
    public void shouldKeepUiLikeFrameWhenOnlyMainContentFalse() {
        ScrapeRequest request = ScrapeRequest.builder()
            .url("https://example.com")
            .format("markdown")
            .onlyMainContent(false)
            .build();
        ScrapeProperties properties = new ScrapeProperties();
        BrowserRuntimeContext context = BrowserRuntimeContext.builder().page(page).build();

        String mainHtml = "<html><body><h1>Main</h1></body></html>";
        String frameHtml = "<html><body><div>toolbar</div></body></html>";
        String frameUrl = "https://example.com/frame";
        String frameMarkdown = "插入\n正文\n16\n退出\n流程图";

        when(page.content()).thenReturn(mainHtml);
        when(page.request()).thenReturn(apiRequestContext);
        when(apiRequestContext.get(eq("https://example.com"), any(RequestOptions.class))).thenReturn(apiResponse);
        when(apiResponse.ok()).thenReturn(false);
        when(page.mainFrame()).thenReturn(mainFrame);
        when(page.frames()).thenReturn(List.of(mainFrame, childFrame));
        when(childFrame.isDetached()).thenReturn(false);
        when(childFrame.parentFrame()).thenReturn(mainFrame);
        when(childFrame.url()).thenReturn(frameUrl);
        when(childFrame.content()).thenReturn(frameHtml);

        when(htmlMainContentCleaner.clean(
            mainHtml,
            "https://example.com",
            false,
            properties.isStripChromeTags(),
            properties.isRemoveBase64Images()
        )).thenReturn("<h1>Main</h1>");
        when(htmlMainContentCleaner.clean(
            frameHtml,
            frameUrl,
            false,
            properties.isStripChromeTags(),
            properties.isRemoveBase64Images()
        )).thenReturn("<div>toolbar</div>");

        when(markdownRenderer.render("<h1>Main</h1>", "https://example.com")).thenReturn("Main");
        when(markdownPostProcessor.process("Main")).thenReturn("Main");
        when(markdownRenderer.render("<div>toolbar</div>", frameUrl)).thenReturn(frameMarkdown);
        when(markdownPostProcessor.process(frameMarkdown)).thenReturn(frameMarkdown);

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

        assertThat(response.getContent()).contains("## Embedded Frame Contents");
        assertThat(response.getContent()).contains("插入");
        verify(apiResponse).dispose();
    }

    @Test
    public void shouldReturnScreenshotDataUriForScreenshotFormat() {
        ScrapeRequest request = ScrapeRequest.builder()
            .url("https://example.com/page")
            .format("screenshot")
            .build();
        ScrapeProperties properties = new ScrapeProperties();
        BrowserRuntimeContext context = BrowserRuntimeContext.builder().page(page).build();

        when(page.request()).thenReturn(apiRequestContext);
        when(apiRequestContext.get(eq("https://example.com/page"), any(RequestOptions.class))).thenReturn(apiResponse);
        when(apiResponse.ok()).thenReturn(false);
        when(page.content()).thenReturn("<html><body>raw</body></html>");
        when(htmlMainContentCleaner.clean(
            "<html><body>raw</body></html>",
            "https://example.com/page",
            false,
            properties.isStripChromeTags(),
            properties.isRemoveBase64Images()
        ))
            .thenReturn("<html><body>raw</body></html>");
        when(page.screenshot()).thenReturn(new byte[] {1, 2, 3});

        ScrapeBrowserTask task = new ScrapeBrowserTask(
            request,
            ScrapeFormat.SCREENSHOT,
            properties,
            htmlMainContentCleaner,
            markdownRenderer,
            markdownPostProcessor,
            linkExtractor
        );

        ScrapeResponse response = task.execute(context);

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getFormat()).isEqualTo("screenshot");
        assertThat(response.getScreenshotMime()).isEqualTo("image/png");
        assertThat(response.getScreenshotBase64()).isEqualTo("data:image/png;base64,AQID");
        verify(apiResponse).dispose();
    }

    @Test
    public void shouldReturnDirectMediaDataUriRegardlessOfFormat() {
        ScrapeRequest request = ScrapeRequest.builder()
            .url("https://example.com/image.png")
            .format("markdown")
            .build();
        ScrapeProperties properties = new ScrapeProperties();
        BrowserRuntimeContext context = BrowserRuntimeContext.builder().page(page).build();

        when(page.request()).thenReturn(apiRequestContext);
        when(apiRequestContext.get(eq("https://example.com/image.png"), any(RequestOptions.class))).thenReturn(apiResponse);
        when(apiResponse.ok()).thenReturn(true);
        when(apiResponse.headers()).thenReturn(Map.of("content-type", "image/png"));
        when(apiResponse.body()).thenReturn(new byte[] {1, 2});

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

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getFormat()).isEqualTo("media");
        assertThat(response.getScreenshotMime()).isEqualTo("image/png");
        assertThat(response.getScreenshotBase64()).isEqualTo("data:image/png;base64,AQI=");
        verify(page, never()).navigate(any(String.class), any(Page.NavigateOptions.class));
        verify(apiResponse).dispose();
    }

    @Test
    public void shouldFallbackToHttpDirectMediaWhenNavigateTriggersDownload() throws Exception {
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
            ScrapeRequest request = ScrapeRequest.builder()
                .url(url)
                .format("markdown")
                .build();
            ScrapeProperties properties = new ScrapeProperties();
            BrowserRuntimeContext context = BrowserRuntimeContext.builder().page(page).build();

            lenient().when(page.request()).thenReturn(apiRequestContext);
            lenient().when(apiRequestContext.get(eq(url), any(RequestOptions.class))).thenReturn(apiResponse);
            lenient().when(apiResponse.ok()).thenReturn(false);
            lenient().when(page.navigate(eq(url), any(Page.NavigateOptions.class)))
                .thenThrow(new RuntimeException("Download is starting"));

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

            assertThat(response.getStatusCode()).isEqualTo(200);
            assertThat(response.getFormat()).isEqualTo("media");
            assertThat(response.getScreenshotMime()).isEqualTo("application/pdf");
            assertThat(response.getScreenshotBase64()).isEqualTo(
                "data:application/pdf;base64," + Base64.getEncoder().encodeToString(pdfBytes)
            );
            verify(apiResponse).dispose();
            verify(page, never()).content();
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void shouldReturnEmptyTextWhenNavigateAbortedAndHttpStatusNoContent() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/no-content", exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();

        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/no-content";
        try {
            ScrapeRequest request = ScrapeRequest.builder()
                .url(url)
                .format("markdown")
                .build();
            ScrapeProperties properties = new ScrapeProperties();
            BrowserRuntimeContext context = BrowserRuntimeContext.builder().page(page).build();

            lenient().when(page.request()).thenReturn(apiRequestContext);
            lenient().when(apiRequestContext.get(eq(url), any(RequestOptions.class))).thenReturn(apiResponse);
            lenient().when(apiResponse.ok()).thenReturn(false);
            lenient().when(page.navigate(eq(url), any(Page.NavigateOptions.class)))
                .thenThrow(new RuntimeException("net::ERR_ABORTED at " + url));

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

            assertThat(response.getStatusCode()).isEqualTo(200);
            assertThat(response.getFormat()).isEqualTo("markdown");
            assertThat(response.getContent()).isEqualTo("");
            verify(apiResponse).dispose();
            verify(page, never()).content();
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void shouldTreatOctetStreamWithMediaExtensionAsDirectMedia() {
        ScrapeRequest request = ScrapeRequest.builder()
            .url("https://example.com/image.png?x=1#anchor")
            .format("markdown")
            .build();
        ScrapeProperties properties = new ScrapeProperties();
        BrowserRuntimeContext context = BrowserRuntimeContext.builder().page(page).build();

        when(page.request()).thenReturn(apiRequestContext);
        when(apiRequestContext.get(eq("https://example.com/image.png?x=1#anchor"), any(RequestOptions.class))).thenReturn(apiResponse);
        when(apiResponse.ok()).thenReturn(true);
        when(apiResponse.headers()).thenReturn(Map.of("content-type", "application/octet-stream"));
        when(apiResponse.body()).thenReturn(new byte[] {5, 6, 7});

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

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getFormat()).isEqualTo("media");
        assertThat(response.getScreenshotMime()).isEqualTo("application/octet-stream");
        assertThat(response.getScreenshotBase64()).isEqualTo("data:application/octet-stream;base64,BQYH");
        verify(page, never()).navigate(any(String.class), any(Page.NavigateOptions.class));
        verify(apiResponse).dispose();
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
        when(page.request()).thenReturn(apiRequestContext);
        when(apiRequestContext.get(eq("https://example.com"), any(RequestOptions.class))).thenReturn(apiResponse);
        when(apiResponse.ok()).thenReturn(false);
        when(htmlMainContentCleaner.clean(
            "<html><body>raw</body></html>",
            "https://example.com",
            false,
            properties.isStripChromeTags(),
            properties.isRemoveBase64Images()
        ))
            .thenReturn("<html><body>raw</body></html>");
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
        verify(htmlMainContentCleaner).clean(
            "<html><body>raw</body></html>",
            "https://example.com",
            false,
            properties.isStripChromeTags(),
            properties.isRemoveBase64Images()
        );
        verify(apiResponse).dispose();
    }

}
