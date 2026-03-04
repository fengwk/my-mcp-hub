Load a specialized skill that provides domain-specific instructions and workflows.
<#if skills?? && skills?has_content>

When you recognize that a task matches one of the available skills listed below, use this tool to load the full skill instructions.

The skill will inject detailed instructions, workflows, and access to bundled resources (scripts, references, templates) into the conversation context.

Tool output includes a `<skill_content name="...">` block with the loaded content.

The following skills provide specialized sets of instructions for particular tasks
Invoke this tool to load a skill when a task matches one of the available skills listed below:

<available_skills>
<#list skills as skill>
  <skill>
    <name>${skill.name}</name>
    <description>${skill.description!""}</description>
    <location>${skill.location!""}</location>
  </skill>
</#list>
</available_skills>
<#else>
No skills are currently available.
</#if>
