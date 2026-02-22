<#if data??>
    <#if data.error?? && data.error?has_content>
Error: ${(data.error)!""}
ElapsedMs: ${(data.elapsedMs)!""}
    <#else>
Format: ${(data.format)!""}
ElapsedMs: ${(data.elapsedMs)!""}
        <#if data.links?? && data.links?has_content>
Links:
            <#list data.links as link>
- ${(link)!""}
            </#list>
        <#elseif data.screenshotBase64?? && data.screenshotBase64?has_content>
ScreenshotBase64:
${(data.screenshotBase64)!""}
        <#else>
${(data.content)!""}
        </#if>
    </#if>
<#else>
Empty response.
</#if>
