package fun.fengwk.mmh.core.mcp;

import fun.fengwk.mmh.core.CoreTestApplication;
import fun.fengwk.mmh.core.service.browser.BrowserProperties;
import fun.fengwk.mmh.core.service.scrape.model.ScrapeResponse;
import fun.fengwk.mmh.core.service.UtilMcpService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * mmh_scrape 高并发压测 - 第二轮 (queue-offer-timeout-ms=15000)
 * 
 * @author fengwk
 */
@Slf4j
@SpringBootTest(classes = CoreTestApplication.class)
public class MmhScrapeStressTest {

    @Autowired
    private UtilMcpService utilMcpService;

    @Autowired
    private BrowserProperties browserProperties;

    private static final int ROUNDS = 3;
    private static final int CONCURRENT_PER_ROUND = 12;

    // 错误码定义
    private static final int SUCCESS = 0;
    private static final int TIMEOUT = -32001;  // queue offer timeout
    private static final int BUSY = -32002;     // worker pool busy
    private static final int OTHER_ERROR = -1;

    /**
     * 压测任务配置
     */
    static class ScrapeTask {
        final String name;
        final String url;
        final String format;
        final Boolean onlyMainContent;

        ScrapeTask(String name, String url, String format, Boolean onlyMainContent) {
            this.name = name;
            this.url = url;
            this.format = format;
            this.onlyMainContent = onlyMainContent;
        }
    }

    /**
     * 压测结果
     */
    static class ScrapeResult {
        final String name;
        final String url;
        final int errorCode;
        final long elapsedMs;
        final String errorMessage;

        ScrapeResult(String name, String url, int errorCode, long elapsedMs, String errorMessage) {
            this.name = name;
            this.url = url;
            this.errorCode = errorCode;
            this.elapsedMs = elapsedMs;
            this.errorMessage = errorMessage;
        }

        boolean isSuccess() {
            return errorCode == SUCCESS;
        }
    }

    /**
     * 轮次统计
     */
    static class RoundStats {
        final int round;
        final int total;
        int success;
        int timeout;
        int busy;
        int otherError;
        long maxElapsedMs;
        long minElapsedMs;

        RoundStats(int round, int total) {
            this.round = round;
            this.total = total;
            this.maxElapsedMs = 0;
            this.minElapsedMs = Long.MAX_VALUE;
        }

        void addResult(ScrapeResult result) {
            if (result.isSuccess()) {
                success++;
            } else if (result.errorCode == TIMEOUT) {
                timeout++;
            } else if (result.errorCode == BUSY) {
                busy++;
            } else {
                otherError++;
            }
            if (result.elapsedMs > maxElapsedMs) maxElapsedMs = result.elapsedMs;
            if (result.elapsedMs < minElapsedMs) minElapsedMs = result.elapsedMs;
        }

        String toSummaryLine() {
            if (minElapsedMs == Long.MAX_VALUE) minElapsedMs = 0;
            return String.format("Round %d: total=%d, success=%d, timeout=%d, busy=%d, other_error=%d, max_elapsed_ms=%d, min_elapsed_ms=%d",
                round, total, success, timeout, busy, otherError, maxElapsedMs, minElapsedMs);
        }
    }

    /**
     * URL 维度统计
     */
    static class UrlStats {
        final String url;
        int success;
        int failed;

        UrlStats(String url) {
            this.url = url;
        }

        void addSuccess() { success++; }
        void addFailed() { failed++; }

        double successRate() {
            int total = success + failed;
            return total > 0 ? (double) success / total * 100 : 0;
        }
    }

    private List<ScrapeTask> createTasks() {
        List<ScrapeTask> tasks = new ArrayList<>();
        
        // bbc-1..4 (4 个并发)
        for (int i = 1; i <= 4; i++) {
            tasks.add(new ScrapeTask("bbc-" + i, "https://www.bbc.com", "markdown", true));
        }
        
        // openai-1..3 (3 个并发)
        for (int i = 1; i <= 3; i++) {
            tasks.add(new ScrapeTask("openai-" + i, "https://openai.com", "markdown", true));
        }
        
        // wiki-1..3 (3 个并发)
        for (int i = 1; i <= 3; i++) {
            tasks.add(new ScrapeTask("wiki-" + i, "https://www.wikipedia.org", "markdown", true));
        }
        
        // example-1 (1 个)
        tasks.add(new ScrapeTask("example-1", "https://example.com", "markdown", false));
        
        // httpbin-1 (1 个)
        tasks.add(new ScrapeTask("httpbin-1", "https://httpbin.org/html", "markdown", false));
        
        assertThat(tasks).hasSize(CONCURRENT_PER_ROUND);
        return tasks;
    }

