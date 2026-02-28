package fun.fengwk.mmh.core.mcp;

import fun.fengwk.mmh.core.CoreTestApplication;
import fun.fengwk.mmh.core.service.scrape.model.ScrapeResponse;
import fun.fengwk.mmh.core.service.UtilMcpService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * mmh_scrape service 层功能穷举验证 (75 cases)
 */
@Slf4j
@SpringBootTest(classes = CoreTestApplication.class)
public class MmhScrapeExhaustiveTest {

    private static final byte[] LOCAL_PNG_BYTES = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

    @Autowired
    private UtilMcpService utilMcpService;

    static class CaseResult {
        final String caseId;
        final String group;
        final String params;
        final boolean pass;
        final String errorType;
        final long elapsedMs;
        final String briefReason;

        CaseResult(String caseId, String group, String params, boolean pass, String errorType, long elapsedMs, String briefReason) {
            this.caseId = caseId;
            this.group = group;
            this.params = params;
            this.pass = pass;
            this.errorType = errorType;
            this.elapsedMs = elapsedMs;
            this.briefReason = briefReason;
        }
    }

    static class GroupStats {
        final String group;
        int total, pass, fail;
        GroupStats(String group) { this.group = group; }
        void add(boolean p) { total++; if (p) pass++; else fail++; }
    }

    private CaseResult run(String caseId, String group, String url, String format, String profileMode, Boolean onlyMainContent) {
        long start = System.currentTimeMillis();
        String params = String.format("url=%s, format=%s, profileMode=%s, onlyMainContent=%s", url, format, profileMode, onlyMainContent);
        try {
            ScrapeResponse r = utilMcpService.scrape(url, format, onlyMainContent, null, profileMode);
            long elapsed = System.currentTimeMillis() - start;
            if (r == null) return new CaseResult(caseId, group, params, false, "unexpected_error", elapsed, "response is null");
            if (r.getError() != null && !r.getError().isEmpty()) {
                String err = r.getError().toLowerCase();
                String expectErr = expectError(caseId);
                if (expectErr != null) {
                    boolean match = (expectErr.equals("invalid_format") && err.contains("unsupported format"))
                        || (expectErr.equals("invalid_profile") && err.contains("unsupported profilemode"))
                        || (expectErr.equals("blank_url") && err.contains("blank"))
                        || (expectErr.equals("ftp_url") && err.contains("unsupported url protocol"));
                    if (match) return new CaseResult(caseId, group, params, true, null, elapsed, "expected: " + r.getError());
                    return new CaseResult(caseId, group, params, false, "validation_mismatch", elapsed, "expect " + expectErr + ", got: " + r.getError());
                }
                return new CaseResult(caseId, group, params, false, "unexpected_error", elapsed, r.getError());
            }
            boolean ok = validateSuccess(caseId, r);
            return new CaseResult(caseId, group, params, ok, ok ? null : "validation_mismatch", elapsed, ok ? "success" : "content mismatch");
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            String expectErr = expectError(caseId);
            if (expectErr != null) {
                String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                boolean match = (expectErr.equals("invalid_format") && msg.contains("unsupported format"))
                    || (expectErr.equals("invalid_profile") && msg.contains("unsupported profilemode"))
                    || (expectErr.equals("blank_url") && msg.contains("blank"))
                    || (expectErr.equals("ftp_url") && msg.contains("unsupported url protocol"));
                if (match) return new CaseResult(caseId, group, params, true, null, elapsed, "expected: " + e.getMessage());
            }
            return new CaseResult(caseId, group, params, false, "unexpected_error", elapsed, e.getMessage());
        }
    }

    private String expectError(String caseId) {
        if (caseId.startsWith("C")) return "invalid_format";
        if (caseId.startsWith("D")) return "invalid_profile";
        if ("E1".equals(caseId)) return "blank_url";
        if ("E2".equals(caseId)) return "ftp_url";
        return null;
    }

    private boolean validateSuccess(String caseId, ScrapeResponse r) {
        if (caseId.startsWith("F") || caseId.startsWith("B")) {
            return r.getScreenshotBase64() != null && !r.getScreenshotBase64().isEmpty()
                && r.getScreenshotMime() != null && r.getScreenshotMime().contains("image/png");
        }
        if (caseId.startsWith("A")) {
            String fmt = getFormatFromCaseId(caseId);
            if ("links".equals(fmt)) {
                return r.getFormat() != null && r.getFormat().equalsIgnoreCase("links")
                    && r.getLinks() != null && !r.getLinks().isEmpty();
            }
            return r.getContent() != null && !r.getContent().isEmpty() && r.getContent().contains("Example Domain");
        }
        return true;
    }

    private String getFormatFromCaseId(String caseId) {
        if (caseId.startsWith("A") && caseId.length() >= 2) {
            int fi = Character.getNumericValue(caseId.charAt(1));
            return switch (fi) { case 0 -> null; case 1 -> "markdown"; case 2 -> "html"; case 3 -> "links"; default -> null; };
        }
        return null;
    }

