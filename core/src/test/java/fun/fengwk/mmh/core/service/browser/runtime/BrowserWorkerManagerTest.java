package fun.fengwk.mmh.core.service.browser.runtime;

import fun.fengwk.mmh.core.service.browser.BrowserProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        properties.setQueueOfferTimeoutMs(100);
        manager = new BrowserWorkerManager(properties);

        String result = manager.execute(() -> "ok");

        assertThat(result).isEqualTo("ok");
    }

    @Test
    public void shouldFailFastWhenWorkerPoolBusy() throws Exception {
        BrowserProperties properties = new BrowserProperties();
        properties.setWorkerPoolMinSizePerProcess(1);
        properties.setWorkerPoolMaxSizePerProcess(1);
        properties.setQueueOfferTimeoutMs(50);
        manager = new BrowserWorkerManager(properties);

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<String> first = CompletableFuture.supplyAsync(() -> manager.execute(() -> {
            entered.countDown();
            release.await(1, TimeUnit.SECONDS);
            return "first";
        }));

        assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> manager.execute(() -> "second"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("worker pool is busy");

        release.countDown();
        assertThat(first.join()).isEqualTo("first");
    }

    @Test
    public void shouldWrapTaskFailure() {
        BrowserProperties properties = new BrowserProperties();
        properties.setWorkerPoolMinSizePerProcess(1);
        properties.setWorkerPoolMaxSizePerProcess(1);
        properties.setQueueOfferTimeoutMs(100);
        manager = new BrowserWorkerManager(properties);

        assertThatThrownBy(() -> manager.execute(() -> {
            throw new RuntimeException("boom");
        }))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("browser worker execution failed");
    }

    @Test
    public void shouldReinitializeWorkerPoolAfterIdleRelease() throws Exception {
        BrowserProperties properties = new BrowserProperties();
        properties.setWorkerPoolMinSizePerProcess(1);
        properties.setWorkerPoolMaxSizePerProcess(1);
        properties.setWorkerIdleTtlMs(1);
        properties.setQueueOfferTimeoutMs(100);
        manager = new BrowserWorkerManager(properties);

        assertThat(manager.execute(() -> "first")).isEqualTo("first");

        Thread.sleep(5);
        Method method = BrowserWorkerManager.class.getDeclaredMethod("releaseIdleWorkerPool");
        method.setAccessible(true);
        method.invoke(manager);

        assertThat(manager.execute(() -> "second")).isEqualTo("second");
    }

}
