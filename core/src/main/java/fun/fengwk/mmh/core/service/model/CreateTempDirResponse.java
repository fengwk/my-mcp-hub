package fun.fengwk.mmh.core.service.model;

import lombok.Builder;
import lombok.Data;

/**
 * Create temp dir response model.
 *
 * @author fengwk
 */
@Data
@Builder
public class CreateTempDirResponse {

    private String path;
    private String error;

}
