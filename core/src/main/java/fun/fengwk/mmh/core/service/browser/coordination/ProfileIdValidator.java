package fun.fengwk.mmh.core.service.browser.coordination;

import fun.fengwk.convention4j.common.lang.StringUtils;
import fun.fengwk.mmh.core.service.browser.BrowserProperties;
import fun.fengwk.mmh.core.service.scrape.ScrapeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Validates and normalizes profile id.
 *
 * @author fengwk
 */
@Component
@RequiredArgsConstructor
public class ProfileIdValidator {

    private final BrowserProperties browserProperties;
    private final ScrapeProperties scrapeProperties;

    public String normalizeProfileId(String profileId) {
        String normalized = StringUtils.isBlank(profileId)
            ? scrapeProperties.getDefaultProfileId()
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

    public Path resolveProfileDir(Path snapshotRoot, String profileId) {
        Path profileDir = snapshotRoot.resolve(profileId).normalize();
        if (!profileDir.startsWith(snapshotRoot)) {
            throw new IllegalArgumentException("invalid profile path");
        }
        return profileDir;
    }

    public Path resolveSnapshotRoot() {
        return Paths.get(browserProperties.getSnapshotRoot()).toAbsolutePath().normalize();
    }

}
