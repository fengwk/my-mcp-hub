package fun.fengwk.mmh.core.service.skill.support;

import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 SKILL.md 文件，提取 YAML frontmatter 和 Markdown 内容
 *
 * @author fengwk
 */
@Slf4j
public class SkillMarkdownParser {

    /**
     * 匹配 YAML frontmatter 的正则表达式
     * 格式：以 --- 开头，以 --- 结尾，中间是 YAML 内容
     */
    private static final Pattern FRONTMATTER_PATTERN =
        Pattern.compile("^---\\s*\\n(.*?)\\n---\\s*\\n?", Pattern.DOTALL);

    private SkillMarkdownParser() {}

    /**
     * 解析结果
     *
     * @param name        Skill 名称
     * @param description Skill 描述
     * @param content     Markdown 正文部分
     */
    public record ParseResult(String name, String description, String content) {}

    /**
     * 解析 SKILL.md 文件内容
     *
     * @param markdown 原始 Markdown 内容
     * @return 解析结果，包含 name、description 和正文
     */
    public static ParseResult parse(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return new ParseResult(null, null, "");
        }

        Matcher matcher = FRONTMATTER_PATTERN.matcher(markdown);
        if (matcher.find()) {
            String yamlContent = matcher.group(1);
            String bodyContent = markdown.substring(matcher.end()).trim();
            Map<String, String> metadata = parseYamlMetadata(yamlContent);
            return new ParseResult(
                metadata.get("name"),
                metadata.get("description"),
                bodyContent
            );
        }

        // 没有 frontmatter，整个内容作为正文
        return new ParseResult(null, null, markdown.trim());
    }

    private static Map<String, String> parseYamlMetadata(String yamlContent) {
        try {
            Yaml yaml = new Yaml();
            Map<String, Object> map = yaml.load(yamlContent);
            if (map == null) {
                return Map.of();
            }

            return Map.of(
                "name", map.get("name") != null ? String.valueOf(map.get("name")) : "",
                "description", map.get("description") != null ? String.valueOf(map.get("description")) : ""
            );
        } catch (Exception e) {
            log.warn("Failed to parse YAML frontmatter: {}", e.getMessage());
            return Map.of();
        }
    }

}
