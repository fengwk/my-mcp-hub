package fun.fengwk.mmh.core.facade.search.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fun.fengwk.convention4j.common.lang.StringUtils;
import fun.fengwk.mmh.core.facade.search.SearchFacade;
import fun.fengwk.mmh.core.facade.search.model.SearchRequest;
import fun.fengwk.mmh.core.facade.search.model.SearchResponse;
import fun.fengwk.mmh.core.facade.search.model.SearchResultItem;
import fun.fengwk.mmh.core.facade.search.searxng.SearxngClient;
import fun.fengwk.mmh.core.facade.search.searxng.SearxngClientResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author fengwk
 */
@Component
@RequiredArgsConstructor
public class SearchFacadeImpl implements SearchFacade {

    /**
     * Default max number of results to return.
     */
    private static final int DEFAULT_LIMIT = 10;

    private final SearxngClient searxngClient;
    private final ObjectMapper objectMapper;

    @Override
    public SearchResponse search(SearchRequest request) {
        // Validate request and normalize defaults.
        if (request == null || StringUtils.isBlank(request.getQuery())) {
            return SearchResponse.builder()
                .statusCode(400)
                .error("query is blank")
                .build();
        }

        int limit = request.getLimit() == null || request.getLimit() <= 0
            ? DEFAULT_LIMIT
            : request.getLimit();
        int page = request.getPage() == null || request.getPage() <= 0
            ? 1
            : request.getPage();

        Map<String, String> params = new LinkedHashMap<>();
        params.put("q", request.getQuery());
        if (StringUtils.isNotBlank(request.getTimeRange())) {
            params.put("time_range", request.getTimeRange());
        }
        params.put("pageno", String.valueOf(page));

        SearxngClientResponse response = searxngClient.search(params);
        SearchResponse.SearchResponseBuilder builder = SearchResponse.builder()
            .statusCode(response.getStatusCode());
        if (response.hasError()) {
            return builder.error(response.getError().getMessage()).build();
        }
        if (StringUtils.isBlank(response.getBody())) {
            return builder.error("empty response body").build();
        }

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            builder.query(textOrNull(root.get("query")));
            JsonNode totalNode = root.get("number_of_results");
            if (totalNode != null && totalNode.isNumber()) {
                builder.numberOfResults(totalNode.intValue());
            }

            List<SearchResultItem> items = new ArrayList<>();
            JsonNode results = root.get("results");
            if (results != null && results.isArray()) {
                for (JsonNode itemNode : results) {
                    SearchResultItem item = SearchResultItem.builder()
                        .title(textOrNull(itemNode.get("title")))
                        .url(textOrNull(itemNode.get("url")))
                        .content(textOrNull(itemNode.get("content")))
                        .build();
                    items.add(item);
                    if (items.size() >= limit) {
                        break;
                    }
                }
            }
            builder.results(items);
            return builder.build();
        } catch (Exception ex) {
            return builder.error(ex.getMessage()).build();
        }
    }

    /**
     * Extract string value from JSON node.
     */
    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }

}
