package fun.fengwk.mmh.core.mcp;

import fun.fengwk.convention4j.common.lang.StringUtils;
import fun.fengwk.mmh.core.service.UtilMcpService;
import fun.fengwk.mmh.core.service.model.CreateTempDirResponse;
import lombok.RequiredArgsConstructor;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

/**
 * Annotation-based MCP tools for simple temp-dir use case.
 *
 * @author fengwk
 */
@Component
@RequiredArgsConstructor
public class AnnotatedMcpTools {

    private final UtilMcpService utilMcpService;

    @McpTool(name = "create_temp_dir", description = """
        Create an exclusive temporary working directory and return its absolute path.
        No parameters.
        You should use it whenever a temporary workspace is needed, to effectively isolate side effects from \
        pulling temporary code repositories, downloading and extracting archives, staging intermediate files, \
        running one-off scripts, file conversion/transcoding, etc.
        IMPORTANT: Never store long-term files in this directory, \
        the temporary directory will be automatically destroyed when the program exits.""")
    public String createTempDir() {
        CreateTempDirResponse response = utilMcpService.createTempDir();
        if (response == null) {
            throw new IllegalStateException("create temp dir response is null");
        }
        if (StringUtils.isNotBlank(response.getError())) {
            throw new IllegalStateException(response.getError());
        }
        if (StringUtils.isBlank(response.getPath())) {
            throw new IllegalStateException("create temp dir path is blank");
        }
        return response.getPath();
    }

}
