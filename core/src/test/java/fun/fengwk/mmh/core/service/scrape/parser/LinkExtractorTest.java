package fun.fengwk.mmh.core.service.scrape.parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author fengwk
 */
public class LinkExtractorTest {

    private final LinkExtractor linkExtractor = new LinkExtractor();

    @Test
    public void shouldExtractAndDeduplicateLinks() {
        String html = """
            <html><body>
            <a href=\"https://a.com\">a</a>
            <a href=\"https://b.com\">b</a>
            <a href=\"https://a.com\">a2</a>
            </body></html>
            """;

        List<String> links = linkExtractor.extract(html, "https://example.com");

        assertThat(links).containsExactly("https://a.com", "https://b.com");
    }

    @Test
    public void shouldReturnEmptyWhenNoLink() {
        assertThat(linkExtractor.extract("<html><body>no links</body></html>", "https://example.com")).isEmpty();
    }

    @Test
    public void shouldResolveRelativeLinksWithBaseUrl() {
        String html = """
            <html><body>
            <a href="/a">a</a>
            <a href="b">b</a>
            </body></html>
            """;

        List<String> links = linkExtractor.extract(html, "https://example.com/root/");

        assertThat(links).containsExactly(
            "https://example.com/a",
            "https://example.com/root/b"
        );
    }

}
