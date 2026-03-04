<skill_content name="${skill.name}">
# Skill: ${skill.name}

${(skill.content?trim)!""}

<#if skill.basePath?? && skill.basePath?has_content>
Base directory for this skill: ${skill.basePath}
Relative paths in this skill (e.g., scripts/, reference/) are relative to this base directory.
</#if>
<#if skill.files?? && skill.files?has_content>
Note: file list is sampled.

<skill_files>
<#list skill.files as file>
<file>${file}</file>
</#list>
</skill_files>
</#if>
</skill_content>
