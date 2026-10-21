package com.ax.core.uw;

import com.ax.core.application.DisclosureForm.AnswerInput;
import com.ax.core.uw.UwService.ManualChoice;
import com.ax.core.uw.engine.Exclusion;
import com.ax.core.uw.engine.MedicalRecord;
import com.ax.core.uw.engine.Signal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/uw")
@RequiredArgsConstructor
public class UwController {

    private final UwService uwService;
    private final UwCaseRepository caseRepository;
    private final PreUwInquiryRepository inquiryRepository;

    public record ResultResponse(Long coverageId, String coverageName, long insuredAmount, String outcome,
                                 String outcomeLabel, Signal signal, List<Exclusion> exclusions, int ratingPct,
                                 List<String> matchedRules, List<String> reasons) {
        static ResultResponse from(UwCoverageResult r) {
            return new ResultResponse(r.getCoverage().getId(), r.getCoverage().getName(), r.getInsuredAmount(),
                    r.getOutcome().name(), r.getOutcome().getLabel(), Signal.of(r.getOutcome()), r.getExclusions(),
                    r.getRatingPct(), r.getMatchedRules(), r.getReasons());
        }
    }

    public record LogResponse(String stage, String actor, String decision, List<String> matchedRules,
                              String reason, LocalDateTime createdAt) {
        static LogResponse from(UwDecisionLog l) {
            return new LogResponse(l.getStage().getLabel(), l.getActor(), l.getDecision(), l.getMatchedRules(),
                    l.getReason(), l.getCreatedAt());
        }
    }

    public record CaseResponse(Long id, String stage, String stageLabel, String status, String statusLabel,
                               Long customerId, String customerName, Long productId, String productName,
                               boolean simplifiedUw, Long applicationId, String applicationNo, int ruleSetVersion,
                               Signal signal, String signalLabel, String referReason, String aiOpinion,
                               PreDisclosure preDisclosure, LocalDateTime createdAt,
                               List<ResultResponse> results, List<LogResponse> logs) {
        public static CaseResponse from(UwCase c) {
            Signal signal = Signal.overall(c.getResults().stream().map(UwCoverageResult::getOutcome).toList());
            return new CaseResponse(c.getId(), c.getStage().name(), c.getStage().getLabel(), c.getStatus().name(),
                    c.getStatus().getLabel(), c.getCustomer().getId(), c.getCustomer().getName(), c.getProduct().getId(),
                    c.getProduct().getName(), c.getProduct().isSimplifiedUw(),
                    c.getApplication() == null ? null : c.getApplication().getId(),
                    c.getApplication() == null ? null : c.getApplication().getApplicationNo(),
                    c.getRuleSetVersion(), signal, signal.getLabel(), c.getReferReason(), c.getAiOpinion(),
                    c.getPreDisclosure(), c.getCreatedAt(),
                    c.getResults().stream().map(ResultResponse::from).toList(),
                    c.getLogs().stream().map(LogResponse::from).toList());
        }
    }

    /** hint: 설계사 안내 (예: 표준심사에서 조건이 붙으면 간편심사형 검토) */
    public record PreUwResponse(CaseResponse uwCase, List<MedicalRecord> inquired, String hint) {
    }

    /** answers 를 주면 고지 기반 선심사 (questionnaireId 필수) */
    public record PreUwRequest(@NotNull Long customerId, @NotNull Long productId, Long questionnaireId,
                               List<AnswerInput> answers) {
    }

    public record DecisionRequest(@NotEmpty Map<Long, ManualChoice> choices, @NotBlank String underwriter, String comment) {
    }

    /** 본사 심사 대기함 */
    @GetMapping("/cases")
    @Transactional(readOnly = true)
    public List<CaseResponse> pending(@RequestParam(defaultValue = "MANUAL_UW") UwStage stage,
                                      @RequestParam(defaultValue = "PENDING") UwCaseStatus status) {
        return caseRepository.findByStageAndStatusOrderByIdAsc(stage, status).stream().map(CaseResponse::from).toList();
    }

    @GetMapping("/cases/{id}")
    @Transactional(readOnly = true)
    public CaseResponse get(@PathVariable Long id) {
        return CaseResponse.from(uwService.get(id));
    }

    @PostMapping("/cases/{id}/decision")
    @Transactional
    public CaseResponse decide(@PathVariable Long id, @Valid @RequestBody DecisionRequest req) {
        return CaseResponse.from(uwService.decideManually(id, req.choices(), req.underwriter(), req.comment()));
    }

    @PostMapping("/pre")
    @Transactional
    public PreUwResponse preUnderwrite(@Valid @RequestBody PreUwRequest req) {
        UwCase c = uwService.preUnderwrite(req.customerId(), req.productId(), req.questionnaireId(), req.answers());
        List<MedicalRecord> inquired = inquiryRepository.findByUwCaseId(c.getId())
                .map(PreUwInquiry::getSnapshot).orElse(List.of());
        CaseResponse response = CaseResponse.from(c);
        return new PreUwResponse(response, inquired, hintFor(response));
    }

    private static String hintFor(CaseResponse c) {
        if (c.signal() == Signal.GREEN) {
            return "예상 결과대로라면 청약 후 전산심사에서 자동 승인됩니다. 고지는 사실대로 해야 합니다.";
        }
        if (!c.simplifiedUw()) {
            return "표준심사 기준으로 조건이 붙거나 인수가 어렵습니다. 간편심사형(3-2-5) 상품은 고지 범위가 좁아 인수될 수 있습니다(보험료는 더 높음).";
        }
        return c.signal() == Signal.RED
                ? "간편고지 항목에 해당해 간편심사형으로도 가입이 어렵습니다. 치료 종결 후 다시 검토하세요."
                : "조건부 인수 또는 본사 심사가 예상됩니다.";
    }
}
