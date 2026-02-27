package fun.fengwk.mmh.core.service.browser.coordination;

import fun.fengwk.mmh.core.service.browser.BrowserProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author fengwk
 */
public class ProfileIdValidatorTest {

    @Test
    public void shouldUseDefaultProfileIdWhenBlank() {
        BrowserProperties browserProperties = new BrowserProperties();
        browserProperties.setDefaultProfileId("master");
        ProfileIdValidator validator = new ProfileIdValidator(browserProperties);

        assertThat(validator.normalizeProfileId(" ")).isEqualTo("master");
    }

    @Test
    public void shouldRejectPathTraversalProfileId() {
        BrowserProperties browserProperties = new BrowserProperties();
        ProfileIdValidator validator = new ProfileIdValidator(browserProperties);

        assertThatThrownBy(() -> validator.normalizeProfileId("../bad"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("invalid profileId");
    }

    @Test
    public void shouldAcceptSimpleProfileId() {
        BrowserProperties browserProperties = new BrowserProperties();
        ProfileIdValidator validator = new ProfileIdValidator(browserProperties);

        assertThat(validator.normalizeProfileId("master")).isEqualTo("master");
    }

    @Test
    public void shouldRejectSlashInProfileId() {
        BrowserProperties browserProperties = new BrowserProperties();
        ProfileIdValidator validator = new ProfileIdValidator(browserProperties);

        assertThatThrownBy(() -> validator.normalizeProfileId("a/b"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("invalid profileId");
    }

}
