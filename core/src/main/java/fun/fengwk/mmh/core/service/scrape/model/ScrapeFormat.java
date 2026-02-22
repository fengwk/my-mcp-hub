package fun.fengwk.mmh.core.service.scrape.model;

import fun.fengwk.convention4j.common.lang.StringUtils;

/**
 * Supported scrape output formats.
 *
 * @author fengwk
 */
public enum ScrapeFormat {

    MARKDOWN("markdown"),
    HTML("html"),
    LINKS("links"),
    SCREENSHOT("screenshot"),
    FULLSCREENSHOT("fullscreenshot");

    private final String value;

    ScrapeFormat(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ScrapeFormat fromValue(String value) {
        if (StringUtils.isBlank(value)) {
            return MARKDOWN;
        }
        for (ScrapeFormat format : values()) {
            if (format.value.equalsIgnoreCase(value.trim())) {
                return format;
            }
        }
        throw new IllegalArgumentException("unsupported format: " + value);
    }

}
