package fun.fengwk.mmh.core.mcp;

import fun.fengwk.mmh.core.facade.search.model.SearchResponse;
import fun.fengwk.mmh.core.service.UtilMcpService;
import fun.fengwk.mmh.core.service.scrape.model.ScrapeResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author fengwk
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
public class UtilMcpTest {

    @Mock
    private UtilMcpService utilMcpService;

    @Mock
    private McpFormatter mcpFormatter;

    private UtilMcp utilsMcp;

    @BeforeEach
    void setUp() {
        utilsMcp = new UtilMcp(utilMcpService, mcpFormatter);
    }

    @Test
    public void testMmhSearch() {
        SearchResponse response = SearchResponse.builder()
            .statusCode(200)
            .build();
        when(utilMcpService.search("spring ai", 2, "month", 1)).thenReturn(response);
        when(mcpFormatter.format("mmh_search_result.ftl", response)).thenReturn("ok");

        String result = utilsMcp.search("spring ai", 2, "month", 1);
        log.info("mmh_search result:\n{}", result);
        assertThat(result).isEqualTo("ok");

        verify(utilMcpService).search("spring ai", 2, "month", 1);
        verify(mcpFormatter).format("mmh_search_result.ftl", response);
    }

    @Test
    public void testSearchFacadeReturnsError() {
        SearchResponse response = SearchResponse.builder()
            .statusCode(400)
            .error("query is blank")
            .build();
        when(utilMcpService.search(" ", null, null, null)).thenReturn(response);
        when(mcpFormatter.format("mmh_search_result.ftl", response)).thenReturn("Error: query is blank");

        String result = utilsMcp.search(" ", null, null, null);

        assertThat(result).isEqualTo("Error: query is blank");
        verify(utilMcpService).search(" ", null, null, null);
        verify(mcpFormatter).format("mmh_search_result.ftl", response);
    }

    @Test
    public void testFormatterReturnsFormatError() {
        SearchResponse response = SearchResponse.builder()
            .statusCode(200)
            .build();
        when(utilMcpService.search("spring ai", null, null, null)).thenReturn(response);
        when(mcpFormatter.format("mmh_search_result.ftl", response)).thenReturn("format error: boom");

        String result = utilsMcp.search("spring ai", null, null, null);

        assertThat(result).isEqualTo("format error: boom");
        verify(mcpFormatter).format("mmh_search_result.ftl", response);
    }

    @Test
    public void testCreateTempDir() {
        when(utilMcpService.createTempDir()).thenReturn("/tmp/mmh-123");

        String result = utilsMcp.createTempDir();

        assertThat(result).isEqualTo("/tmp/mmh-123");
        verify(utilMcpService).createTempDir();
    }

    @Test
    public void testScrape() {
        ScrapeResponse response = ScrapeResponse.builder()
            .statusCode(200)
            .format("html")
            .content("<html></html>")
            .build();
        when(utilMcpService.scrape("https://example.com", "html", true, 0, "master"))
            .thenReturn(response);
        when(mcpFormatter.format("mmh_scrape_result.ftl", response)).thenReturn("ok");

        String result = utilsMcp.scrape("https://example.com", "html", "master", true, 0);

        assertThat(result).isEqualTo("ok");
        verify(utilMcpService).scrape("https://example.com", "html", true, 0, "master");
        verify(mcpFormatter).format("mmh_scrape_result.ftl", response);
    }

}
