package fun.fengwk.mmh.core.mcp;

import fun.fengwk.mmh.core.facade.search.model.SearchResponse;
import fun.fengwk.mmh.core.service.UtilMcpService;
import fun.fengwk.mmh.core.service.model.CreateTempDirResponse;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.RequiredArgsConstructor;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * Annotation-based MCP tools for simple use cases.
 *
 * @author fengwk
 */
@Component
@RequiredArgsConstructor
public class AnnotatedMcpTools {

    private final UtilMcpService utilMcpService;
    private final SearchMcpResultMapper searchMcpResultMapper;
    private final TempDirMcpResultMapper tempDirMcpResultMapper;

    @McpTool(name = "search", description = """
        Search web pages and return formatted results.
        Output includes human-readable text plus structuredContent with status, paging, and result items.""")
    public CallToolResult search(
        @McpToolParam(description = """
            Query syntax:
            - Basics: plain keywords, e.g. spring ai.
            - Advanced: site:domain, e.g. site:github.com spring ai.
            - Advanced: -term, e.g. spring ai -tutorial.
            - Advanced: \"phrase\", e.g. \"spring ai\".
            - Advanced: intitle:word, e.g. intitle:spring ai.""") String query,
        @McpToolParam(description = "max results, default 10", required = false) Integer limit,
        @McpToolParam(description = "time range: day/week/month/year, default no filter", required = false) String timeRange,
        @McpToolParam(description = "page number, default 1", required = false) Integer page
    ) {
        SearchResponse response = utilMcpService.search(query, limit, timeRange, page);
        return searchMcpResultMapper.toResult(response);
    }

    @McpTool(name = "create_temp_dir", description = """
        Create an exclusive temporary working directory and return its absolute path.
        No parameters.
        You should use it whenever a temporary workspace is needed, to effectively isolate side effects from \
        pulling temporary code repositories, downloading and extracting archives, staging intermediate files, \
        running one-off scripts, file conversion/transcoding, etc.
        IMPORTANT: Never store long-term files in this directory, \
        the temporary directory will be automatically destroyed when the program exits.""")
    public CallToolResult createTempDir() {
        CreateTempDirResponse response = utilMcpService.createTempDir();
        return tempDirMcpResultMapper.toResult(response);
    }

}
