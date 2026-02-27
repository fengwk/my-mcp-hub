package fun.fengwk.mmh.core.service.browser.coordination;

import fun.fengwk.convention4j.common.lang.StringUtils;
import fun.fengwk.mmh.core.service.browser.BrowserProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Validates and normalizes profile id.
 *
 * @author fengwk
 */
@Component
@RequiredArgsConstructor
public class ProfileIdValidator {

    private final BrowserProperties browserProperties;

    public String normalizeProfileId(String profileId) {
        String normalized = StringUtils.isBlank(profileId)
            ? browserProperties.getDefaultProfileId()
            : profileId.trim();
        if (StringUtils.isBlank(normalized)) {
            throw new IllegalArgumentException("profileId is blank");
        }
        if (!normalized.matches(browserProperties.getProfileIdRegex())) {
            throw new IllegalArgumentException("invalid profileId");
        }
        if (normalized.contains("/") || normalized.contains("\\") || normalized.contains("..")) {
            throw new IllegalArgumentException("invalid profileId");
        }
        return normalized;
    }

}
