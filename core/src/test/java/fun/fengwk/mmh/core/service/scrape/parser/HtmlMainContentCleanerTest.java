package fun.fengwk.mmh.core.service.scrape.parser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author fengwk
 */
public class HtmlMainContentCleanerTest {

    private final HtmlMainContentCleaner cleaner = new HtmlMainContentCleaner();

    @Test
    public void shouldRemoveNonMainWhenOnlyMainContent() {
        String html = "<html><body><header>nav</header><div id=\"main\"><p>hello</p></div></body></html>";

        String result = cleaner.clean(html, "https://example.com", true, true, true);

        assertThat(result).contains("hello");
        assertThat(result).doesNotContain("nav");
    }

    @Test
    public void shouldKeepNonMainWhenOnlyMainContentDisabledAndStripChromeOff() {
        String html = "<html><body><header>nav</header><div id=\"main\"><p>hello</p></div></body></html>";

        String result = cleaner.clean(html, "https://example.com", false, false, true);

        assertThat(result).contains("nav");
        assertThat(result).contains("hello");
    }

    @Test
    public void shouldKeepChromeWhenOnlyMainContentDisabled() {
        String html = "<html><body><header>nav</header><div id=\"main\"><p>hello</p></div></body></html>";

        String result = cleaner.clean(html, "https://example.com", false, true, true);

        assertThat(result).contains("hello");
        assertThat(result).contains("nav");
    }

    @Test
    public void shouldChooseLargestSrcsetAndMakeAbsolute() {
        String html = "<html><body><img src=\"/small.png\" srcset=\"/small.png 1x, /large.png 2x\"></body></html>";

        String result = cleaner.clean(html, "https://example.com", false, false, true);

        assertThat(result).contains("src=\"https://example.com/large.png\"");
    }

}