    private ScrapeResult executeTask(ScrapeTask task) {
        long startMs = System.currentTimeMillis();
        try {
            ScrapeResponse response = utilMcpService.scrape(
                task.url, 
                task.format, 
                task.onlyMainContent, 
                null, 
                "default"
            );
            long elapsedMs = System.currentTimeMillis() - startMs;
            
            // 检查响应是否表示错误
            if (response != null && response.getError() != null) {
                String errorMsg = response.getError();
                int errorCode = OTHER_ERROR;
                if (errorMsg != null) {
                    if (errorMsg.toLowerCase().contains("timeout") || errorMsg.toLowerCase().contains("queue")) {
                        errorCode = TIMEOUT;
                    } else if (errorMsg.toLowerCase().contains("busy")) {
                        errorCode = BUSY;
                    }
                }
                return new ScrapeResult(task.name, task.url, errorCode, elapsedMs, errorMsg);
            }
            
            return new ScrapeResult(task.name, task.url, SUCCESS, elapsedMs, null);
        } catch (Exception e) {
            long elapsedMs = System.currentTimeMillis() - startMs;
            String errorMsg = e.getMessage();
            int errorCode = OTHER_ERROR;
            if (errorMsg != null) {
                if (errorMsg.toLowerCase().contains("timeout") || errorMsg.toLowerCase().contains("queue")) {
                    errorCode = TIMEOUT;
                } else if (errorMsg.toLowerCase().contains("busy")) {
                    errorCode = BUSY;
                }
            }
            return new ScrapeResult(task.name, task.url, errorCode, elapsedMs, errorMsg);
        }
    }

    private String formatRate(double rate) {
        return String.format(Locale.ROOT, "%.2f", rate);
    }

    @Test
    @DisplayName("mmh_scrape 高并发压测 - 第二轮 (queue-offer-timeout-ms=15000)")
    public void stressTestRound2() throws Exception {
        log.info("=== mmh_scrape 高并发压测 - 第二轮 ===");
        log.info(
            "配置：queue-offer-timeout-ms={}, worker-pool-max-size-per-process={}, 并发={}, 轮数={}",
            browserProperties.getQueueOfferTimeoutMs(),
            browserProperties.getWorkerPoolMaxSizePerProcess(),
            CONCURRENT_PER_ROUND,
            ROUNDS
        );
        log.info("");

        List<ScrapeTask> tasks = createTasks();
        List<RoundStats> roundStatsList = new ArrayList<>();
        Map<String, UrlStats> urlStatsMap = new ConcurrentHashMap<>();
        
        // 错误样本收集
        List<ScrapeResult> timeoutSamples = new CopyOnWriteArrayList<>();
        List<ScrapeResult> busySamples = new CopyOnWriteArrayList<>();
        List<ScrapeResult> otherSamples = new CopyOnWriteArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_PER_ROUND);

        try {
            // 执行 3 轮
            for (int round = 1; round <= ROUNDS; round++) {
                log.info("执行第 {} 轮...", round);
                RoundStats stats = new RoundStats(round, CONCURRENT_PER_ROUND);
                
                List<Future<ScrapeResult>> futures = new ArrayList<>();
                
                // 并发提交所有任务
                for (ScrapeTask task : tasks) {
                    futures.add(executor.submit(() -> executeTask(task)));
                }
                
                // 收集结果
                for (Future<ScrapeResult> future : futures) {
                    ScrapeResult result = future.get(120, TimeUnit.SECONDS); // 单请求超时 120s
                    stats.addResult(result);
                    
                    // URL 统计
                    urlStatsMap.computeIfAbsent(result.url, k -> new UrlStats(result.url));
                    UrlStats urlStats = urlStatsMap.get(result.url);
                    if (result.isSuccess()) {
                        urlStats.addSuccess();
                    } else {
                        urlStats.addFailed();
                        // 收集错误样本
                        if (result.errorCode == TIMEOUT && timeoutSamples.size() < 2) {
                            timeoutSamples.add(result);
                        } else if (result.errorCode == BUSY && busySamples.size() < 2) {
                            busySamples.add(result);
                        } else if (result.errorCode == OTHER_ERROR && otherSamples.size() < 2) {
                            otherSamples.add(result);
                        }
                    }
                }
                
                roundStatsList.add(stats);
                log.info(stats.toSummaryLine());
            }
        } finally {
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }

