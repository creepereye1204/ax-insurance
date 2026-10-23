package com.ax.core.uw;

import com.ax.core.application.Application;
import com.ax.core.application.ApplicationCoverage;
import com.ax.core.application.ApplicationStatus;
import com.ax.core.application.DisclosureAnswer;
import com.ax.core.application.DisclosureForm;
import com.ax.core.application.DisclosureForm.AnswerInput;
import com.ax.core.common.BusinessException;
import com.ax.core.common.NotFoundException;
import com.ax.core.contract.ContractService;
import com.ax.core.customer.Consent;
import com.ax.core.customer.ConsentRepository;
import com.ax.core.customer.ConsentType;
import com.ax.core.customer.Customer;
import com.ax.core.customer.CustomerRepository;
import com.ax.core.disclosure.Questionnaire;
import com.ax.core.disclosure.QuestionnaireRepository;
import com.ax.core.product.Coverage;
import com.ax.core.product.CoverageCategory;
import com.ax.core.product.Product;
import com.ax.core.product.ProductRepository;
import com.ax.core.product.SaleStatus;
import com.ax.core.uw.engine.CoverageDecision;
import com.ax.core.uw.engine.CoverageOutcome;
import com.ax.core.uw.engine.MedicalRecord;
import com.ax.core.uw.engine.RuleEngine;
import com.ax.core.uw.engine.UwResult;
import com.ax.core.uw.rule.UwRuleSet;
import com.ax.core.uw.rule.UwRuleSetRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UwService {

    private final RuleEngine ruleEngine;
    private final UwRuleSetRepository ruleSetRepository;
    private final UwCaseRepository caseRepository;
    private final PreUwInquiryRepository inquiryRepository;
    private final UwInputFactory inputFactory;
    private final ContractService contractService;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final ConsentRepository consentRepository;
    private final QuestionnaireRepository questionnaireRepository;
    private final DisclosureForm disclosureForm;
    private final ApplicationEventPublisher events;

    // ---------------------------------------------------------------- 전산심사

    /**
     * 청약 제출 직후 실행. 결과에 따라
     * 본사 이관(REFER) / 연기 / 전 담보 거절 / 자동 승인 후 계약 성립 으로 분기한다.
     */
    @Transactional
    public UwCase autoUnderwrite(Application app) {
        UwRuleSet ruleSet = activeRuleSet();
        List<CoverageCategory> categories = app.getCoverages().stream()
                .map(c -> c.getCoverage().getCategory()).distinct().toList();
        UwResult result = ruleEngine.evaluate(ruleSet.getVersion(), ruleSet.getRules(),
                inputFactory.forApplication(app, categories));

        UwCase uwCase = new UwCase(app.getCustomer(), app.getProduct(), app, UwStage.AUTO_UW, ruleSet.getVersion());
        for (ApplicationCoverage ac : app.getCoverages()) {
            uwCase.addResult(toResult(ac.getCoverage(), ac.getInsuredAmount(), ac.getBasePremium(),
                    result.decisions().get(ac.getCoverage().getCategory())));
        }

        String summary;
        if (result.anyReferred()) {
            summary = "본사 이관";
            uwCase.referToManual(referReason(result));
            app.changeStatus(ApplicationStatus.UNDER_REVIEW);
        } else if (result.anyPostponed()) {
            summary = "연기";
            app.changeStatus(ApplicationStatus.POSTPONED);
        } else if (result.allDeclined()) {
            summary = "거절";
            app.changeStatus(ApplicationStatus.DECLINED);
        } else {
            summary = "자동 승인";
            contractService.issue(app, accepted(uwCase));
            app.changeStatus(ApplicationStatus.ISSUED);
        }
        uwCase.log(new UwDecisionLog(UwStage.AUTO_UW, "SYSTEM", summary, result.matchedRules(),
                "룰셋 v" + ruleSet.getVersion() + " 전산심사"));
        caseRepository.save(uwCase);

        if (result.anyReferred()) {
            events.publishEvent(new UwReferredEvent(uwCase.getId()));
        }
        return uwCase;
    }

    /** 전산심사 사전 실행: 저장하지 않고 룰 엔진 결과만 돌려준다 (청약 제출 전 확인용) */
    @Transactional(readOnly = true)
    public UwResult simulate(Application app) {
        UwRuleSet ruleSet = activeRuleSet();
        List<CoverageCategory> categories = app.getCoverages().stream()
                .map(c -> c.getCoverage().getCategory()).distinct().toList();
        return ruleEngine.evaluate(ruleSet.getVersion(), ruleSet.getRules(), inputFactory.forApplication(app, categories));
    }

    // ---------------------------------------------------------------- 본사심사

    public enum ManualChoice { ACCEPT_PROPOSED, ACCEPT_STANDARD, DECLINE }

    @Transactional
    public UwCase decideManually(Long caseId, Map<Long, ManualChoice> choices, String underwriter, String comment) {
        UwCase uwCase = get(caseId);
        uwCase.completeManual();

        for (UwCoverageResult r : uwCase.getResults()) {
            ManualChoice choice = choices.get(r.getCoverage().getId());
            if (choice == null) {
                throw new BusinessException("담보 '" + r.getCoverage().getName() + "'의 결정이 없습니다.");
            }
            switch (choice) {
                case ACCEPT_STANDARD -> r.decide(CoverageOutcome.STANDARD, List.of(), 0);
                case DECLINE -> r.decide(CoverageOutcome.DECLINE, List.of(), 0);
                case ACCEPT_PROPOSED -> r.decide(
                        r.getExclusions().isEmpty() && r.getRatingPct() == 0 ? CoverageOutcome.STANDARD : CoverageOutcome.CONDITIONAL,
                        r.getExclusions(), r.getRatingPct());
            }
        }

        Application app = uwCase.getApplication();
        List<UwCoverageResult> accepted = accepted(uwCase);
        String decision;
        if (accepted.isEmpty()) {
            decision = "거절";
            app.changeStatus(ApplicationStatus.DECLINED);
        } else {
            decision = accepted.size() == uwCase.getResults().size() ? "승인" : "일부 담보 승인";
            contractService.issue(app, accepted);
            app.changeStatus(ApplicationStatus.ISSUED);
        }
        String detail = uwCase.getResults().stream()
                .map(r -> r.getCoverage().getName() + "=" + r.getOutcome().getLabel())
                .collect(Collectors.joining(", "));
        uwCase.log(new UwDecisionLog(UwStage.MANUAL_UW, underwriter, decision, List.of(),
                detail + (comment == null || comment.isBlank() ? "" : " / " + comment)));
        return uwCase;
    }

    // ---------------------------------------------------------------- 선심사

    /**
     * 청약 전 예상 심사 (구속력 없음). 근거는 두 가지이고 하나 이상 있어야 한다.
     * <ul>
     *   <li>정보조회 동의가 있으면: 당사 청구이력 + 외부기관 의료이력 조회 (조회 기록은 PreUwInquiry 스냅샷)</li>
     *   <li>고지 응답을 주면: 상품 심사 유형에 맞는 질문지(표준 / 간편 3-2-5)로 검증한 고지 (케이스에 스냅샷)</li>
     * </ul>
     */
    @Transactional
    public UwCase preUnderwrite(Long customerId, Long productId, Long questionnaireId, List<AnswerInput> answers) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new NotFoundException("고객", customerId));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("상품", productId));
        Optional<Consent> consent = consentRepository.findFirstByCustomerIdAndTypeAndExpiresAtAfterOrderByAgreedAtDesc(
                customerId, ConsentType.PRE_UW_INQUIRY, LocalDateTime.now());
        boolean disclosed = answers != null && !answers.isEmpty();
        if (consent.isEmpty() && !disclosed) {
            throw new BusinessException("선심사 근거가 없습니다. 고객의 정보조회 동의를 받거나 알릴의무 고지를 입력하세요.");
        }

        List<DisclosureAnswer> disclosure = List.of();
        PreDisclosure snapshot = null;
        if (disclosed) {
            Questionnaire questionnaire = questionnaireRepository.findById(questionnaireId == null ? -1L : questionnaireId)
                    .orElseThrow(() -> new NotFoundException("질문지", questionnaireId));
            disclosureForm.checkFor(questionnaire, product);
            disclosure = disclosureForm.build(questionnaire, answers, LocalDate.now());
            Map<String, Boolean> byCode = new LinkedHashMap<>();
            disclosure.forEach(a -> byCode.put(a.getQuestion().getCode(), a.isAnswer()));
            snapshot = new PreDisclosure(questionnaire.getCode() + " v" + questionnaire.getVersion(), byCode,
                    inputFactory.disclosedRecords(disclosure));
        }

        UwRuleSet ruleSet = activeRuleSet();
        List<MedicalRecord> history = consent.map(c -> inputFactory.historyOf(customer)).orElse(List.of());
        List<CoverageCategory> categories = product.getCoverages().stream().map(Coverage::getCategory).distinct().toList();
        UwResult result = ruleEngine.evaluate(ruleSet.getVersion(), ruleSet.getRules(),
                inputFactory.forPreUw(customer, product, history, disclosure, categories));

        UwCase uwCase = new UwCase(customer, product, null, UwStage.PRE_UW, ruleSet.getVersion());
        for (Coverage coverage : product.getCoverages()) {
            uwCase.addResult(toResult(coverage, coverage.getDefaultAmount(), 0, result.decisions().get(coverage.getCategory())));
        }
        uwCase.recordPreDisclosure(snapshot);
        String basis = (consent.isPresent() ? "조회 이력 " + history.size() + "건" : "정보조회 동의 없음")
                + (snapshot == null ? ", 고지 미입력" : ", 고지 " + snapshot.answers().size() + "문항('예' " + snapshot.yesCount() + ")")
                + (product.isSimplifiedUw() ? ", 간편심사 기준" : ", 표준심사 기준");
        uwCase.log(new UwDecisionLog(UwStage.PRE_UW, "SYSTEM", "예상 결과 산출 — " + result.signal().getColor(),
                result.matchedRules(), basis + " / 청약 후 전산심사에서 확정"));
        caseRepository.save(uwCase);
        consent.ifPresent(c -> inquiryRepository.save(new PreUwInquiry(uwCase, c, history)));
        return uwCase;
    }

    /**
     * 비교설계 대상: 같은 유형에서 심사 방식(표준↔간편)이 반대인 판매 중 상품.
     * 고객 보험나이로 가입할 수 있어야 하고, 담보 분류가 많이 겹칠수록 · 최근 출시일수록 우선한다.
     */
    @Transactional(readOnly = true)
    public Optional<Product> counterpartOf(Long productId, Long customerId) {
        Product base = productRepository.findById(productId).orElseThrow(() -> new NotFoundException("상품", productId));
        Customer customer = customerRepository.findById(customerId).orElseThrow(() -> new NotFoundException("고객", customerId));
        int age = customer.insuranceAgeAt(LocalDate.now());
        List<CoverageCategory> categories = base.getCoverages().stream().map(Coverage::getCategory).distinct().toList();
        Comparator<Product> overlap = Comparator.comparingLong(
                p -> p.getCoverages().stream().map(Coverage::getCategory).distinct().filter(categories::contains).count());
        return productRepository.findByTypeAndSimplifiedUwAndSaleStatus(base.getType(), !base.isSimplifiedUw(), SaleStatus.ON_SALE)
                .stream()
                .filter(p -> p.acceptsAge(age))
                .max(overlap.thenComparing(Product::getLaunchedOn, Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    /** 비교설계는 조회 이력으로만 판단한다 (고지 문항이 표준·간편 질문지마다 달라 같은 답을 쓸 수 없다) */
    @Transactional(readOnly = true)
    public boolean hasInquiryConsent(Long customerId) {
        return consentRepository.findFirstByCustomerIdAndTypeAndExpiresAtAfterOrderByAgreedAtDesc(
                customerId, ConsentType.PRE_UW_INQUIRY, LocalDateTime.now()).isPresent();
    }

    // ----------------------------------------------------------------

    @Transactional(readOnly = true)
    public UwCase get(Long id) {
        return caseRepository.findById(id).orElseThrow(() -> new NotFoundException("심사 건", id));
    }

    private UwRuleSet activeRuleSet() {
        return ruleSetRepository.findFirstByActiveTrueOrderByVersionDesc()
                .orElseThrow(() -> new IllegalStateException("활성화된 심사 룰셋이 없습니다."));
    }

    private static UwCoverageResult toResult(Coverage coverage, long amount, long basePremium, CoverageDecision d) {
        return new UwCoverageResult(coverage, amount, basePremium, d.outcome(), d.exclusions(), d.ratingPct(),
                d.matchedRules(), d.reasons());
    }

    private static List<UwCoverageResult> accepted(UwCase uwCase) {
        return uwCase.getResults().stream().filter(r -> r.getOutcome().isAccepted()).toList();
    }

    private static String referReason(UwResult result) {
        return result.decisions().values().stream()
                .filter(d -> d.outcome() == CoverageOutcome.REFER)
                .flatMap(d -> d.reasons().stream())
                .distinct()
                .collect(Collectors.joining(" / "));
    }
}
