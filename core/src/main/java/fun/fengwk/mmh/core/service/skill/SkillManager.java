package fun.fengwk.mmh.core.service.skill;

import fun.fengwk.convention4j.common.lang.StringUtils;
import fun.fengwk.mmh.core.configuration.MmhProperties;
import fun.fengwk.mmh.core.service.skill.SkillProperties.RepositoryConfig;
import fun.fengwk.mmh.core.service.skill.SkillProperties.RepositoryType;
import fun.fengwk.mmh.core.service.skill.model.Skill;
import fun.fengwk.mmh.core.service.skill.repository.GitSkillRepository;
import fun.fengwk.mmh.core.service.skill.repository.LocalSkillRepository;
import fun.fengwk.mmh.core.service.skill.repository.SkillRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Skill 管理器
 * 聚合多个 SkillRepository，按配置顺序加载，同名 Skill 取第一个
 *
 * @author fengwk
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillManager {

    private final MmhProperties mmhProperties;
    private final SkillProperties skillProperties;

    /**
     * 按配置顺序存储的仓库列表
     */
    private final List<SkillRepository> repositories = new ArrayList<>();

    /**
     * 聚合后的 Skill 映射，同名取第一个
     */
    private final Map<String, Skill> skillMap = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        List<RepositoryConfig> configs = skillProperties.getRepositories();
        if (configs == null || configs.isEmpty()) {
            log.info("No skill repositories configured");
            return;
        }

        for (int i = 0; i < configs.size(); i++) {
            RepositoryConfig config = configs.get(i);
            if (!config.isEnabled()) {
                log.info("Skill repository at index {} is disabled, skipping", i);
                continue;
            }

            try {
                SkillRepository repository = createRepository(config, i);
                if (repository != null) {
                    repositories.add(repository);
                    loadSkillsFromRepository(repository, i);
                }
            } catch (Exception ex) {
                log.warn("Failed to initialize skill repository at index {}: {}", i, ex.getMessage());
            }
        }

        log.info("Skill manager initialized with {} repositories, {} skills loaded",
            repositories.size(), skillMap.size());
    }

    /**
     * 根据配置创建对应类型的仓库
     */
    private SkillRepository createRepository(RepositoryConfig config, int index) {
        if (config.getType() == RepositoryType.LOCAL) {
            return createLocalRepository(config);
        } else if (config.getType() == RepositoryType.GIT) {
            return createGitRepository(config, index);
        } else {
            log.warn("Unknown repository type [{}] at index {}", config.getType(), index);
            return null;
        }
    }

    private SkillRepository createLocalRepository(RepositoryConfig config) {
        if (StringUtils.isBlank(config.getPath())) {
            log.warn("Local skill repository has no path configured");
            return null;
        }

        Path basePath = Paths.get(config.getPath());
        log.info("Creating local skill repository at {}", basePath);
        return new LocalSkillRepository(basePath);
    }

    private SkillRepository createGitRepository(RepositoryConfig config, int index) {
        if (StringUtils.isBlank(config.getUrl())) {
            log.warn("Git skill repository at index {} has no url configured", index);
            return null;
        }

        Path reposRoot = Paths.get(mmhProperties.getSkillReposPath());
        String branch = StringUtils.isNotBlank(config.getBranch()) ? config.getBranch() : "main";
        // 用 index 作为本地克隆目录名，保证唯一且稳定
        String repoName = "repo-" + index;

        log.info("Creating git skill repository from {} (branch: {})", config.getUrl(), branch);
        return new GitSkillRepository(config.getUrl(), branch, reposRoot, repoName);
    }

    /**
     * 从仓库加载 Skill 到聚合映射中，同名取第一个
     */
    private void loadSkillsFromRepository(SkillRepository repository, int index) {
        List<String> skillNames = repository.listSkillNames();
        int loaded = 0;

        for (String skillName : skillNames) {
            if (skillMap.containsKey(skillName)) {
                log.debug("Skill [{}] already loaded, skipping from repository at index {}", skillName, index);
                continue;
            }

            repository.getSkill(skillName).ifPresent(skill -> {
                skillMap.put(skillName, skill);
            });
            loaded++;
        }

        log.info("Loaded {} skills from repository at index {}", loaded, index);
    }

    /**
     * 获取所有 Skill 名称列表
     *
     * @return Skill 名称列表
     */
    public List<String> listSkillNames() {
        return new ArrayList<>(skillMap.keySet());
    }

    /**
     * 根据名称获取 Skill
     *
     * @param name Skill 名称
     * @return Skill 对象，不存在返回 empty
     */
    public Optional<Skill> getSkill(String name) {
        return Optional.ofNullable(skillMap.get(name));
    }

    /**
     * 获取所有已加载的 Skill
     *
     * @return Skill 列表
     */
    public List<Skill> listSkills() {
        return new ArrayList<>(skillMap.values());
    }

}
