package fun.fengwk.mmh.core.facade.search.impl;

import fun.fengwk.mmh.core.facade.search.model.SearchRequest;
import fun.fengwk.mmh.core.facade.search.model.SearchResponse;
import fun.fengwk.mmh.core.facade.search.searxng.SearxngClient;
import fun.fengwk.mmh.core.facade.search.searxng.SearxngClientResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SearchFacadeImpl tests.
 *
 * @author fengwk
 */
@ExtendWith(MockitoExtension.class)
class SearchFacadeImplTest {

    @Mock
    private SearxngClient searxngClient;

    private SearchFacadeImpl searchFacade;

    @BeforeEach
    void setUp() {
        searchFacade = new SearchFacadeImpl(searxngClient, new ObjectMapper());
    }

    @Test
    void shouldReturnBadRequestWhenQueryBlank() {
        SearchRequest request = new SearchRequest();
        request.setQuery(" ");

        SearchResponse response = searchFacade.search(request);

        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getError()).isEqualTo("query is blank");
        verify(searxngClient, never()).search(anyMap());
    }

    @Test
    void shouldMapAndTruncateResults() {
        when(searxngClient.search(anyMap()))
            .thenReturn(SearxngClientResponse.builder()
                .statusCode(200)
                .body(buildJsonResults(20))
                .build());

        SearchRequest request = new SearchRequest();
        request.setQuery("spring ai");

        SearchResponse response = searchFacade.search(request);

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getQuery()).isEqualTo("spring ai");
        assertThat(response.getNumberOfResults()).isEqualTo(20);
        assertThat(response.getResults()).hasSize(10);
        assertThat(response.getResults().get(0).getTitle()).isEqualTo("title-1");
        assertThat(response.getResults().get(0).getUrl()).isEqualTo("https://example.com/1");
        assertThat(response.getResults().get(0).getContent()).isEqualTo("content-1");
    }

    @Test
    void shouldPassTimeRangeAndPage() {
        when(searxngClient.search(anyMap()))
            .thenReturn(SearxngClientResponse.builder()
                .statusCode(200)
                .body(buildJsonResults(1))
                .build());

        SearchRequest request = new SearchRequest();
        request.setQuery("spring ai");
        request.setTimeRange("month");
        request.setPage(2);

        searchFacade.search(request);

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(searxngClient).search(captor.capture());
        Map<String, String> params = captor.getValue();
        assertThat(params.get("q")).isEqualTo("spring ai");
        assertThat(params.get("time_range")).isEqualTo("month");
        assertThat(params.get("pageno")).isEqualTo("2");
    }

    @Test
    void shouldReturnErrorWhenUpstreamFails() {
        when(searxngClient.search(anyMap()))
            .thenReturn(SearxngClientResponse.builder()
                .statusCode(500)
                .error(new RuntimeException("upstream failed"))
                .build());

        SearchRequest request = new SearchRequest();
        request.setQuery("spring ai");

        SearchResponse response = searchFacade.search(request);

        assertThat(response.getStatusCode()).isEqualTo(500);
        assertThat(response.getError()).isEqualTo("upstream failed");
    }

    private static String buildJsonResults(int count) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"query\":\"spring ai\",\"number_of_results\":").append(count)
            .append(",\"results\":[");
        for (int i = 1; i <= count; i++) {
            if (i > 1) {
                builder.append(",");
            }
            builder.append("{\"title\":\"title-").append(i).append("\",")
                .append("\"url\":\"https://example.com/").append(i).append("\",")
                .append("\"content\":\"content-").append(i).append("\"}");
        }
        builder.append("]}");
        return builder.toString();
    }

}
