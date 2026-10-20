package com.ax.core.uw;

import com.ax.core.ai.AiReviewClient;
import com.ax.core.ai.AiReviewException;
import com.ax.core.application.Application;
import com.ax.core.application.DisclosureAnswer;
import com.ax.core.claim.ClaimAiReviewer;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionOperations;

/**
 * 본사 이관 건에 대해 AI 심사 참고의견을 비동기로 생성한다.
 * 실패해도 심사 흐름에는 영향이 없다(심사자는 의견 없이 결정 가능).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UwAiOpinionWriter {

    private final UwCaseRepository caseRepository;
    private final AiReviewClient aiReviewClient;
    private final TransactionOperations tx;

    @Async
    @TransactionalEventListener
    public void onReferred(UwReferredEvent event) {
        String question = tx.execute(status -> buildQuestion(caseRepository.findById(event.uwCaseId()).orElseThrow()));
        String opinion;
        try {
            opinion = aiReviewClient.review(question).answer();
            log.info("AI 심사의견 생성 uwCaseId={}", event.uwCaseId());
        } catch (AiReviewException e) {
            log.warn("AI 심사의견 생성 실패 uwCaseId={} reason={}", event.uwCaseId(), e.getMessage());
            opinion = "[AI 의견 생성 실패] " + e.getMessage();
        }
        String result = opinion;
        tx.execute(status -> {
            UwCase uwCase = caseRepository.findById(event.uwCaseId()).orElseThrow();
            uwCase.writeAiOpinion(result);
            uwCase.log(new UwDecisionLog(UwStage.MANUAL_UW, "AI", "참고의견 작성", List.of(), null));
            return null;
        });
    }

    static String buildQuestion(UwCase uwCase) {
        Application app = uwCase.getApplication();
        String disclosures = app.getAnswers().stream()
                .filter(DisclosureAnswer::isAnswer)
                .flatMap(a -> a.getDetails().stream().map(d -> "%s: %s %s(%s, %d일, %s)".formatted(
                        a.getQuestion().getCode(), d.getDiagnosedOn(), d.getKcdCode(), d.getTreatment().getLabel(),
                        d.getTreatmentDays(), d.isCured() ? "완치" : "치료중")))
                .collect(Collectors.joining("\n  - ", "  - ", ""));
        String proposals = uwCase.getResults().stream()
                .map(r -> "%s: %s (적용 룰 %s)".formatted(r.getCoverage().getName(), r.getOutcome().getLabel(), r.getMatchedRules()))
                .collect(Collectors.joining("\n  - ", "  - ", ""));
        return """
                보험 가입 심사(언더라이팅) 참고의견을 작성해 주세요.
                - 상품: %s
                - 피보험자: 만 %d세
                - 본사 이관 사유: %s
                - 고지 내역:
                %s
                - 전산심사 담보별 제안:
                %s
                약관에서 계약 전 알릴 의무, 보장하지 않는 손해 등 관련 조항을 찾아 근거로 제시하고,
                심사자가 추가로 확인해야 할 사항(필요 서류, 확인할 병력)을 정리해 주세요. 최종 결정은 심사자가 합니다."""
                .formatted(ClaimAiReviewer.policyOf(app.getProduct()),
                        app.getCustomer().ageAt(app.getSubmittedAt().toLocalDate()),
                        uwCase.getReferReason(), disclosures, proposals);
    }
}
