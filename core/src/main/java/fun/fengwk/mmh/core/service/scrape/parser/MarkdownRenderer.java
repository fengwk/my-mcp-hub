package fun.fengwk.mmh.core.service.scrape.parser;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import com.vladsch.flexmark.util.data.MutableDataSet;
import fun.fengwk.convention4j.common.lang.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Lightweight html to markdown renderer.
 *
 * @author fengwk
 */
@Component
public class MarkdownRenderer {

    private final FlexmarkHtmlConverter converter;

    public MarkdownRenderer() {
        MutableDataSet options = new MutableDataSet();
        options.set(FlexmarkHtmlConverter.SETEXT_HEADINGS, true);
        options.set(FlexmarkHtmlConverter.UNORDERED_LIST_DELIMITER, '*');
        options.set(FlexmarkHtmlConverter.LIST_ITEM_INDENT, 4);
        options.set(FlexmarkHtmlConverter.LIST_CONTENT_INDENT, true);
        options.set(FlexmarkHtmlConverter.DIV_AS_PARAGRAPH, true);
        this.converter = FlexmarkHtmlConverter.builder(options).build();
    }

    public String render(String html) {
        return render(html, "");
    }

    public String render(String html, String baseUrl) {
        if (StringUtils.isBlank(html)) {
            return "";
        }
        return converter.convert(html);
    }

}
