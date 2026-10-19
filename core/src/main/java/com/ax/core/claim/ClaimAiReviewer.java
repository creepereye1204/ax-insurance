package com.ax.core.claim;

import com.ax.core.ai.AiReviewClient;
import com.ax.core.ai.AiReviewException;
import java.text.NumberFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionOperations;

/**
 * 청구 접수 커밋 이후 AI 서비스에 지급 검토를 요청한다.
 * AI 호출(수십 초)은 트랜잭션 밖에서 수행해 DB 커넥션을 오래 잡지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimAiReviewer {

    private final ClaimRepository claimRepository;
    private final AiReviewClient aiReviewClient;
    private final TransactionOperations tx;

    @Async
    @TransactionalEventListener
    public void onClaimSubmitted(ClaimSubmittedEvent event) {
        review(event.claimId());
    }

    void review(Long claimId) {
        String question = tx.execute(status -> {
            Claim claim = claimRepository.findById(claimId).orElseThrow();
            claim.startAiReview();
            return buildQuestion(claim);
        });

        try {
            AiReviewClient.Review review = aiReviewClient.review(question);
            tx.execute(status -> {
                claimRepository.findById(claimId).orElseThrow().completeAiReview(review.answer(), review.runId());
                return null;
            });
            log.info("AI 검토 완료 claimId={} runId={}", claimId, review.runId());
        } catch (AiReviewException e) {
            log.warn("AI 검토 실패, 수동 심사로 전환 claimId={} reason={}", claimId, e.getMessage());
            tx.execute(status -> {
                claimRepository.findById(claimId).orElseThrow().fallbackToManualReview(e.getMessage());
                return null;
            });
        }
    }

    static String buildQuestion(Claim claim) {
        String amount = NumberFormat.getIntegerInstance().format(claim.getClaimedAmount());
        return """
                다음 보험금 청구 건을 약관 기준으로 검토해 주세요.
                - 상품: %s
                - 청구 유형: %s
                - 진료일: %s
                - 진단/치료 내용: %s
                - 청구 금액: %s원
                보장 대상 여부, 면책 사유 해당 여부, 예상 지급액(계산 과정 포함)을 약관 조항을 근거로 알려주세요."""
                .formatted(claim.getContract().getProduct().getPolicyDocument(), claim.getType().getLabel(),
                        claim.getTreatmentDate(), claim.getDiagnosis(), amount);
    }
}
