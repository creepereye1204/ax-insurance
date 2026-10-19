package com.ax.core.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.http.HttpClient;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Python AI 서비스(/chat) 호출 클라이언트. 타임아웃·통신 오류는 AiReviewException 으로 통일한다. */
@Component
public class AiReviewClient {

    private final RestClient restClient;

    public AiReviewClient(AiProperties props) {
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
    }

    public record Review(String answer, Long runId) {
    }

    private record ChatResponse(String answer, @JsonProperty("run_id") Long runId) {
    }

    public Review review(String question) {
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
