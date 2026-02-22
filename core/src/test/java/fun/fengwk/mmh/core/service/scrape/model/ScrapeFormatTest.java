package fun.fengwk.mmh.core.service.scrape.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author fengwk
 */
public class ScrapeFormatTest {

    @Test
    public void shouldDefaultToMarkdownWhenValueBlank() {
        assertThat(ScrapeFormat.fromValue(null)).isEqualTo(ScrapeFormat.MARKDOWN);
        assertThat(ScrapeFormat.fromValue(" ")).isEqualTo(ScrapeFormat.MARKDOWN);
    }

    @Test
    public void shouldParseFormatIgnoringCase() {
        assertThat(ScrapeFormat.fromValue("HTML")).isEqualTo(ScrapeFormat.HTML);
        assertThat(ScrapeFormat.fromValue("fullscreenshot")).isEqualTo(ScrapeFormat.FULLSCREENSHOT);
    }

    @Test
    public void shouldThrowWhenFormatUnsupported() {
        assertThatThrownBy(() -> ScrapeFormat.fromValue("pdf"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unsupported format");
    }

}
