<#if data??>
    <#if data.error?? && data.error?has_content>
Error: ${(data.error)!""}
    <#else>
        <#if data.results?? && data.results?has_content>
            <#list data.results as item>
Result ${item?index + 1}:
* [${(item.title)!""}](${(item.url)!""})
```
${(item.content)!""}
```
                <#if item?has_next>
---
                </#if>
            </#list>
        <#else>
No results.
        </#if>
    </#if>
<#else>
Empty response.
</#if>
