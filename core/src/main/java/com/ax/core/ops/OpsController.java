package com.ax.core.ops;

import com.ax.core.ai.AiReviewClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 화면에 표시하는 운영 상태 (인스턴스, AI 서킷브레이커). 민감 정보는 포함하지 않는다. */
@RestController
@RequestMapping("/ops")
public class OpsController {

    private final String instance;
    private final CircuitBreaker aiCircuit;

    public OpsController(@Value("${ax.instance}") String instance, CircuitBreakerRegistry registry) {
        this.instance = instance;
        this.aiCircuit = registry.circuitBreaker(AiReviewClient.CIRCUIT);
    }

    public record CircuitStatus(String state, float failureRate, int bufferedCalls, int failedCalls, long notPermittedCalls) {
    }

    public record Status(String instance, CircuitStatus aiCircuit) {
    }

    @GetMapping("/status")
    public Status status() {
        CircuitBreaker.Metrics m = aiCircuit.getMetrics();
        return new Status(instance, new CircuitStatus(aiCircuit.getState().name(), m.getFailureRate(),
                m.getNumberOfBufferedCalls(), m.getNumberOfFailedCalls(), m.getNumberOfNotPermittedCalls()));
    }
}
