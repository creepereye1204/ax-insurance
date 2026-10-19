package com.ax.core.uw;

import com.ax.core.uw.UwService.ManualChoice;
import com.ax.core.uw.engine.Exclusion;
import com.ax.core.uw.engine.MedicalRecord;
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
                                 String outcomeLabel, List<Exclusion> exclusions, int ratingPct,
                                 List<String> matchedRules, List<String> reasons) {
        static ResultResponse from(UwCoverageResult r) {
            return new ResultResponse(r.getCoverage().getId(), r.getCoverage().getName(), r.getInsuredAmount(),
                    r.getOutcome().name(), r.getOutcome().getLabel(), r.getExclusions(), r.getRatingPct(),
                    r.getMatchedRules(), r.getReasons());
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
                               Long customerId, String customerName, String productName,
                               Long applicationId, String applicationNo, int ruleSetVersion,
                               String referReason, String aiOpinion, LocalDateTime createdAt,
                               List<ResultResponse> results, List<LogResponse> logs) {
        public static CaseResponse from(UwCase c) {
            return new CaseResponse(c.getId(), c.getStage().name(), c.getStage().getLabel(), c.getStatus().name(),
                    c.getStatus().getLabel(), c.getCustomer().getId(), c.getCustomer().getName(), c.getProduct().getName(),
                    c.getApplication() == null ? null : c.getApplication().getId(),
                    c.getApplication() == null ? null : c.getApplication().getApplicationNo(),
                    c.getRuleSetVersion(), c.getReferReason(), c.getAiOpinion(), c.getCreatedAt(),
                    c.getResults().stream().map(ResultResponse::from).toList(),
                    c.getLogs().stream().map(LogResponse::from).toList());
        }
    }

    public record PreUwResponse(CaseResponse uwCase, List<MedicalRecord> inquired) {
    }

    public record PreUwRequest(@NotNull Long customerId, @NotNull Long productId) {
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
        UwCase c = uwService.preUnderwrite(req.customerId(), req.productId());
        List<MedicalRecord> inquired = inquiryRepository.findByUwCaseId(c.getId())
                .map(PreUwInquiry::getSnapshot).orElse(List.of());
        return new PreUwResponse(CaseResponse.from(c), inquired);
    }
}
