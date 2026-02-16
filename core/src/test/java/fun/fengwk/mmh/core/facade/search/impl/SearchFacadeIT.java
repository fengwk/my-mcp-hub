package fun.fengwk.mmh.core.facade.search.impl;

import fun.fengwk.mmh.core.CoreTestApplication;
import fun.fengwk.mmh.core.facade.search.SearchFacade;
import fun.fengwk.mmh.core.facade.search.model.SearchRequest;
import fun.fengwk.mmh.core.facade.search.model.SearchResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author fengwk
 */
@Slf4j
@SpringBootTest(classes = CoreTestApplication.class)
public class SearchFacadeIT {

    @Autowired
    private SearchFacade searchFacade;

    @Test
    public void testSearch() {
        SearchRequest request = new SearchRequest();
        request.setQuery("spring ai");
        request.setLimit(5);
        request.setTimeRange("month");
        request.setPage(1);

        SearchResponse response = searchFacade.search(request);
        log.info("search status: {}, error: {}", response.getStatusCode(), response.getError());
        assertThat(response.getStatusCode()).isBetween(200, 299);
        assertThat(response.getError()).isNull();
        assertThat(response.getResults()).isNotNull();
    }

}
