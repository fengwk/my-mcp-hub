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
    public void shouldReturnEmptyWhenBlank() {
        assertThat(markdownPostProcessor.process(" ")).isEmpty();
        assertThat(markdownPostProcessor.process(null)).isEmpty();
    }

}
