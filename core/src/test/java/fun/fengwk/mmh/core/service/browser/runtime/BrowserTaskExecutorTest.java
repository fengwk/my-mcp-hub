package fun.fengwk.mmh.core.service.browser.runtime;

import fun.fengwk.mmh.core.service.browser.coordination.ProfileIdValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @Test
    public void shouldNormalizeProfileAndDelegateToWorkerManager() {
        BrowserTaskExecutor executor = new BrowserTaskExecutor(
            profileIdValidator,
            browserWorkerManager
        );

        when(profileIdValidator.normalizeProfileId("master")).thenReturn("master");
        when(browserWorkerManager.executeDefault(any())).thenReturn("ok");

        String result = executor.execute("master", ProfileType.DEFAULT, context -> "ignored");

        assertThat(result).isEqualTo("ok");
        verify(profileIdValidator).normalizeProfileId("master");
        verify(browserWorkerManager).executeDefault(any());
    }

    @Test
    public void shouldDelegateMasterModeToMasterPool() {
        BrowserTaskExecutor executor = new BrowserTaskExecutor(
            profileIdValidator,
            browserWorkerManager
        );

        when(profileIdValidator.normalizeProfileId("master")).thenReturn("master");
        when(browserWorkerManager.executeMaster(eq("master"), any())).thenReturn("ok");

        String result = executor.execute("master", ProfileType.MASTER, context -> "ignored");

        assertThat(result).isEqualTo("ok");
        verify(profileIdValidator).normalizeProfileId("master");
        verify(browserWorkerManager).executeMaster(eq("master"), any());
    }

}
