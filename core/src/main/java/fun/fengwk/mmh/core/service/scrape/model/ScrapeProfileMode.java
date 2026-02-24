package fun.fengwk.mmh.core.service.scrape.model;

import fun.fengwk.convention4j.common.lang.StringUtils;

/**
 * Profile mode for scrape requests.
 *
 * @author fengwk
 */
public enum ScrapeProfileMode {

    DEFAULT("default"),
    MASTER("master");

    private final String value;

    ScrapeProfileMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ScrapeProfileMode fromValue(String value) {
        if (StringUtils.isBlank(value)) {
            return DEFAULT;
        }
        for (ScrapeProfileMode mode : values()) {
            if (mode.value.equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }
        throw new IllegalArgumentException("unsupported profileMode: " + value);
    }

}
