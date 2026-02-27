package fun.fengwk.mmh.core.service.browser.runtime;

import fun.fengwk.convention4j.common.lang.StringUtils;

/**
 * Profile type for browser worker pools.
 *
 * @author fengwk
 */
public enum ProfileType {

    /**
     * Default profile with worker-private persistent profile.
     */
    DEFAULT("default"),

    /**
     * Master profile with persistent context and cross-process locking.
     */
    MASTER("master");

    private final String value;

    ProfileType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ProfileType fromValue(String value) {
        if (StringUtils.isBlank(value)) {
            return DEFAULT;
        }
        for (ProfileType type : values()) {
            if (type.value.equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        throw new IllegalArgumentException("unsupported profileMode: " + value);
    }

}
