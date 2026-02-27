package fun.fengwk.mmh.core.service.browser.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author fengwk
 */
public class ProfileTypeTest {

    @Test
    public void shouldDefaultWhenBlank() {
        assertThat(ProfileType.fromValue(null)).isEqualTo(ProfileType.DEFAULT);
        assertThat(ProfileType.fromValue(" ")).isEqualTo(ProfileType.DEFAULT);
    }

    @Test
    public void shouldResolveCaseInsensitive() {
        assertThat(ProfileType.fromValue("MASTER")).isEqualTo(ProfileType.MASTER);
    }

    @Test
    public void shouldThrowWhenUnsupported() {
        assertThatThrownBy(() -> ProfileType.fromValue("unknown"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unsupported profileMode");
    }

}
