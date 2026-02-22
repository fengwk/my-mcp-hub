package fun.fengwk.mmh.core.service.browser.runtime;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import lombok.Builder;
import lombok.Data;

/**
 * Runtime context passed to browser tasks.
 *
 * @author fengwk
 */
@Data
@Builder
public class BrowserRuntimeContext {

    private String profileId;
    private long baseVersion;
    private BrowserContext browserContext;
    private Page page;

}
