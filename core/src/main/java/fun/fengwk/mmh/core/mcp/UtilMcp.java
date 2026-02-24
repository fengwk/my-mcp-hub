package fun.fengwk.mmh.core.mcp;

import fun.fengwk.mmh.core.facade.search.model.SearchResponse;
import fun.fengwk.mmh.core.service.UtilMcpService;
import fun.fengwk.mmh.core.service.scrape.model.ScrapeResponse;
import fun.fengwk.mmh.core.utils.StringToolCallResultConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * @author fengwk
 */
@Component
@RequiredArgsConstructor
public class UtilMcp {

    private final UtilMcpService utilMcpService;
    private final McpFormatter mcpFormatter;

    @Tool(name = "search",
        description = """
            Search web pages and return formatted results.
            Return format: result list with title, url, and content excerpt; or 'No results.'; or an error message.""",
        resultConverter = StringToolCallResultConverter.class)
    public String search(
        @ToolParam(description = """
            Query syntax:
            - Basics: plain keywords, e.g. spring ai.
            - Advanced: site:domain, e.g. site:github.com spring ai.
            - Advanced: -term, e.g. spring ai -tutorial.
            - Advanced: "phrase", e.g. "spring ai".
            - Advanced: intitle:word, e.g. intitle:spring ai.""") String query,
        @ToolParam(description = "max results, default 10", required = false) Integer limit,
        @ToolParam(description = "time range: day/week/month/year, default no filter", required = false) String timeRange,
        @ToolParam(description = "page number, default 1", required = false) Integer page
    ) {
        SearchResponse response = utilMcpService.search(query, limit, timeRange, page);
        return mcpFormatter.format("mmh_search_result.ftl", response);
    }

    @Tool(name = "create_temp_dir",
        description = """
            Create an exclusive temporary working directory and return its absolute path.
            No parameters.
            You should use it whenever a temporary workspace is needed, to effectively isolate side effects from \
            pulling temporary code repositories, downloading and extracting archives, staging intermediate files, \
            running one-off scripts, file conversion/transcoding, etc.
            IMPORTANT: Never store long-term files in this directory, \
            the temporary directory will be automatically destroyed when the program exits.""",
        resultConverter = StringToolCallResultConverter.class)
    public String createTempDir() {
        return utilMcpService.createTempDir();
    }

    @Tool(name = "scrape",
        description = """
            Scrape a single web page and return one output format.
            Supported format values: markdown, html, links, screenshot, fullscreenshot.
            Returns extracted content, links, or screenshot base64. Returns error message when failed.""",
        resultConverter = StringToolCallResultConverter.class)
    public String scrape(
        @ToolParam(description = "target page url (http/https). Provide a fully qualified URL") String url,
        @ToolParam(description = "output format, default markdown. Choose one of: markdown/html/links/screenshot/fullscreenshot", required = false) String format,
        @ToolParam(description = "profile mode, default default. Choose one of: default/master", required = false) String profileMode,
        @ToolParam(description = "whether to keep only main content, default false. Set true to focus on main content only", required = false) Boolean onlyMainContent,
        @ToolParam(description = "wait in milliseconds after navigation, default 0. You can estimate a proper waitFor by checking the returned elapsedMs", required = false) Integer waitFor
    ) {
        ScrapeResponse response = utilMcpService.scrape(url, format, onlyMainContent, waitFor, profileMode);
        return mcpFormatter.format("mmh_scrape_result.ftl", response);
    }

//    @Tool(name = "workflow_trace",
//        description = """
//            Trace the workflow execution status for the current round.
//            You runs in rounds, and each round either invokes tools or outputs answer text.
//            You must call this tool at the start of every non-final round.
//            Do not call it in the final answer summary.
//            Provide status, previous-round conclusion/blocker/hypothesis, and brief current-round actions.""",
//        resultConverter = StringToolCallResultConverter.class)
//    public String workflowTrace(
//        @ToolParam(description = "current workflow state label") String status,
//        @ToolParam(description = "previous round outcome, conclusion, blocker, hypothesis, or uncertainty") String progress,
//        @ToolParam(description = "brief descriptions of current round actions, one item per action") List<String> actions
//    ) {
//        return mcpFormatter.format("mmh_workflow_trace_result.ftl", Map.of(
//            "status", status,
//            "progress", progress,
//            "actions", actions == null ? List.of() : actions
//        ));
//    }

}
