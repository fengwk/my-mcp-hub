package fun.fengwk.mmh.core.service.skill.repository;

import fun.fengwk.mmh.core.service.skill.model.Skill;
import fun.fengwk.mmh.core.service.skill.support.SkillFileSampler;
import fun.fengwk.mmh.core.service.skill.support.SkillMarkdownParser;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 本地文件系统 Skill 仓库实现
 * 构造时传入目录路径，初始化时扫描并构建所有 Skill 对象
 *
 * @author fengwk
 */
@Slf4j
public class LocalSkillRepository implements SkillRepository {

    private static final String SKILL_FILE_NAME = "SKILL.md";
    private static final int DEFAULT_FILE_SAMPLE_LIMIT = 10;

    /**
     * Skill 目录的根路径
     */
    private final Path basePath;

    /**
     * 所有 Skill 对象（初始化时构建，运行期间不变）
     * key: skill name, value: Skill object
     */
    private final Map<String, Skill> skills;

    /**
     * 构造 LocalSkillRepository
     * 初始化时扫描目录并构建所有 Skill 对象
     *
     * @param basePath Skill 目录的根路径
     * @throws IllegalArgumentException 如果目录不存在或无法访问
     */
    public LocalSkillRepository(Path basePath) {
        this.basePath = basePath;

        if (!Files.isDirectory(basePath)) {
            throw new IllegalArgumentException("Skill base path does not exist or is not a directory: " + basePath);
        }

        this.skills = loadAllSkills();
        log.info("LocalSkillRepository initialized with path: {}, loaded {} skills: {}",
            basePath, skills.size(), skills.keySet());
    }

    @Override
    public List<String> listSkillNames() {
        return List.copyOf(skills.keySet());
    }

    @Override
    public Optional<Skill> getSkill(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(skills.get(name));
    }

    /**
     * 获取仓库的基础路径
     */
    public Path getBasePath() {
        return basePath;
    }

    /**
     * 初始化时加载所有 Skill
     */
    private Map<String, Skill> loadAllSkills() {
        Map<String, Skill> result = new LinkedHashMap<>();

        try (Stream<Path> subDirs = Files.list(basePath)) {
            subDirs.filter(Files::isDirectory)
                .filter(dir -> !dir.getFileName().toString().startsWith("."))
                .forEach(skillDir -> {
                    Path skillFile = skillDir.resolve(SKILL_FILE_NAME);
                    if (Files.isRegularFile(skillFile)) {
                        try {
                            Skill skill = loadSkill(skillDir, skillFile);
                            result.put(skill.getName(), skill);
                        } catch (IOException e) {
                            throw new IllegalStateException("Failed to load skill from " + skillFile, e);
                        }
                    }
                });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan skill directory: " + basePath, e);
        }

        return Collections.unmodifiableMap(result);
    }

    /**
     * 加载单个 Skill
     */
    private Skill loadSkill(Path skillDir, Path skillFile) throws IOException {
        String rawContent = Files.readString(skillFile);
        SkillMarkdownParser.ParseResult parseResult = SkillMarkdownParser.parse(rawContent);
        List<String> files = SkillFileSampler.sample(skillDir, DEFAULT_FILE_SAMPLE_LIMIT);

        String dirName = skillDir.getFileName().toString();
        String name = parseResult.name() != null && !parseResult.name().isBlank()
            ? parseResult.name()
            : dirName;

        return Skill.builder()
            .name(name)
            .description(parseResult.description())
            .content(parseResult.content())
            .basePath(skillDir.toAbsolutePath().toString())
            .files(files)
            .build();
    }

}
