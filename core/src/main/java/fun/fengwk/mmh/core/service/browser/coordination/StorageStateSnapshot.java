package fun.fengwk.mmh.core.service.browser.coordination;

import lombok.Builder;
import lombok.Data;

import java.nio.file.Path;

/**
 * Snapshot info for worker startup.
 *
 * @author fengwk
 */
@Data
@Builder
public class StorageStateSnapshot {

    private String profileId;
    private long version;
    private Path statePath;

}
