package fun.fengwk.mmh.core.service.scrape.runtime;

/**
 * Thrown when master profile lock is occupied.
 *
 * @author fengwk
 */
public class MasterProfileLockedException extends RuntimeException {

    public static final String DEFAULT_MESSAGE =
        "Master profile is in use and temporarily unavailable; please retry later or use default mode.";

    public MasterProfileLockedException() {
        super(DEFAULT_MESSAGE);
    }

    public MasterProfileLockedException(String message) {
        super(message);
    }

}
