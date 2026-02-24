package fun.fengwk.mmh.core.service.browser.runtime;

import fun.fengwk.mmh.core.service.browser.BrowserProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.lang.reflect.Field;
import java.util.concurrent.BlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * @author fengwk
 */
public class BrowserWorkerManagerTest {

    private BrowserWorkerManager manager;

    @AfterEach
    public void tearDown() {
        if (manager != null) {
            manager.shutdown();
        }
    }

    @Test
    public void shouldExecuteTaskSuccessfully() {
        BrowserProperties properties = new BrowserProperties();
        properties.setWorkerPoolMinSizePerProcess(1);
        properties.setWorkerPoolMaxSizePerProcess(1);
        properties.setRequestQueueCapacity(1);
        properties.setQueueOfferTimeoutMs(100);
        properties.setWorkerIdleTtlMs(1000);
        properties.setWorkerRefreshIntervalMs(50);
        manager = new BrowserWorkerManager(properties, () ->
            new BrowserWorkerManager.BrowserSession(mock(com.microsoft.playwright.Playwright.class),
                mock(com.microsoft.playwright.Browser.class))
        );

        String result = manager.execute(browser -> "ok");

        assertThat(result).isEqualTo("ok");
    }

    @Test
    public void shouldFailFastWhenQueueBusy() throws Exception {
        BrowserProperties properties = new BrowserProperties();
        properties.setWorkerPoolMinSizePerProcess(1);
        properties.setWorkerPoolMaxSizePerProcess(1);
        properties.setRequestQueueCapacity(1);
        properties.setQueueOfferTimeoutMs(50);
        properties.setWorkerIdleTtlMs(1000);
        properties.setWorkerRefreshIntervalMs(50);
        manager = new BrowserWorkerManager(properties, () ->
            new BrowserWorkerManager.BrowserSession(mock(com.microsoft.playwright.Playwright.class),
                mock(com.microsoft.playwright.Browser.class))
        );

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<String> first = CompletableFuture.supplyAsync(() -> manager.execute(browser -> {
            entered.countDown();
            release.await(1, TimeUnit.SECONDS);
            return "first";
        }));

        assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<String> second = CompletableFuture.supplyAsync(() -> manager.execute(browser -> "second"));
        waitUntilQueueSize(1, 1000);

        assertThatThrownBy(() -> manager.execute(browser -> "third"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("worker pool is busy");

        release.countDown();
        assertThat(first.join()).isEqualTo("first");
        assertThat(second.join()).isEqualTo("second");
    }

    private void waitUntilQueueSize(int expected, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (queueSize() == expected) {
                return;
            }
            Thread.sleep(10);
        }
    }

    private int queueSize() throws Exception {
        Field field = BrowserWorkerManager.class.getDeclaredField("queue");
        field.setAccessible(true);
        BlockingQueue<?> queue = (BlockingQueue<?>) field.get(manager);
        return queue.size();
    }

    @Test
    public void shouldWrapTaskFailure() {
        BrowserProperties properties = new BrowserProperties();
        properties.setWorkerPoolMinSizePerProcess(1);
        properties.setWorkerPoolMaxSizePerProcess(1);
        properties.setRequestQueueCapacity(1);
        properties.setQueueOfferTimeoutMs(100);
        properties.setWorkerIdleTtlMs(1000);
        properties.setWorkerRefreshIntervalMs(50);
        manager = new BrowserWorkerManager(properties, () ->
            new BrowserWorkerManager.BrowserSession(mock(com.microsoft.playwright.Playwright.class),
                mock(com.microsoft.playwright.Browser.class))
        );

        assertThatThrownBy(() -> manager.execute(browser -> {
            throw new RuntimeException("boom");
        }))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("browser worker execution failed");
    }

}
