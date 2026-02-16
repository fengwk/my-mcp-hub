package fun.fengwk.mmh.core.facade.search.searxng;

import fun.fengwk.mmh.core.CoreTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author fengwk
 */
@Slf4j
@SpringBootTest(classes = CoreTestApplication.class)
public class SearxngClientIT {

    @Autowired
    private SearxngClient searxngClient;

    @Test
    public void testSearch() {
        SearxngClientResponse response = searxngClient.search(Map.of("q", "spring ai"));
        log.info("searxng response status: {}", response.getStatusCode());
        assertThat(response.getStatusCode()).isBetween(200, 299);
        assertThat(response.getBody()).isNotBlank();
        assertThat(response.getBody()).contains("\"results\"");
    }

}
