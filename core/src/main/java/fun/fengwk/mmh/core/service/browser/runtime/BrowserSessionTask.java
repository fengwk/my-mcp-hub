package fun.fengwk.mmh.core.service.browser.runtime;

import com.microsoft.playwright.Browser;

/**
 * Task executed with a shared browser instance.
 *
 * @author fengwk
 */
@FunctionalInterface
public interface BrowserSessionTask<T> {

    T execute(Browser browser) throws Exception;

}