    private HttpServer startDirectMediaServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/image.png", this::handleImageRequest);
        server.start();
        return server;
    }

    private void handleImageRequest(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "image/png");
        exchange.sendResponseHeaders(200, LOCAL_PNG_BYTES.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(LOCAL_PNG_BYTES);
        }
    }

    private String buildLocalImageUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/image.png";
    }

    @Test
    @DisplayName("mmh_scrape 功能穷举验证 (75 cases)")
    public void exhaustiveTest() throws IOException {
        HttpServer directMediaServer = startDirectMediaServer();
        String directMediaUrl = buildLocalImageUrl(directMediaServer);
        log.info("=== mmh_scrape 功能穷举验证 ===");
        try {
            List<CaseResult> results = new ArrayList<>();
            Map<String, GroupStats> stats = new HashMap<>();
            for (String g : List.of("A", "B", "C", "D", "E", "F")) {
                stats.put(g, new GroupStats(g));
            }

            String[] formats = {null, "markdown", "html", "links"};
            String[] profiles = {null, "default", "master"};
            Boolean[] onlyMains = {null, false, true};

            // A: normal-page (36)
            log.info("执行 A 组：normal-page 笛卡尔集 (36 cases)...");
            int c = 0;
            for (int fi = 0; fi < 4; fi++) {
                for (int pi = 0; pi < 3; pi++) {
                    for (int oi = 0; oi < 3; oi++) {
                        c++;
                        String cid = "A" + fi + pi + oi;
                        CaseResult r = run(cid, "A", "https://example.com", formats[fi], profiles[pi], onlyMains[oi]);
                        results.add(r);
                        stats.get("A").add(r.pass);
                        log.info("  [{}] {} - {}", r.pass ? "PASS" : "FAIL", cid, r.briefReason);
                    }
                }
            }
            log.info("A 组完成：{}/{} passed\n", stats.get("A").pass, stats.get("A").total);

            // B: direct-media (14)
            log.info("执行 B 组：direct-media 组合 (14 cases)...");
            log.info("B 组使用本地 direct-media 服务，url={}", directMediaUrl);
            c = 0;
            for (int fi = 0; fi < 4; fi++) {
                for (int pi = 0; pi < 3; pi++) {
                    c++;
                    String cid = "B" + String.format("%02d", c);
                    CaseResult r = run(cid, "B", directMediaUrl, formats[fi], profiles[pi], null);
                    results.add(r);
                    stats.get("B").add(r.pass);
                    log.info("  [{}] {} - {}", r.pass ? "PASS" : "FAIL", cid, r.briefReason);
                }
            }
            CaseResult r13 = run("B13", "B", directMediaUrl, "markdown", "default", false);
            results.add(r13);
            stats.get("B").add(r13.pass);
            log.info("  [{}] B13 - {}", r13.pass ? "PASS" : "FAIL", r13.briefReason);
            CaseResult r14 = run("B14", "B", directMediaUrl, "markdown", "default", true);
            results.add(r14);
            stats.get("B").add(r14.pass);
            log.info("  [{}] B14 - {}", r14.pass ? "PASS" : "FAIL", r14.briefReason);
            log.info("B 组完成：{}/{} passed\n", stats.get("B").pass, stats.get("B").total);

            // C: invalid-format (9)
            log.info("执行 C 组：invalid-format (9 cases)...");
            c = 0;
            for (int pi = 0; pi < 3; pi++) {
                for (int oi = 0; oi < 3; oi++) {
                    c++;
                    String cid = "C" + pi + oi;
                    CaseResult r = run(cid, "C", "https://example.com", "pdf", profiles[pi], onlyMains[oi]);
                    results.add(r);
                    stats.get("C").add(r.pass);
                    log.info("  [{}] {} - {}", r.pass ? "PASS" : "FAIL", cid, r.briefReason);
                }
            }
            log.info("C 组完成：{}/{} passed\n", stats.get("C").pass, stats.get("C").total);

            // D: invalid-profile (12)
            log.info("执行 D 组：invalid-profile (12 cases)...");
            c = 0;
            for (int fi = 0; fi < 4; fi++) {
                for (int oi = 0; oi < 3; oi++) {
                    c++;
                    String cid = "D" + fi + oi;
                    CaseResult r = run(cid, "D", "https://example.com", formats[fi], "unknown", onlyMains[oi]);
                    results.add(r);
                    stats.get("D").add(r.pass);
                    log.info("  [{}] {} - {}", r.pass ? "PASS" : "FAIL", cid, r.briefReason);
                }
            }
            log.info("D 组完成：{}/{} passed\n", stats.get("D").pass, stats.get("D").total);

            // E: invalid-url (2)
            log.info("执行 E 组：invalid-url (2 cases)...");
            CaseResult rE1 = run("E1", "E", " ", null, null, null);
            results.add(rE1);
            stats.get("E").add(rE1.pass);
            log.info("  [{}] E1 - {}", rE1.pass ? "PASS" : "FAIL", rE1.briefReason);
            CaseResult rE2 = run("E2", "E", "ftp://example.com", null, null, null);
            results.add(rE2);
            stats.get("E").add(rE2.pass);
            log.info("  [{}] E2 - {}", rE2.pass ? "PASS" : "FAIL", rE2.briefReason);
            log.info("E 组完成：{}/{} passed\n", stats.get("E").pass, stats.get("E").total);

            // F: screenshot smoke (2)
            log.info("执行 F 组：screenshot smoke (2 cases)...");
            CaseResult rF1 = run("F1", "F", "https://example.com", "screenshot", "default", null);
            results.add(rF1);
            stats.get("F").add(rF1.pass);
            log.info("  [{}] F1 - {}", rF1.pass ? "PASS" : "FAIL", rF1.briefReason);
            CaseResult rF2 = run("F2", "F", "https://example.com", "fullscreenshot", "default", null);
            results.add(rF2);
            stats.get("F").add(rF2.pass);
            log.info("  [{}] F2 - {}", rF2.pass ? "PASS" : "FAIL", rF2.briefReason);
            log.info("F 组完成：{}/{} passed\n", stats.get("F").pass, stats.get("F").total);

            // Summary
            int total = results.size();
            int pass = (int) results.stream().filter(r -> r.pass).count();
            int fail = total - pass;
            double passRate = (double) pass / total * 100;

            log.info("===========================================");
            log.info("=== 统计摘要 ===");
            log.info("===========================================");
            log.info("\n## 总体统计");
            log.info("- total={}, pass={}, fail={}, pass_rate={:.2f}%", total, pass, fail, passRate);
            log.info("\n## 分组统计");
            for (String g : List.of("A", "B", "C", "D", "E", "F")) {
                GroupStats s = stats.get(g);
                log.info("- 组{}: total={}, pass={}, fail={}", g, s.total, s.pass, s.fail);
            }

            Map<String, Integer> errStats = new HashMap<>();
            for (CaseResult r : results) {
                if (!r.pass && r.errorType != null) {
                    errStats.merge(r.errorType, 1, Integer::sum);
                }
            }
            log.info("\n## 错误类型统计");
            if (errStats.isEmpty()) {
                log.info("- 无错误");
            } else {
                for (Map.Entry<String, Integer> e : errStats.entrySet()) {
                    log.info("- {}: {}", e.getKey(), e.getValue());
                }
            }

            List<CaseResult> failed = results.stream().filter(r -> !r.pass).limit(12).toList();
            log.info("\n## 失败样本 (最多 12 条)");
            if (failed.isEmpty()) {
                log.info("- 无失败用例");
            } else {
                for (CaseResult r : failed) {
                    log.info("- [{}] {}: params={}, reason={}", r.group, r.caseId, r.params, r.briefReason);
                }
            }

            log.info("\n===========================================");
            log.info("## 结论");
            log.info("===========================================");
            List<String> conclusions = new ArrayList<>();
            conclusions.add(stats.get("A").fail == 0 ? "✓ normal-page 功能已穷举验证通过 (service format: null/markdown/html/links, profileMode: null/default/master, onlyMainContent: null/false/true)"
                : "✗ normal-page 功能存在 " + stats.get("A").fail + " 个失败用例");
            conclusions.add(stats.get("B").fail == 0 ? "✓ direct-media 功能已穷举验证通过 (直接返回媒体数据 URI)"
                : "✗ direct-media 功能存在 " + stats.get("B").fail + " 个失败用例");
            conclusions.add(stats.get("C").fail == 0 ? "✓ invalid-format 错误处理已验证通过 (format=pdf 返回 unsupported format 错误)"
                : "✗ invalid-format 错误处理存在 " + stats.get("C").fail + " 个失败用例");
            conclusions.add(stats.get("D").fail == 0 ? "✓ invalid-profile 错误处理已验证通过 (profileMode=unknown 返回 unsupported profileMode 错误)"
                : "✗ invalid-profile 错误处理存在 " + stats.get("D").fail + " 个失败用例");
            conclusions.add(stats.get("E").fail == 0 ? "✓ invalid-url 错误处理已验证通过 (blank url 和 ftp 协议均返回对应错误)"
                : "✗ invalid-url 错误处理存在 " + stats.get("E").fail + " 个失败用例");
            conclusions.add(stats.get("F").fail == 0 ? "✓ screenshot/fullscreenshot 功能 smoke 测试通过"
                : "✗ screenshot/fullscreenshot 功能存在 " + stats.get("F").fail + " 个失败用例");
            if (passRate < 100) {
                conclusions.add("⚠ 总体通过率 " + String.format("%.2f", passRate) + "%，存在 " + fail + " 个失败用例");
            }
            for (String ccl : conclusions) {
                log.info(ccl);
            }
            log.info("\n=== 穷举验证完成 ===");

            assertThat(fail).withFailMessage("有 %d 个测试用例失败", fail).isEqualTo(0);
        } finally {
            directMediaServer.stop(0);
        }
    }
}
