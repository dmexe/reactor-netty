package reactor.netty.timer;

import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class TimeoutTest {
  private Timeout timeout;

  @Before
  public void before() {
    timeout = Timeout.create();
  }

  @After
  public void after() {
    timeout.dispose();
  }

  @Test
  public void testSubscription() {
    long elapsed = measure(() ->
      StepVerifier
          .create(timeout.ofMillis(100))
          .expectComplete()
          .verify(Duration.ofMillis(500))
    );

    assertThat(elapsed, greaterThan(100L));
  }

  @Test
  public void testExpireTimeout() {
    long elapsed = measure(() -> {
      Mono<Integer> src = Mono.just(1)
          .delayElement(Duration.ofSeconds(1))
          .timeout(timeout.ofMillis(100).log("timer"))
          .log();

      StepVerifier
          .create(src)
          .expectError(TimeoutException.class)
          .verify(Duration.ofMillis(500));
    });

    assertThat(elapsed, greaterThan(100L));
  }

  @Test
  public void testCancelTimeout() {
    Mono<Integer> src = Mono.just(1)
        .timeout(timeout.ofMillis(1000).log("timer"))
        .log();

    StepVerifier
        .create(src)
        .expectNext(1)
        .expectComplete()
        .verify(Duration.ofMillis(500));
  }

  private long measure(Runnable r) {
    long now = System.currentTimeMillis();
    r.run();
    return System.currentTimeMillis() - now;
  }
}
