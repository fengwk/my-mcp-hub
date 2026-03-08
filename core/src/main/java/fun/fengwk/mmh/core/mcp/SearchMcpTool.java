package fun.fengwk.mmh.core.mcp;

import fun.fengwk.convention4j.common.lang.StringUtils;
import fun.fengwk.mmh.core.facade.search.model.SearchResponse;
import fun.fengwk.mmh.core.service.UtilMcpService;
import lombok.RequiredArgsConstructor;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Annotation-based MCP tool for search.
 *
 * @author fengwk
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "mmh.search", name = "mcp-tool-enabled", havingValue = "true")
public class SearchMcpTool {

    private final UtilMcpService utilMcpService;

    @McpTool(name = "search", description = """
        Search web pages and return the normalized search response as JSON text.
        Success responses contain the serialized search response body; failures are returned as MCP errors.""")
    public SearchResponse search(
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
        if (response == null) {
            throw new IllegalStateException("search response is null");
        }
        if (StringUtils.isNotBlank(response.getError())) {
            throw new IllegalStateException(response.getError());
        }
        return response;
    }

}
