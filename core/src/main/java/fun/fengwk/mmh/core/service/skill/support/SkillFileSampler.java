package fun.fengwk.mmh.core.service.skill.support;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * 采样 Skill 目录下的文件列表
 *
 * @author fengwk
 */
@Slf4j
public class SkillFileSampler {

    private static final int DEFAULT_LIMIT = 10;
    private static final String SKILL_FILE_NAME = "SKILL.md";

    private SkillFileSampler() {}

    /**
     * 采样 Skill 目录下的文件
     *
     * @param directory Skill 目录
     * @param limit     最大返回数量
     * @return 相对于 Skill 目录的文件路径列表
     */
    public static List<String> sample(Path directory, int limit) {
        if (directory == null || !Files.isDirectory(directory)) {
            return Collections.emptyList();
        }

        int effectiveLimit = limit > 0 ? limit : DEFAULT_LIMIT;

        try (Stream<Path> pathStream = Files.walk(directory)) {
            return pathStream
                .filter(Files::isRegularFile)
                .filter(p -> !p.getFileName().toString().equals(SKILL_FILE_NAME))
                .limit(effectiveLimit)
                .map(p -> directory.relativize(p).toString())
                .toList();
        } catch (IOException e) {
            log.warn("Failed to sample files from {}: {}", directory, e.getMessage());
            return Collections.emptyList();
        }
    }

}