        // 计算全局统计
        int totalRequests = ROUNDS * CONCURRENT_PER_ROUND;
        int totalSuccess = roundStatsList.stream().mapToInt(s -> s.success).sum();
        int totalTimeout = roundStatsList.stream().mapToInt(s -> s.timeout).sum();
        int totalBusy = roundStatsList.stream().mapToInt(s -> s.busy).sum();
        int totalOtherError = roundStatsList.stream().mapToInt(s -> s.otherError).sum();

        assertThat(totalSuccess + totalTimeout + totalBusy + totalOtherError).isEqualTo(totalRequests);

        double successRate = (double) totalSuccess / totalRequests * 100;
        double timeoutRate = (double) totalTimeout / totalRequests * 100;
        double busyRate = (double) totalBusy / totalRequests * 100;
        double otherErrorRate = (double) totalOtherError / totalRequests * 100;

        // 输出统计摘要
        log.info("");
        log.info("=== 统计摘要 ===");
        log.info("");
        log.info("## 每轮统计");
        for (RoundStats stats : roundStatsList) {
            log.info("- {}", stats.toSummaryLine());
        }
        
        log.info("");
        log.info("## 全局统计");
        log.info(
            "- total={}, success_rate={}%, timeout_rate={}%, busy_rate={}%, other_error_rate={}%",
            totalRequests,
            formatRate(successRate),
            formatRate(timeoutRate),
            formatRate(busyRate),
            formatRate(otherErrorRate)
        );
        
        log.info("");
        log.info("## URL 维度统计");
        urlStatsMap.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue().successRate(), a.getValue().successRate()))
            .forEach(entry -> {
                UrlStats us = entry.getValue();
                log.info(
                    "- {}: success={}, failed={}, success_rate={}%",
                    us.url,
                    us.success,
                    us.failed,
                    formatRate(us.successRate())
                );
            });
        
        log.info("");
        log.info("## 错误样本 (每类最多 2 条)");
        if (timeoutSamples.isEmpty() && busySamples.isEmpty() && otherSamples.isEmpty()) {
            log.info("- 无错误");
        } else {
            if (!timeoutSamples.isEmpty()) {
                log.info("- Timeout 错误:");
                for (ScrapeResult r : timeoutSamples) {
                    log.info("  - {} ({}): {}", r.name, r.url, r.errorMessage);
                }
            }
            if (!busySamples.isEmpty()) {
                log.info("- Busy 错误:");
                for (ScrapeResult r : busySamples) {
                    log.info("  - {} ({}): {}", r.name, r.url, r.errorMessage);
                }
            }
            if (!otherSamples.isEmpty()) {
                log.info("- Other 错误:");
                for (ScrapeResult r : otherSamples) {
                    log.info("  - {} ({}): {}", r.name, r.url, r.errorMessage);
                }
            }
        }
        
        log.info("");
        log.info("## 结论 (仅本轮观察)");
        if (totalTimeout == 0 && totalBusy == 0 && totalOtherError == 0) {
            log.info("- 100% 成功率，未触发 timeout/busy 错误");
            log.info(
                "- queue-offer-timeout-ms={} 配置下，worker 池能够处理 {} 并发请求",
                browserProperties.getQueueOfferTimeoutMs(),
                CONCURRENT_PER_ROUND
            );
            log.info("- 所有 URL 均正常响应，无特定 URL 失败模式");
        } else {
            if (totalTimeout > 0) {
                log.info("- 检测到 {} 次 timeout 错误，可能由于 worker 获取超时", totalTimeout);
            }
            if (totalBusy > 0) {
                log.info("- 检测到 {} 次 busy 错误，worker 池资源不足", totalBusy);
            }
            if (totalOtherError > 0) {
                log.info("- 检测到 {} 次其他错误，需进一步分析", totalOtherError);
            }
            log.info("- 建议增加 worker 池大小或调整 queue-offer-timeout-ms");
        }
        
        log.info("");
        log.info("=== 压测完成 ===");
    }
}
