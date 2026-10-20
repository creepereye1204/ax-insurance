package com.ax.core.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.net.http.HttpClient;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Python AI 서비스(/chat) 호출 클라이언트.
 * 서킷브레이커로 감싸서, AI 서비스가 연속으로 실패하면 일정 시간 호출 자체를 차단한다
 * (기다리지 않고 바로 수동심사로 넘어가고, 장애 난 서비스에 요청을 계속 쌓지 않는다).
 * 타임아웃·통신 오류·차단은 모두 AiReviewException 으로 통일한다.
 */
@Component
public class AiReviewClient {

    public static final String CIRCUIT = "ai";

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    public AiReviewClient(AiProperties props, CircuitBreakerRegistry registry) {
        // JDK HttpClient 기본값은 HTTP/2 업그레이드(h2c) 시도 → uvicorn 에서 요청 본문이 유실되므로 1.1 고정
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(props.connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(props.readTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .build();
        this.circuitBreaker = registry.circuitBreaker(CIRCUIT);
    }

    public record Review(String answer, Long runId) {
    }

    private record ChatResponse(String answer, @JsonProperty("run_id") Long runId) {
    }

    public Review review(String question) {
        try {
            return circuitBreaker.executeSupplier(() -> call(question));
        } catch (CallNotPermittedException e) {
            throw new AiReviewException("AI 서비스 장애로 호출이 일시 차단되었습니다(서킷 오픈). 잠시 후 자동으로 재시도합니다.", e);
        }
    }

    public CircuitBreaker.State state() {
        return circuitBreaker.getState();
    }

    private Review call(String question) {
        try {
            ChatResponse res = restClient.post()
                    .uri("/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("question", question))
                    .retrieve()
                    .body(ChatResponse.class);
            if (res == null || res.answer() == null || res.answer().isBlank()) {
                throw new AiReviewException("AI 서비스가 빈 응답을 반환했습니다.", null);
            }
            return new Review(res.answer(), res.runId());
        } catch (RestClientException e) {
            throw new AiReviewException("AI 서비스 호출 실패: " + e.getMessage(), e);
        }
    }
}
