package fun.fengwk.mmh.core.service.scrape.runtime;

/**
 * Thrown when master profile lock is occupied.
 *
 * @author fengwk
 */
public class MasterProfileLockedException extends RuntimeException {

    public MasterProfileLockedException(String message) {
        super(message);
    }

}
