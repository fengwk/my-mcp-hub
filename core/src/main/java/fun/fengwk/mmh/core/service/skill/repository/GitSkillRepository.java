package fun.fengwk.mmh.core.service.skill.repository;

import fun.fengwk.mmh.core.service.skill.model.Skill;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Git 远程仓库 Skill Repository
 * 初始化时克隆或拉取远程仓库，然后委托给 LocalSkillRepository 扫描 Skills
 *
 * @author fengwk
 */
@Slf4j
public class GitSkillRepository implements SkillRepository {

    private static final long GIT_TIMEOUT_SECONDS = 120;

    private final String remoteUrl;
    private final String branch;
    private final Path localPath;
    private final LocalSkillRepository delegate;

    /**
     * 构造 GitSkillRepository
     *
     * @param remoteUrl 远程仓库 URL
     * @param branch    分支名称
     * @param reposRoot skill-repos 根目录
     * @param repoName  仓库名称，用于生成本地目录
     */
    public GitSkillRepository(String remoteUrl, String branch, Path reposRoot, String repoName) {
        this.remoteUrl = remoteUrl;
        this.branch = branch;
        this.localPath = reposRoot.resolve(sanitizeName(repoName));
        this.delegate = initDelegate();
    }

    /**
     * 清理仓库名称，移除不安全字符
     */
    private String sanitizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * 初始化：克隆或拉取仓库，然后创建 LocalSkillRepository 委托
     */
    private LocalSkillRepository initDelegate() {
        try {
            syncRepository();
            return new LocalSkillRepository(localPath);
        } catch (Exception ex) {
            log.error("Failed to initialize git skill repository from {}: {}",
                remoteUrl, ex.getMessage());
            return null;
        }
    }

    /**
     * 同步仓库：如果本地不存在则克隆，否则拉取最新
     */
    private void syncRepository() throws IOException, InterruptedException {
        if (Files.exists(localPath.resolve(".git"))) {
            pullRepository();
        } else {
            cloneRepository();
        }
    }

    /**
     * 克隆远程仓库
     */
    private void cloneRepository() throws IOException, InterruptedException {
        Files.createDirectories(localPath.getParent());

        log.info("Cloning git repository {} to {}", remoteUrl, localPath);

        ProcessBuilder pb = new ProcessBuilder(
            "git", "clone",
            "--branch", branch,
            "--single-branch",
            "--depth", "1",
            remoteUrl,
            localPath.toString()
        );
        pb.redirectErrorStream(true);

        executeGitCommand(pb, "clone");
    }

    /**
     * 拉取最新代码
     */
    private void pullRepository() throws IOException, InterruptedException {
        log.info("Pulling latest from git repository at {}", localPath);

        // 先 fetch
        ProcessBuilder fetchPb = new ProcessBuilder(
            "git", "-C", localPath.toString(),
            "fetch", "--depth", "1", "origin", branch
        );
        fetchPb.redirectErrorStream(true);
        executeGitCommand(fetchPb, "fetch");

        // 再 reset 到最新
        ProcessBuilder resetPb = new ProcessBuilder(
            "git", "-C", localPath.toString(),
            "reset", "--hard", "origin/" + branch
        );
        resetPb.redirectErrorStream(true);
        executeGitCommand(resetPb, "reset");
    }

    /**
     * 执行 Git 命令
     */
    private void executeGitCommand(ProcessBuilder pb, String operation)
        throws IOException, InterruptedException {

        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Git " + operation + " timed out after " + GIT_TIMEOUT_SECONDS + " seconds");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new IOException("Git " + operation + " failed with exit code " + exitCode + ": " + output);
        }

        log.debug("Git {} completed successfully", operation);
    }

    @Override
    public List<String> listSkillNames() {
        if (delegate == null) {
            return List.of();
        }
        return delegate.listSkillNames();
    }

    @Override
    public Optional<Skill> getSkill(String name) {
        if (delegate == null) {
            return Optional.empty();
        }
        return delegate.getSkill(name);
    }

}
