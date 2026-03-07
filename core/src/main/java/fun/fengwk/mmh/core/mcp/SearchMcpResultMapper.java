package fun.fengwk.mmh.core.mcp;

import fun.fengwk.convention4j.common.lang.StringUtils;
import fun.fengwk.mmh.core.facade.search.model.SearchResponse;
import fun.fengwk.mmh.core.facade.search.model.SearchResultItem;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Search result mapper.
 *
 * @author fengwk
 */
@Component
@RequiredArgsConstructor
public class SearchMcpResultMapper {

    private final McpFormatter mcpFormatter;

    public McpSchema.CallToolResult toResult(SearchResponse response) {
        if (response == null) {
            return McpToolSupport.errorResult("search response is null");
        }

        String text = mcpFormatter.format("mmh_search_result.ftl", response);
        boolean hasError = StringUtils.isNotBlank(response.getError()) || text.startsWith("format error:");

        McpSchema.CallToolResult.Builder builder = McpSchema.CallToolResult.builder()
            .addTextContent(text)
            .isError(hasError);
        if (!hasError) {
            builder.structuredContent(toStructuredContent(response));
        }
        return builder.build();
    }

    private Map<String, Object> toStructuredContent(SearchResponse response) {
        Map<String, Object> structuredContent = new LinkedHashMap<>();
        structuredContent.put("statusCode", response.getStatusCode());
        if (StringUtils.isNotBlank(response.getQuery())) {
            structuredContent.put("query", response.getQuery());
        }
        if (response.getNumberOfResults() != null) {
            structuredContent.put("numberOfResults", response.getNumberOfResults());
        }
        structuredContent.put("results", toStructuredResults(response.getResults()));
        return structuredContent;
    }

    private List<Map<String, Object>> toStructuredResults(List<SearchResultItem> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> structuredResults = new ArrayList<>(results.size());
        for (SearchResultItem result : results) {
            Map<String, Object> resultItem = new LinkedHashMap<>();
            if (StringUtils.isNotBlank(result.getTitle())) {
                resultItem.put("title", result.getTitle());
            }
            if (StringUtils.isNotBlank(result.getUrl())) {
                resultItem.put("url", result.getUrl());
            }
            if (StringUtils.isNotBlank(result.getContent())) {
                resultItem.put("content", result.getContent());
            }
            structuredResults.add(resultItem);
        }
        return structuredResults;
    }

}
