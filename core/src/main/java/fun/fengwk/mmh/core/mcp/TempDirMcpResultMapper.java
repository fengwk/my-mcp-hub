package fun.fengwk.mmh.core.mcp;

import fun.fengwk.convention4j.common.lang.StringUtils;
import fun.fengwk.mmh.core.service.model.CreateTempDirResponse;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Component;

/**
 * Temp dir result mapper.
 *
 * @author fengwk
 */
@Component
public class TempDirMcpResultMapper {

    public McpSchema.CallToolResult toResult(CreateTempDirResponse response) {
        if (response == null) {
            return McpToolSupport.errorResult("create temp dir response is null");
        }
        if (StringUtils.isNotBlank(response.getError())) {
            return McpToolSupport.errorResult(response.getError());
        }
        if (StringUtils.isBlank(response.getPath())) {
            return McpToolSupport.errorResult("create temp dir path is blank");
        }
        return McpToolSupport.textResult(response.getPath());
    }

}
