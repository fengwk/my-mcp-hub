package fun.fengwk.mmh.core.service.scrape.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author fengwk
 */
public class ScrapeProfileModeTest {

    @Test
    public void shouldDefaultToDefaultWhenValueBlank() {
        assertThat(ScrapeProfileMode.fromValue(null)).isEqualTo(ScrapeProfileMode.DEFAULT);
        assertThat(ScrapeProfileMode.fromValue(" ")).isEqualTo(ScrapeProfileMode.DEFAULT);
    }

    @Test
    public void shouldParseMasterIgnoringCase() {
        assertThat(ScrapeProfileMode.fromValue("MASTER")).isEqualTo(ScrapeProfileMode.MASTER);
    }

    @Test
    public void shouldThrowWhenModeUnsupported() {
        assertThatThrownBy(() -> ScrapeProfileMode.fromValue("unknown"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unsupported profileMode");
    }

}
