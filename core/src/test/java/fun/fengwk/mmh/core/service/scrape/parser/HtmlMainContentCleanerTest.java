package fun.fengwk.mmh.core.service.scrape.parser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author fengwk
 */
public class HtmlMainContentCleanerTest {

    private final HtmlMainContentCleaner cleaner = new HtmlMainContentCleaner();

    @Test
    public void shouldReturnMainWhenMainExists() {
        String html = "<html><body><main><p>hello</p></main><div>ignored</div></body></html>";

        String result = cleaner.clean(html);

        assertThat(result).contains("<main>");
        assertThat(result).contains("hello");
        assertThat(result).doesNotContain("ignored");
    }

    @Test
    public void shouldFallbackToBodyWhenMainNotFound() {
        String html = "<html><body><div>hello</div></body></html>";

        String result = cleaner.clean(html);

        assertThat(result).contains("<div>hello</div>");
    }

}
