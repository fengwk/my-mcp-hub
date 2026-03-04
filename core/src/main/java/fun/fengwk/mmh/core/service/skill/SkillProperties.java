package fun.fengwk.mmh.core.service.skill;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Skill 配置属性
 *
 * @author fengwk
 */
@Data
@Component
@ConfigurationProperties(prefix = "mmh.skill")
public class SkillProperties {

    /**
     * Skill 仓库配置列表，按顺序加载，同名 Skill 取第一个
     */
    private List<RepositoryConfig> repositories = new ArrayList<>();

    /**
     * 仓库配置
     */
    @Data
    public static class RepositoryConfig {

        /**
         * 是否启用此仓库
         */
        private boolean enabled = true;

        /**
         * 仓库类型：local 或 git
         */
        private RepositoryType type = RepositoryType.LOCAL;

        /**
         * Local 类型：本地目录路径
         */
        private String path;

        /**
         * Git 类型：远程仓库 URL
         */
        private String url;

        /**
         * Git 类型：分支名称，默认 main
         */
        private String branch = "main";

    }

    /**
     * 仓库类型枚举
     */
    public enum RepositoryType {
        /**
         * 本地文件系统仓库
         */
        LOCAL,
        /**
         * Git 远程仓库
         */
        GIT
    }

}
