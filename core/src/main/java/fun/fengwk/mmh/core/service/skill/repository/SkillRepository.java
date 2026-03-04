package fun.fengwk.mmh.core.service.skill.repository;

import fun.fengwk.mmh.core.service.skill.model.Skill;

import java.util.List;
import java.util.Optional;

/**
 * Skill 仓库接口
 * 定义 Skill 的发现和获取能力
 *
 * @author fengwk
 */
public interface SkillRepository {

    /**
     * 获取所有 Skill 的名称列表
     *
     * @return Skill 名称列表
     */
    List<String> listSkillNames();

    /**
     * 根据名称获取完整的 Skill 对象
     *
     * @param name Skill 名称
     * @return Skill 对象，如果不存在返回 empty
     */
    Optional<Skill> getSkill(String name);

}
