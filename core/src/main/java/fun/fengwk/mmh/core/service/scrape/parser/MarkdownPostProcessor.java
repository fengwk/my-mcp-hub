package fun.fengwk.mmh.core.service.scrape.parser;

import fun.fengwk.convention4j.common.lang.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Post-process markdown output.
 *
 * @author fengwk
 */
@Component
public class MarkdownPostProcessor {

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

    private String stripTrailingSpaces(String line) {
        int end = line.length();
        while (end > 0 && Character.isWhitespace(line.charAt(end - 1)) && line.charAt(end - 1) != '\n') {
            end--;
        }
        return line.substring(0, end);
    }

}
