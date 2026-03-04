package fun.fengwk.mmh.core.service.skill.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 完整的 Skill 对象
 *
 * @author fengwk
 */
@Data
@Builder
public class Skill {

    /**
     * Skill 名称（来自 YAML frontmatter）
     */
    private String name;

    /**
     * Skill 描述（来自 YAML frontmatter）
     */
    private String description;

    /**
     * Skill 内容（Markdown 正文部分）
     */
    private String content;

    /**
     * Skill 所在目录的绝对路径（平台原生格式）
     * Unix:    /home/user/.opencode/skills/git-release
     * Windows: C:/Users/user/.opencode/skills/git-release
     */
    private String basePath;

    /**
     * 目录下的文件列表（采样）
     */
    private List<String> files;

}
