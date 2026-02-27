<#if data??>
    <#if data.error?? && data.error?has_content>
Error: ${(data.error)!""}
ElapsedMs: ${(data.elapsedMs)!""}
    <#elseif data.screenshotBase64?? && data.screenshotBase64?has_content>
{
  "title": "mmh-scrape-media (${(data.screenshotMime)!'application/octet-stream'})",
  "output": "Media fetched successfully",
  "metadata": {
    "format": "${(data.format)!''}",
    "elapsedMs": "${(data.elapsedMs)!''}"
  },
  "attachments": [
    {
      "type": "file",
      "mime": "${(data.screenshotMime)!'application/octet-stream'}",
      "url": "${(data.screenshotBase64)!''}"
    }
  ]
}
    <#else>
Format: ${(data.format)!""}
ElapsedMs: ${(data.elapsedMs)!""}
        <#if data.links?? && data.links?has_content>
Links:
            <#list data.links as link>
- ${(link)!""}
            </#list>
        <#else>
${(data.content)!""}
        </#if>
    </#if>
<#else>
Empty response.
</#if>
