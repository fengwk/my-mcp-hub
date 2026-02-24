package fun.fengwk.mmh.core.service.scrape.parser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author fengwk
 */
public class MarkdownRendererTest {

    private final MarkdownRenderer markdownRenderer = new MarkdownRenderer();

    @Test
    public void shouldRenderStructuredMarkdown() {
        String html = """
            <h1>Title</h1>
            <p>content <a href=\"https://a.com\">link</a></p>
            <ul><li>item</li></ul>
            """;

        String result = markdownRenderer.render(html);

        assertThat(result).contains("Title");
        assertThat(result).contains("===");
        assertThat(result).contains("[link](https://a.com)");
        assertThat(result).contains("* item");
    }

    @Test
    public void shouldHandleNullInput() {
        assertThat(markdownRenderer.render(null)).isEmpty();
    }

    @Test
    public void shouldRenderCodeBlock() {
        String html = "<pre><code>line1\nline2</code></pre>";

        String result = markdownRenderer.render(html);

        assertThat(result).contains("line1");
        assertThat(result).contains("line2");
    }

    @Test
    public void shouldSeparateBlockContainers() {
        String html = "<div>first</div><div>second</div>";

        String result = markdownRenderer.render(html);

        assertThat(result).contains("first");
        assertThat(result).contains("second");
        assertThat(result.indexOf("second")).isGreaterThan(result.indexOf("first"));
    }

    @Test
    public void shouldRenderListItemBlocks() {
        String html = """
            <ul>
              <li><img src="https://example.com/hero.png" alt="hero"/><h3>Title</h3><p>Desc</p></li>
            </ul>
            """;

        String result = markdownRenderer.render(html, "https://example.com");

        assertThat(result).contains("![hero](https://example.com/hero.png)");
        assertThat(result).contains("### Title");
        assertThat(result).contains("Desc");
    }

}
