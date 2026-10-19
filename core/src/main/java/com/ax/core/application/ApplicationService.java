package com.ax.core.application;

import com.ax.core.common.BusinessException;
import com.ax.core.common.NotFoundException;
import com.ax.core.common.TreatmentType;
import com.ax.core.customer.Customer;
import com.ax.core.customer.CustomerRepository;
import com.ax.core.disclosure.Question;
import com.ax.core.disclosure.Questionnaire;
import com.ax.core.disclosure.QuestionnaireRepository;
import com.ax.core.kcd.KcdRepository;
import com.ax.core.product.Coverage;
import com.ax.core.product.Product;
import com.ax.core.product.ProductRepository;
import com.ax.core.uw.UwCase;
import com.ax.core.uw.UwService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private static final long MIN_AMOUNT = 10_000_000;
    private static final long MAX_AMOUNT = 100_000_000;

    private final ApplicationRepository applicationRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final QuestionnaireRepository questionnaireRepository;
    private final KcdRepository kcdRepository;
    private final UwService uwService;

    public record CoverageInput(Long coverageId, long insuredAmount) {
    }

    public record DetailInput(String kcdCode, LocalDate diagnosedOn, TreatmentType treatment, int treatmentDays, boolean cured) {
    }

    public record AnswerInput(Long questionId, boolean answer, List<DetailInput> details) {
    }

    public record Submitted(Application application, UwCase uwCase) {
    }

    /** 청약 제출: 입력 검증 → 저장 → 전산심사까지 한 트랜잭션으로 처리한다. */
    @Transactional
    public Submitted submit(Long customerId, Long productId, Long questionnaireId,
                            List<CoverageInput> coverageInputs, List<AnswerInput> answerInputs) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new NotFoundException("고객", customerId));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("상품", productId));
        Questionnaire questionnaire = questionnaireRepository.findById(questionnaireId)
                .orElseThrow(() -> new NotFoundException("질문지", questionnaireId));

        // 가입나이 초과도 여기서 막지 않고 심사 룰(R-AGE-01)로 거절해 심사 기록을 남긴다
        LocalDateTime now = LocalDateTime.now();
        Application app = new Application(newApplicationNo(), customer, product, questionnaire, now);
        addCoverages(app, product, coverageInputs);
        addAnswers(app, questionnaire, answerInputs, now.toLocalDate());
        applicationRepository.save(app);

        return new Submitted(app, uwService.autoUnderwrite(app));
    }

    private void addCoverages(Application app, Product product, List<CoverageInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw new BusinessException("가입할 담보를 하나 이상 선택해야 합니다.");
        }
        Map<Long, Coverage> coverages = product.getCoverages().stream()
                .collect(Collectors.toMap(Coverage::getId, Function.identity()));
        Set<Long> seen = new HashSet<>();
        for (CoverageInput in : inputs) {
            Coverage coverage = coverages.get(in.coverageId());
            if (coverage == null) {
                throw new BusinessException("상품에 없는 담보입니다: " + in.coverageId());
            }
            if (!seen.add(in.coverageId())) {
                throw new BusinessException("같은 담보를 중복 선택했습니다: " + coverage.getName());
            }
            if (in.insuredAmount() < MIN_AMOUNT || in.insuredAmount() > MAX_AMOUNT || in.insuredAmount() % MIN_AMOUNT != 0) {
                throw new BusinessException(coverage.getName() + " 가입금액은 1천만원 단위, 1천만원~1억원이어야 합니다.");
            }
            app.addCoverage(new ApplicationCoverage(coverage, in.insuredAmount()));
        }
    }

    /** 모든 문항에 답해야 하고, "예"인 상세 필요 문항은 상세가 1건 이상, 진단일은 문항의 조회기간 안이어야 한다. */
    private void addAnswers(Application app, Questionnaire questionnaire, List<AnswerInput> inputs, LocalDate today) {
        Map<Long, AnswerInput> byQuestion = inputs == null ? Map.of()
                : inputs.stream().collect(Collectors.toMap(AnswerInput::questionId, Function.identity(), (a, b) -> a));

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
            app.addAnswer(answer);
        }
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

    @Transactional(readOnly = true)
    public Application get(Long id) {
        return applicationRepository.findById(id).orElseThrow(() -> new NotFoundException("청약", id));
    }

    @Transactional(readOnly = true)
    public List<Application> list(Long customerId) {
        return customerId == null
                ? applicationRepository.findTop100ByOrderByIdDesc()
                : applicationRepository.findByCustomerIdOrderByIdDesc(customerId);
    }

    private String newApplicationNo() {
        return "AP" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }
}
