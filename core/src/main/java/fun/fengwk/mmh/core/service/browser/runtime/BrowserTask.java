package fun.fengwk.mmh.core.service.browser.runtime;

/**
 * Generic browser task abstraction.
 *
 * @param <T> task result type
 * @author fengwk
 */
public interface BrowserTask<T> {

    T execute(BrowserRuntimeContext context) throws Exception;

}
