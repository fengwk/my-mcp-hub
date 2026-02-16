package fun.fengwk.mmh.core.facade.search.searxng;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Raw response from SearXNG.
 *
 * @author fengwk
 */
@Data
@Builder
public class SearxngClientResponse {

    private int statusCode;
    private Map<String, List<String>> headers;
    private String body;
    private Throwable error;

    public boolean hasError() {
        return error != null;
    }

}
