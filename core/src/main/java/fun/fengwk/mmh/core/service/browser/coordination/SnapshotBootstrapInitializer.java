package fun.fengwk.mmh.core.service.browser.coordination;

import fun.fengwk.convention4j.common.lang.StringUtils;
import fun.fengwk.mmh.core.service.scrape.ScrapeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Initializes snapshot storage on application startup.
 *
 * @author fengwk
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SnapshotBootstrapInitializer implements ApplicationRunner {

    private final SnapshotBootstrap snapshotBootstrap;
    private final ScrapeProperties scrapeProperties;

    @Override
    public void run(ApplicationArguments args) {
        String profileId = scrapeProperties.getDefaultProfileId();
        if (StringUtils.isBlank(profileId)) {
            log.warn("skip snapshot initialization: default profile id is blank");
            return;
        }
        snapshotBootstrap.ensureInitialized(profileId.trim());
    }

}
