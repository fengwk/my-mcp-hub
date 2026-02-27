package fun.fengwk.mmh.core.service.scrape.parser;

import fun.fengwk.convention4j.common.lang.StringUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Post-process markdown output.
 *
 * @author fengwk
 */
@Component
public class MarkdownPostProcessor {

    private static final Pattern ORDERED_LIST_ITEM_PATTERN = Pattern.compile("^(\\s*)(\\d+)\\.\\s+(.*)$");

    public String process(String markdown) {
        if (StringUtils.isBlank(markdown)) {
            return "";
        }
        String normalized = markdown.replace("\r\n", "\n")
            .replace("\uFEFF", "")
            .replace("\u200B", "")
            .replace("\u2060", "");
        normalized = removeEmptyLinks(normalized);
        normalized = compactAdjacentImages(normalized);
        normalized = mergeAdjacentCodeBlocks(normalized);
        normalized = normalizeOrderedListNumbers(normalized);
        StringBuilder builder = new StringBuilder();
        boolean previousBlank = true;
        for (String line : normalized.split("\n", -1)) {
            String trimmedLine = stripTrailingSpaces(line);
            if (trimmedLine.isBlank()) {
                if (!previousBlank) {
                    builder.append("\n");
                }
                previousBlank = true;
                continue;
            }
            builder.append(trimmedLine).append("\n");
            previousBlank = false;
        }
        return builder.toString().trim();
    }

    private String removeEmptyLinks(String input) {
        String cleaned = input.replaceAll("[\\t ]*(?<!\\!)\\[\\s*\\]\\([^)]*\\)[\\t ]*", " ");
        return cleaned.replaceAll(" {2,}", " ");
    }

    private String compactAdjacentImages(String input) {
        return input.replaceAll("\\)\\s+!\\[", ")![");
    }

    private String mergeAdjacentCodeBlocks(String input) {
        String[] lines = input.split("\n", -1);
        StringBuilder merged = new StringBuilder();
        int index = 0;
        while (index < lines.length) {
            String line = lines[index];
            if (!isFenceLine(line)) {
                appendLine(merged, line);
                index++;
                continue;
            }

            int closeIndex = findFenceClose(lines, index + 1);
            if (closeIndex < 0) {
                appendLine(merged, line);
                index++;
                continue;
            }

            String fence = line.trim();
            StringBuilder content = new StringBuilder();
            appendCodeBlockContent(content, lines, index + 1, closeIndex);
            int nextIndex = closeIndex + 1;

            while (true) {
                int blankStart = nextIndex;
                while (nextIndex < lines.length && lines[nextIndex].trim().isEmpty()) {
                    nextIndex++;
                }
                int blankCount = nextIndex - blankStart;

                if (blankCount > 1 || nextIndex >= lines.length || !fence.equals(lines[nextIndex].trim())) {
                    nextIndex = blankStart;
                    break;
                }

                int nextClose = findFenceClose(lines, nextIndex + 1);
                if (nextClose < 0) {
                    nextIndex = blankStart;
                    break;
                }

                if (content.length() > 0 && content.charAt(content.length() - 1) != '\n') {
                    content.append("\n");
                }
                appendCodeBlockContent(content, lines, nextIndex + 1, nextClose);
                nextIndex = nextClose + 1;
            }

            appendLine(merged, fence);
            if (content.length() > 0) {
                merged.append(content);
                if (content.charAt(content.length() - 1) != '\n') {
                    merged.append("\n");
                }
            }
            appendLine(merged, "```");

            if (nextIndex == closeIndex + 1) {
                index = closeIndex + 1;
            } else {
                index = nextIndex;
            }
        }

        return merged.toString();
    }

    private int findFenceClose(String[] lines, int start) {
        for (int i = start; i < lines.length; i++) {
            if (isFenceLine(lines[i])) {
                return i;
            }
        }
        return -1;
    }

    private boolean isFenceLine(String line) {
        return line != null && line.trim().startsWith("```");
    }

    private void appendCodeBlockContent(StringBuilder content, String[] lines, int start, int endExclusive) {
        for (int i = start; i < endExclusive; i++) {
            content.append(lines[i]);
            if (i < endExclusive - 1) {
                content.append("\n");
            }
        }
    }

    private String normalizeOrderedListNumbers(String input) {
        StringBuilder normalized = new StringBuilder();
        Map<Integer, Integer> countersByIndent = new HashMap<>();
        boolean inCodeBlock = false;

        for (String line : input.split("\n", -1)) {
            String trimmed = line.trim();
            if (isFenceLine(line)) {
                inCodeBlock = !inCodeBlock;
                appendLine(normalized, line);
                continue;
            }

            if (inCodeBlock) {
                appendLine(normalized, line);
                continue;
            }

            Matcher matcher = ORDERED_LIST_ITEM_PATTERN.matcher(line);
            if (matcher.matches()) {
                int indent = matcher.group(1).length();
                clearDeeperIndentCounters(countersByIndent, indent);
                int normalizedIndex = countersByIndent.getOrDefault(indent, 0) + 1;
                countersByIndent.put(indent, normalizedIndex);
                appendLine(normalized, matcher.group(1) + normalizedIndex + ". " + matcher.group(3));
                continue;
            }

            if (!trimmed.isEmpty() && !line.startsWith("  ")) {
                countersByIndent.clear();
            }
            appendLine(normalized, line);
        }

        return normalized.toString();
    }

    private void clearDeeperIndentCounters(Map<Integer, Integer> countersByIndent, int indent) {
        countersByIndent.keySet().removeIf(key -> key > indent);
    }

    private void appendLine(StringBuilder builder, String line) {
        builder.append(line).append("\n");
    }

    private String stripTrailingSpaces(String line) {
        int end = line.length();
        while (end > 0 && Character.isWhitespace(line.charAt(end - 1)) && line.charAt(end - 1) != '\n') {
            end--;
        }
        return line.substring(0, end);
    }

}
