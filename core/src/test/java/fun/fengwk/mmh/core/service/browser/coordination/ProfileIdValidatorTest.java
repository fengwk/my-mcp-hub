package fun.fengwk.mmh.core.service.browser.coordination;

import fun.fengwk.mmh.core.service.browser.BrowserProperties;
import fun.fengwk.mmh.core.service.scrape.ScrapeProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author fengwk
 */
public class ProfileIdValidatorTest {

    @Test
    public void shouldUseDefaultProfileIdWhenBlank() {
        BrowserProperties browserProperties = new BrowserProperties();
        ScrapeProperties scrapeProperties = new ScrapeProperties();
        scrapeProperties.setDefaultProfileId("master");
        ProfileIdValidator validator = new ProfileIdValidator(browserProperties, scrapeProperties);

        assertThat(validator.normalizeProfileId(" ")).isEqualTo("master");
    }

    @Test
    public void shouldRejectPathTraversalProfileId() {
        BrowserProperties browserProperties = new BrowserProperties();
        ScrapeProperties scrapeProperties = new ScrapeProperties();
        ProfileIdValidator validator = new ProfileIdValidator(browserProperties, scrapeProperties);

        assertThatThrownBy(() -> validator.normalizeProfileId("../bad"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("invalid profileId");
    }

    @Test
    public void shouldResolveProfileDirUnderRoot() {
        BrowserProperties browserProperties = new BrowserProperties();
        ScrapeProperties scrapeProperties = new ScrapeProperties();
        ProfileIdValidator validator = new ProfileIdValidator(browserProperties, scrapeProperties);

        assertThat(validator.resolveProfileDir(Paths.get("/tmp/root"), "master"))
            .isEqualTo(Paths.get("/tmp/root/master"));
    }

    @Test
    public void shouldRejectResolvedPathOutsideRoot() {
        BrowserProperties browserProperties = new BrowserProperties();
        ScrapeProperties scrapeProperties = new ScrapeProperties();
        ProfileIdValidator validator = new ProfileIdValidator(browserProperties, scrapeProperties);

        assertThatThrownBy(() -> validator.resolveProfileDir(Paths.get("/tmp/root"), "../evil"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("invalid profile path");
    }

}
