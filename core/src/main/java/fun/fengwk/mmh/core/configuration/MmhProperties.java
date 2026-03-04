package fun.fengwk.mmh.core.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;

/**
 * My MCP Hub 顶层配置
 *
 * @author fengwk
 */
@Data
@Component
@ConfigurationProperties(prefix = "mmh")
public class MmhProperties {

    /**
     * 配置根路径，所有数据文件都存放在此目录下
     */
    private String configPath = Paths.get(System.getProperty("user.home"), ".my-mcp-hub").toString();

    /**
     * 获取浏览器数据目录路径
     *
     * @return browser-data 目录路径
     */
    public String getBrowserDataPath() {
        return getSubPath("browser-data");
    }

    /**
     * 获取 Skill 仓库存储目录路径
     *
     * @return skill-repos 目录路径
     */
    public String getSkillReposPath() {
        return getSubPath("skill-repos");
    }

    /**
     * 获取子目录路径
     *
     * @param subDir 子目录名称
     * @return 完整路径
     */
    private String getSubPath(String subDir) {
        return Paths.get(configPath, subDir).toString();
    }

}
