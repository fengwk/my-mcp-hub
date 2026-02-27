package fun.fengwk.mmh.core.service.browser.runtime;

/**
 * Exception thrown when default browser worker pool is busy.
 */
public class DefaultBrowserWorkerBusyException extends RuntimeException {

    public DefaultBrowserWorkerBusyException(String message) {
        super(message);
    }

    public DefaultBrowserWorkerBusyException(String message, Throwable cause) {
        super(message, cause);
    }
}