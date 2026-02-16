package fun.fengwk.mmh.core.mcp;

import fun.fengwk.mmh.core.CoreTestApplication;
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
public class UtilMcpIT {

    @Autowired
    private UtilMcp utilsMcp;

    @Test
    public void testMmhSearch() {
        String result = utilsMcp.search("spring ai", 5, "month", 1);
        log.info("mmh_search result:\n{}", result);
        assertThat(result).isNotBlank();
        assertThat(result).doesNotContain("format error");
        assertThat(result).doesNotStartWith("Error:");
        assertThat(result).containsAnyOf("## Result", "No results.");
    }

}
