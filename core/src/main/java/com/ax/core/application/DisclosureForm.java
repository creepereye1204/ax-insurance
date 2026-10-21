package com.ax.core.application;

import com.ax.core.common.BusinessException;
import com.ax.core.common.TreatmentType;
import com.ax.core.disclosure.Question;
import com.ax.core.disclosure.Questionnaire;
import com.ax.core.kcd.KcdRepository;
import com.ax.core.product.Product;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 알릴의무 고지 입력 검증. 청약(미리보기·전산심사 사전 실행·제출)과 고지 기반 선심사가 같은 규칙을 쓴다.
 */
@Component
@RequiredArgsConstructor
public class DisclosureForm {

    private final KcdRepository kcdRepository;

    public record DetailInput(String kcdCode, LocalDate diagnosedOn, TreatmentType treatment, int treatmentDays, boolean cured) {
    }

    public record AnswerInput(Long questionId, boolean answer, List<DetailInput> details) {
    }

    /** 상품 심사 유형과 질문지가 맞아야 한다 (간편심사형 ↔ 간편고지 3-2-5) */
    public void checkFor(Questionnaire questionnaire, Product product) {
        String expected = Questionnaire.codeFor(product.isSimplifiedUw());
        if (!expected.equals(questionnaire.getCode())) {
            throw new BusinessException(product.isSimplifiedUw()
                    ? "간편심사형 상품은 간편고지(3-2-5) 질문지로 고지해야 합니다."
                    : "일반심사 상품은 표준 알릴의무 질문지로 고지해야 합니다.");
        }
    }

    /**
     * 모든 문항에 답해야 하고, "예"인 상세 필요 문항은 상세가 1건 이상, 진단일은 문항의 조회기간 안이어야 한다.
     * 반환하는 응답은 아직 청약에 연결되지 않은 상태다.
     */
    public List<DisclosureAnswer> build(Questionnaire questionnaire, List<AnswerInput> inputs, LocalDate today) {
        Map<Long, AnswerInput> byQuestion = inputs == null ? Map.of()
                : inputs.stream().collect(Collectors.toMap(AnswerInput::questionId, Function.identity(), (a, b) -> a));

        List<DisclosureAnswer> answers = new ArrayList<>();
        for (Question q : questionnaire.getQuestions()) {
            AnswerInput in = byQuestion.get(q.getId());
            if (in == null) {
                throw new BusinessException("고지 문항 " + q.getCode() + "에 답하지 않았습니다.");
            }
            DisclosureAnswer answer = new DisclosureAnswer(q, in.answer());
            List<DetailInput> details = in.details() == null ? List.of() : in.details();
            if (in.answer() && q.isDetailRequired() && details.isEmpty()) {
                throw new BusinessException(q.getCode() + " '예' 응답에는 질병·치료 상세를 1건 이상 입력해야 합니다.");
            }
            if (in.answer()) {
                for (DetailInput d : details) {
                    validateDetail(q, d, today);
                    answer.addDetail(new DisclosureDetail(d.kcdCode().trim().toUpperCase(), d.diagnosedOn(),
                            d.treatment(), d.treatmentDays(), d.cured()));
                }
            }
            answers.add(answer);
        }
        return answers;
    }

    private void validateDetail(Question q, DetailInput d, LocalDate today) {
        if (d.kcdCode() == null || d.kcdCode().isBlank()) {
            throw new BusinessException(q.getCode() + ": 질병코드(KCD)를 입력하세요.");
        }
        if (!kcdRepository.existsById(d.kcdCode().trim().toUpperCase())) {
            throw new BusinessException(q.getCode() + ": 등록되지 않은 질병코드입니다 (" + d.kcdCode() + ")");
        }
        if (d.diagnosedOn() == null || d.diagnosedOn().isAfter(today)) {
            throw new BusinessException(q.getCode() + ": 진단일이 올바르지 않습니다.");
        }
        if (q.getLookbackMonths() > 0 && d.diagnosedOn().isBefore(today.minusMonths(q.getLookbackMonths()))) {
            throw new BusinessException(q.getCode() + ": 진단일이 고지 대상 기간(최근 " + q.getLookbackMonths() + "개월)을 벗어납니다.");
        }
        if (d.treatment() == null || d.treatmentDays() < 0) {
            throw new BusinessException(q.getCode() + ": 치료 유형·일수를 확인하세요.");
        }
    }
}
