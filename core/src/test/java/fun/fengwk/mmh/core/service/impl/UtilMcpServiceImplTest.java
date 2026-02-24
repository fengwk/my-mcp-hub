package fun.fengwk.mmh.core.service.impl;

import fun.fengwk.mmh.core.facade.search.SearchFacade;
import fun.fengwk.mmh.core.facade.search.model.SearchResponse;
import fun.fengwk.mmh.core.service.scrape.PageScrapeService;
import fun.fengwk.mmh.core.service.scrape.model.ScrapeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author fengwk
 */
@ExtendWith(MockitoExtension.class)
public class UtilMcpServiceImplTest {

    @Mock
    private SearchFacade searchFacade;

    @Mock
    private PageScrapeService pageScrapeService;

    private UtilMcpServiceImpl utilMcpService;

    @BeforeEach
    void setUp() {
        utilMcpService = new UtilMcpServiceImpl(searchFacade, pageScrapeService);
    }

    @Test
    public void testSearch() {
        SearchResponse response = SearchResponse.builder()
            .statusCode(200)
            .build();
        when(searchFacade.search(any())).thenReturn(response);

        SearchResponse result = utilMcpService.search("spring ai", 5, "month", 2);

        assertThat(result).isEqualTo(response);
        ArgumentCaptor<fun.fengwk.mmh.core.facade.search.model.SearchRequest> captor =
            ArgumentCaptor.forClass(fun.fengwk.mmh.core.facade.search.model.SearchRequest.class);
        verify(searchFacade).search(captor.capture());
        assertThat(captor.getValue().getQuery()).isEqualTo("spring ai");
        assertThat(captor.getValue().getLimit()).isEqualTo(5);
        assertThat(captor.getValue().getTimeRange()).isEqualTo("month");
        assertThat(captor.getValue().getPage()).isEqualTo(2);
    }

    @Test
    public void testScrape() {
        ScrapeResponse response = ScrapeResponse.builder()
            .statusCode(200)
            .format("html")
            .content("<html></html>")
            .build();
        when(pageScrapeService.scrape(any())).thenReturn(response);

        ScrapeResponse result = utilMcpService.scrape("https://example.com", "html", true, 100, "master");

        assertThat(result).isEqualTo(response);
        ArgumentCaptor<fun.fengwk.mmh.core.service.scrape.model.ScrapeRequest> captor =
            ArgumentCaptor.forClass(fun.fengwk.mmh.core.service.scrape.model.ScrapeRequest.class);
        verify(pageScrapeService).scrape(captor.capture());
        assertThat(captor.getValue().getUrl()).isEqualTo("https://example.com");
        assertThat(captor.getValue().getFormat()).isEqualTo("html");
        assertThat(captor.getValue().getProfileMode()).isEqualTo("master");
        assertThat(captor.getValue().getOnlyMainContent()).isTrue();
        assertThat(captor.getValue().getWaitFor()).isEqualTo(100);
    }

}
