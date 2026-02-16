Status: ${(status)!""}
Progress: ${(progress)!""}
Action:
<#if actions?? && actions?has_content>
<#list actions as action>
- ${(action)!""}
</#list>
<#else>
- (none)
</#if>
