package fun.fengwk.mmh.core.service.browser.runtime;

import fun.fengwk.mmh.core.service.browser.BrowserProperties;
import fun.fengwk.mmh.core.service.browser.coordination.ProfileIdValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author fengwk
 */
@ExtendWith(MockitoExtension.class)
public class BrowserTaskExecutorTest {

    @Mock
    private ProfileIdValidator profileIdValidator;

    @Mock
    private BrowserWorkerManager browserWorkerManager;

    @SuppressWarnings("unchecked")
    @Test
    public void shouldNormalizeProfileAndDelegateToWorkerManager() {
        BrowserProperties browserProperties = new BrowserProperties();
        BrowserTaskExecutor executor = new BrowserTaskExecutor(
            browserProperties,
            profileIdValidator,
            browserWorkerManager
        );

        when(profileIdValidator.normalizeProfileId("master")).thenReturn("master");
        when(browserWorkerManager.execute(any())).thenReturn("ok");

        String result = executor.execute("master", context -> "ignored");

        assertThat(result).isEqualTo("ok");
        verify(profileIdValidator).normalizeProfileId("master");
        verify(browserWorkerManager).execute(any());
    }

}
