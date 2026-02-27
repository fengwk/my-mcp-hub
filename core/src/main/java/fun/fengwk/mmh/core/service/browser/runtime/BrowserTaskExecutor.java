package fun.fengwk.mmh.core.service.browser.runtime;

import fun.fengwk.mmh.core.service.browser.coordination.ProfileIdValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Executes browser tasks with profile mode routing.
 *
 * @author fengwk
 */
@Component
@RequiredArgsConstructor
public class BrowserTaskExecutor {

    private final ProfileIdValidator profileIdValidator;
    private final BrowserWorkerManager browserWorkerManager;

    public <T> T execute(String profileId, ProfileType profileType, BrowserTask<T> task) {
        String normalizedProfileId = profileIdValidator.normalizeProfileId(profileId);
        if (profileType == ProfileType.MASTER) {
            return browserWorkerManager.executeMaster(normalizedProfileId, task);
        }
        return browserWorkerManager.executeDefault(task);
    }

}
