package fun.fengwk.mmh.core.service.scrape.parser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author fengwk
 */
public class MarkdownRendererTest {

    private final MarkdownRenderer markdownRenderer = new MarkdownRenderer();

    @Test
    public void shouldRenderPlainTextFromHtml() {
        String result = markdownRenderer.render("<h1>Title</h1><p>content</p>");

        assertThat(result).isEqualTo("Title content");
    }

    @Test
    public void shouldHandleNullInput() {
        assertThat(markdownRenderer.render(null)).isEmpty();
    }

}
