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

    @Test
    public void shouldPreferMainContainerWhenOnlyMainContentEnabled() {
        String html = """
            <html><body>
              <div class="navigation">home products docs community support pricing login register search</div>
              <main id="main-content">
                <h1>Deep Article</h1>
                <p>The main content should win against navigation blocks.</p>
              </main>
            </body></html>
            """;

        String result = cleaner.clean(html, "https://example.com", true, true, true);

        assertThat(result).contains("Deep Article");
        assertThat(result).doesNotContain("pricing login register search");
        assertThat(result.trim()).startsWith("<main");
    }

    @Test
    public void shouldPreferWikipediaContentContainerWhenAvailable() {
        String html = """
            <html><body>
              <div id="mw-navigation">Main menu Contents Current events Random article</div>
              <div id="mw-content-text" class="mw-parser-output">
                <p>Web scraping is data extraction from websites.</p>
              </div>
              <div id="footer">About Wikimedia Contact developers</div>
            </body></html>
            """;

        String result = cleaner.clean(html, "https://en.wikipedia.org/wiki/Web_scraping", true, true, true);

        assertThat(result).contains("Web scraping is data extraction from websites.");
        assertThat(result).doesNotContain("Main menu Contents Current events");
        assertThat(result).doesNotContain("About Wikimedia Contact developers");
        assertThat(result.trim()).startsWith("<div id=\"mw-content-text\"");
    }

    @Test
    public void shouldRemoveEditorChromeElementsWhenOnlyMainContentEnabled() {
        String html = """
            <html><body>
              <main id="main-content">
                <h1>Title <a class="headerlink" href="#title">¶</a></h1>
                <span class="mw-editsection">[edit]</span>
                <button class="copybutton">Copy</button>
                <p>Body text.</p>
              </main>
            </body></html>
            """;

        String result = cleaner.clean(html, "https://example.com/doc", true, true, true);

        assertThat(result).contains("Body text.");
        assertThat(result).doesNotContain("headerlink");
        assertThat(result).doesNotContain("mw-editsection");
        assertThat(result).doesNotContain("copybutton");
        assertThat(result).doesNotContain("[edit]");
        assertThat(result).doesNotContain("Copy");
    }

    @Test
    public void shouldApplyWikipediaDomainChromeRules() {
        String html = """
            <html><body>
              <div class="vector-header-container">Wikipedia header tools</div>
              <div id="mw-navigation">Main menu Contents Current events Random article</div>
              <div id="mw-content-text" class="mw-parser-output">
                <div class="hatnote">For broader coverage of this topic, see Data scraping.</div>
                <table class="ambox"><tbody><tr><td>This article needs additional citations.</td></tr></tbody></table>
                <p>Web scraping keeps this article content.</p>
                <p>Definition text<sup class="reference">[1]</sup></p>
                <div class="navbox">Navigation templates</div>
              </div>
            </body></html>
            """;

        String result = cleaner.clean(html, "https://en.wikipedia.org/wiki/Web_scraping", true, true, true);

        assertThat(result).contains("Web scraping keeps this article content.");
        assertThat(result).contains("Definition text");
        assertThat(result).doesNotContain("Wikipedia header tools");
        assertThat(result).doesNotContain("Main menu Contents Current events");
        assertThat(result).doesNotContain("For broader coverage of this topic");
        assertThat(result).doesNotContain("additional citations");
        assertThat(result).doesNotContain("[1]");
        assertThat(result).doesNotContain("Navigation templates");
    }

    @Test
    public void shouldApplyPythonDocsDomainChromeRules() {
        String html = """
            <html><body>
              <div class="sphinxsidebar">Sidebar navigation and index</div>
              <main>
                <h1>An Informal Introduction to Python</h1>
                <a class="headerlink" href="#intro">¶</a>
                <button class="copybutton">Copy</button>
                <p>Python tutorial body.</p>
              </main>
            </body></html>
            """;

        String result = cleaner.clean(html, "https://docs.python.org/3/tutorial/introduction.html", true, true, true);

        assertThat(result).contains("An Informal Introduction to Python");
        assertThat(result).contains("Python tutorial body.");
        assertThat(result).doesNotContain("Sidebar navigation and index");
        assertThat(result).doesNotContain("headerlink");
        assertThat(result).doesNotContain("copybutton");
    }

}
