package fun.fengwk.mmh.core.service.browser.runtime;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import fun.fengwk.convention4j.common.lang.StringUtils;
import fun.fengwk.mmh.core.service.browser.BrowserProperties;
import fun.fengwk.mmh.core.service.browser.BrowserProperties.BrowserProfileProperties;
import fun.fengwk.mmh.core.service.browser.BrowserStealthSupport;
import fun.fengwk.mmh.core.service.browser.coordination.ProfileIdValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Runtime for manual headed login session.
 *
 * @author fengwk
 */
@Slf4j
@Component
public class MasterLoginRuntime {

    private static final String DISABLE_BACKGROUND_MODE_ARG = "--disable-background-mode";
    private static final String DISABLE_BACKGROUND_NETWORKING_ARG = "--disable-background-networking";
    private static final String DISABLE_COMPONENT_BACKGROUND_PAGES_ARG = "--disable-component-extensions-with-background-pages";
    private static final String DISABLE_EXTENSIONS_ARG = "--disable-extensions";
    private static final String NO_FIRST_RUN_ARG = "--no-first-run";
    private static final String NO_DEFAULT_BROWSER_CHECK_ARG = "--no-default-browser-check";

    private final ProfileIdValidator profileIdValidator;
    private final BrowserProperties browserProperties;
    private final PlaywrightFactory playwrightFactory;

    @Autowired
    public MasterLoginRuntime(
        ProfileIdValidator profileIdValidator,
        BrowserProperties browserProperties
    ) {
        this(profileIdValidator, browserProperties, Playwright::create);
    }

    MasterLoginRuntime(
        ProfileIdValidator profileIdValidator,
        BrowserProperties browserProperties,
        PlaywrightFactory playwrightFactory
    ) {
        this.profileIdValidator = profileIdValidator;
        this.browserProperties = browserProperties;
        this.playwrightFactory = playwrightFactory;
    }

    public HeadedSession open(String profileId) {
        Playwright playwright = null;
        try {
            Path userDataDir = resolveUserDataDir(profileId);
            playwright = playwrightFactory.create();
            BrowserProfileProperties profileProperties = browserProperties.resolveMasterProfile();
            List<String> launchArgs = buildMasterLoginLaunchArgs(profileProperties);
            BrowserType.LaunchPersistentContextOptions options = BrowserContextOptionsSupport.buildContextOptions(
                profileProperties,
                false,
                launchArgs,
                true
            );
            BrowserContext context = playwright.chromium().launchPersistentContext(userDataDir, options);
            BrowserStealthSupport.apply(context, browserProperties);
            openInitialPageIfNeeded(context);
            return new HeadedSession(playwright, context);
        } catch (Exception ex) {
            if (playwright != null) {
                try {
                    playwright.close();
                } catch (Exception closeEx) {
                    ex.addSuppressed(closeEx);
                }
            }
            log.warn("master login runtime open failed, profileId={}, error={}", profileId, ex.getMessage());
            throw new IllegalStateException("failed to open master login runtime: " + ex.getMessage(), ex);
        }
    }

    private List<String> buildMasterLoginLaunchArgs(BrowserProfileProperties profileProperties) {
        List<String> args = new ArrayList<>(BrowserContextOptionsSupport.normalizeLaunchArgs(profileProperties.getLaunchArgs()));
        if (browserProperties.getMasterLoginArgs() != null && !browserProperties.getMasterLoginArgs().isEmpty()) {
            args.addAll(BrowserContextOptionsSupport.normalizeLaunchArgs(browserProperties.getMasterLoginArgs()));
        }

        addIfAbsent(args, DISABLE_BACKGROUND_MODE_ARG);
        addIfAbsent(args, DISABLE_BACKGROUND_NETWORKING_ARG);
        addIfAbsent(args, DISABLE_COMPONENT_BACKGROUND_PAGES_ARG);
        addIfAbsent(args, DISABLE_EXTENSIONS_ARG);
        addIfAbsent(args, NO_FIRST_RUN_ARG);
        addIfAbsent(args, NO_DEFAULT_BROWSER_CHECK_ARG);

        return args;
    }

    private void addIfAbsent(List<String> args, String arg) {
        if (!args.contains(arg)) {
            args.add(arg);
        }
    }

    public Path resolveUserDataDir(String profileId) {
        try {
            String normalizedProfileId = profileIdValidator.normalizeProfileId(profileId);
            Path rootDir = Paths.get(browserProperties.getMasterUserDataRoot()).toAbsolutePath().normalize();
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

    private void openInitialPageIfNeeded(BrowserContext context) {
        String initialPageUrl = browserProperties.getMasterLoginInitialPageUrl();
        if (StringUtils.isBlank(initialPageUrl)) {
            return;
        }
        Page page = resolveInitialPage(context);
        page.navigate(initialPageUrl,
            new Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                .setTimeout((double) browserProperties.getMasterLoginNavigateTimeoutMs())
        );
    }

    private Page resolveInitialPage(BrowserContext context) {
        long waitDeadline = System.currentTimeMillis() + 500;
        while (System.currentTimeMillis() < waitDeadline) {
            if (!context.pages().isEmpty()) {
                return context.pages().get(0);
            }
            sleep(50);
        }
        if (!context.pages().isEmpty()) {
            return context.pages().get(0);
        }
        return context.newPage();
    }

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    interface PlaywrightFactory {

        Playwright create();

    }

}
