package fun.fengwk.mmh.core.service.scrape.parser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author fengwk
 */
public class MarkdownPostProcessorTest {

    private final MarkdownPostProcessor markdownPostProcessor = new MarkdownPostProcessor();

    @Test
    public void shouldTrimMarkdown() {
        assertThat(markdownPostProcessor.process("  hello  ")).isEqualTo("hello");
    }

    @Test
    public void shouldCollapseBlankLines() {
        String input = "line1\n\n\nline2\n\n\n";

        assertThat(markdownPostProcessor.process(input)).isEqualTo("line1\n\nline2");
    }

    @Test
    public void shouldReturnEmptyWhenBlank() {
        assertThat(markdownPostProcessor.process(" ")).isEmpty();
        assertThat(markdownPostProcessor.process(null)).isEmpty();
    }

    @Test
    public void shouldRemoveZeroWidthChars() {
        assertThat(markdownPostProcessor.process("\uFEFFhello\u200B\u2060"))
            .isEqualTo("hello");
    }

    @Test
    public void shouldRemoveEmptyLinks() {
        assertThat(markdownPostProcessor.process("before [](https://a.com) after"))
            .isEqualTo("before after");
    }

    @Test
    public void shouldKeepEmptyImageLinks() {
        assertThat(markdownPostProcessor.process("![](https://a.com/icon.png)"))
            .isEqualTo("![](https://a.com/icon.png)");
    }

    @Test
    public void shouldCompactAdjacentImages() {
        assertThat(markdownPostProcessor.process("![](a.png) ![](b.png)"))
            .isEqualTo("![](a.png)![](b.png)");
    }

}
