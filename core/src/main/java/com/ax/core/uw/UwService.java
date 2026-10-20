package com.ax.core.uw;

import com.ax.core.application.Application;
import com.ax.core.application.ApplicationCoverage;
import com.ax.core.application.ApplicationStatus;
import com.ax.core.common.BusinessException;
import com.ax.core.common.NotFoundException;
import com.ax.core.contract.ContractService;
import com.ax.core.customer.Consent;
import com.ax.core.customer.ConsentRepository;
import com.ax.core.customer.ConsentType;
import com.ax.core.customer.Customer;
import com.ax.core.customer.CustomerRepository;
import com.ax.core.product.Coverage;
import com.ax.core.product.CoverageCategory;
import com.ax.core.product.Product;
import com.ax.core.product.ProductRepository;
import com.ax.core.uw.engine.CoverageDecision;
import com.ax.core.uw.engine.CoverageOutcome;
import com.ax.core.uw.engine.MedicalRecord;
import com.ax.core.uw.engine.RuleEngine;
import com.ax.core.uw.engine.UwResult;
import com.ax.core.uw.rule.UwRuleSet;
import com.ax.core.uw.rule.UwRuleSetRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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

    /** 청약 전, 동의 기반으로 청구이력·외부기관 이력을 조회해 담보별 예상 결과를 낸다 (구속력 없음). */
    @Transactional
    public UwCase preUnderwrite(Long customerId, Long productId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new NotFoundException("고객", customerId));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("상품", productId));
        Consent consent = consentRepository.findFirstByCustomerIdAndTypeAndExpiresAtAfterOrderByAgreedAtDesc(
                        customerId, ConsentType.PRE_UW_INQUIRY, LocalDateTime.now())
                .orElseThrow(() -> new BusinessException("유효한 정보조회 동의가 없습니다. 선심사 전에 고객 동의를 받아야 합니다."));

        UwRuleSet ruleSet = activeRuleSet();
        List<MedicalRecord> history = inputFactory.historyOf(customer);
        List<CoverageCategory> categories = product.getCoverages().stream().map(Coverage::getCategory).distinct().toList();
        UwResult result = ruleEngine.evaluate(ruleSet.getVersion(), ruleSet.getRules(),
                inputFactory.forPreUw(customer, history, categories));

        UwCase uwCase = new UwCase(customer, product, null, UwStage.PRE_UW, ruleSet.getVersion());
        for (Coverage coverage : product.getCoverages()) {
            uwCase.addResult(toResult(coverage, coverage.getDefaultAmount(), 0, result.decisions().get(coverage.getCategory())));
        }
        uwCase.log(new UwDecisionLog(UwStage.PRE_UW, "SYSTEM", "예상 결과 산출", result.matchedRules(),
                "조회 이력 " + history.size() + "건, 고지 전 예상이므로 청약 후 전산심사에서 확정"));
        caseRepository.save(uwCase);
        inquiryRepository.save(new PreUwInquiry(uwCase, consent, history));
        return uwCase;
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
