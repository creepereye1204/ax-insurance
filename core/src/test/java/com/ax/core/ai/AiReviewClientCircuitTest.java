package com.ax.core.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AiReviewClientCircuitTest {

    /** 닫힌 포트로 호출해 즉시 실패시키고, 서킷이 열린 뒤에는 네트워크 호출 없이 바로 차단되는지 확인 */
    @Test
    void 연속_실패하면_서킷이_열리고_이후_호출은_즉시_차단된다() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .build());
        AiReviewClient client = new AiReviewClient(
                new AiProperties("http://127.0.0.1:1", Duration.ofMillis(300), Duration.ofSeconds(1)), registry);

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> client.review("q"))
                    .isInstanceOf(AiReviewException.class)
                    .hasMessageContaining("호출 실패");
        }
        assertThat(client.state()).isEqualTo(CircuitBreaker.State.OPEN);

        long started = System.nanoTime();
        assertThatThrownBy(() -> client.review("q"))
                .isInstanceOf(AiReviewException.class)
                .hasMessageContaining("서킷 오픈");
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(50));
    }
}
