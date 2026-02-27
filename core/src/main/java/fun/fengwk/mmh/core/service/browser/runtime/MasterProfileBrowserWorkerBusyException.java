package fun.fengwk.mmh.core.service.browser.runtime;

/**
 * Exception thrown when master profile browser worker pool is busy.
 */
public class MasterProfileBrowserWorkerBusyException extends RuntimeException {

    public MasterProfileBrowserWorkerBusyException(String message) {
        super(message);
    }

    public MasterProfileBrowserWorkerBusyException(String message, Throwable cause) {
        super(message, cause);
    }
}