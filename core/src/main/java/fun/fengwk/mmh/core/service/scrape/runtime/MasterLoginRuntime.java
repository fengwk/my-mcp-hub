package fun.fengwk.mmh.core.service.scrape.runtime;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import fun.fengwk.mmh.core.service.browser.coordination.ProfileIdValidator;
import fun.fengwk.mmh.core.service.scrape.ScrapeProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Runtime for manual headed login session.
 *
 * @author fengwk
 */
@Component
public class MasterLoginRuntime {

    private final ScrapeProperties scrapeProperties;
    private final ProfileIdValidator profileIdValidator;
    private final PlaywrightFactory playwrightFactory;

    @Autowired
    public MasterLoginRuntime(ScrapeProperties scrapeProperties, ProfileIdValidator profileIdValidator) {
        this(scrapeProperties, profileIdValidator, Playwright::create);
    }

    MasterLoginRuntime(
        ScrapeProperties scrapeProperties,
        ProfileIdValidator profileIdValidator,
        PlaywrightFactory playwrightFactory
    ) {
        this.scrapeProperties = scrapeProperties;
        this.profileIdValidator = profileIdValidator;
        this.playwrightFactory = playwrightFactory;
    }

    public HeadedSession open(String profileId) {
        Playwright playwright = null;
        try {
            Path userDataDir = resolveUserDataDir(profileId);
            playwright = playwrightFactory.create();
            BrowserContext context = playwright.chromium().launchPersistentContext(
                userDataDir,
                new BrowserType.LaunchPersistentContextOptions().setHeadless(false)
            );
            return new HeadedSession(playwright, context);
        } catch (Exception ex) {
            if (playwright != null) {
                try {
                    playwright.close();
                } catch (Exception closeEx) {
                    ex.addSuppressed(closeEx);
                }
            }
            throw new IllegalStateException("failed to open master login runtime: " + ex.getMessage(), ex);
        }
    }

    public Path resolveUserDataDir(String profileId) {
        try {
            String normalizedProfileId = profileIdValidator.normalizeProfileId(profileId);
            Path rootDir = Paths.get(scrapeProperties.getMasterUserDataRoot()).toAbsolutePath().normalize();
            Path userDataDir = rootDir.resolve(normalizedProfileId).normalize();
            if (!userDataDir.startsWith(rootDir)) {
                throw new IllegalArgumentException("invalid user data dir path");
            }
            Files.createDirectories(userDataDir);
            return userDataDir;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("failed to resolve user data dir: " + ex.getMessage(), ex);
        }
    }

    public record HeadedSession(Playwright playwright, BrowserContext context) implements AutoCloseable {

        @Override
        public void close() {
            try {
                context.close();
            } finally {
                playwright.close();
            }
        }

    }

    @FunctionalInterface
    interface PlaywrightFactory {

        Playwright create();

    }

}
