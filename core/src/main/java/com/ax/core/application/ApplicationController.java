package com.ax.core.application;

import com.ax.core.application.ApplicationService.AnswerInput;
import com.ax.core.application.ApplicationService.Signature;
import com.ax.core.contract.ContractRepository;
import com.ax.core.uw.UwCaseRepository;
import com.ax.core.uw.UwController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;
    private final UwCaseRepository uwCaseRepository;
    private final ContractRepository contractRepository;

    public record PreviewRequest(@NotNull Long proposalId, @NotNull Long questionnaireId, @NotEmpty List<AnswerInput> answers) {
    }

    public record SubmitRequest(@NotNull Long proposalId, @NotNull Long questionnaireId, @NotEmpty List<AnswerInput> answers,
                                @NotNull Signature signature) {
    }

    public record DetailResponse(String kcdCode, LocalDate diagnosedOn, String treatment, int treatmentDays, boolean cured) {
    }

    public record AnswerResponse(String questionCode, String questionText, boolean answer, List<DetailResponse> details) {
    }

    public record CoverageResponse(String name, String categoryLabel, long insuredAmount, long basePremium) {
    }

    public record AgreementResponse(String code, String text) {
        static AgreementResponse from(Agreement a) {
            return new AgreementResponse(a.name(), a.getText());
        }
    }

    /** 청약서 본문 (미리보기와 서명 후 조회가 같은 형태) */
    public record Document(String applicationNo, String proposalNo, Long customerId, String customerName,
                           LocalDate birthDate, String gender, String consumerType, boolean protectedSenior,
                           String productName, String productType, String insurancePeriod, int paymentTerm,
                           long monthlyPremium, Long monthlyContribution, Integer annuityStartAge, String suitability,
                           List<CoverageResponse> coverages, int questionnaireVersion, List<AnswerResponse> answers,
                           List<AgreementResponse> requiredAgreements, String documentHash) {
        static Document of(Application a, List<Agreement> required, String hash) {
            var c = a.getCustomer();
            var p = a.getProposal();
            return new Document(a.getApplicationNo(), p == null ? null : p.getProposalNo(), c.getId(), c.getName(),
                    c.getBirthDate(), c.getGender().name(), c.getConsumerType().getLabel(),
                    c.isProtectedSenior(a.getSubmittedAt().toLocalDate()), a.getProduct().getName(),
                    a.getProduct().getType().getLabel(), a.getProduct().getInsurancePeriod(), a.getPaymentTerm(),
                    a.getMonthlyPremium(), p == null ? null : p.getMonthlyContribution(),
                    p == null ? null : p.getAnnuityStartAge(), p == null ? null : p.getSuitability().getLabel(),
                    a.getCoverages().stream().map(x -> new CoverageResponse(x.getCoverage().getName(),
                            x.getCoverage().getCategory().getLabel(), x.getInsuredAmount(), x.getBasePremium())).toList(),
                    a.getQuestionnaire().getVersion(),
                    a.getAnswers().stream().map(x -> new AnswerResponse(x.getQuestion().getCode(), x.getQuestion().getText(),
                            x.isAnswer(), x.getDetails().stream().map(d -> new DetailResponse(d.getKcdCode(),
                                    d.getDiagnosedOn(), d.getTreatment().getLabel(), d.getTreatmentDays(), d.isCured())).toList()))
                            .toList(),
                    required.stream().map(AgreementResponse::from).toList(), hash);
        }
    }

    public record Summary(Long id, String applicationNo, Long customerId, String customerName, String productName,
                          ApplicationStatus status, String statusLabel, long monthlyPremium, LocalDateTime submittedAt) {
        public static Summary from(Application a) {
            return new Summary(a.getId(), a.getApplicationNo(), a.getCustomer().getId(), a.getCustomer().getName(),
                    a.getProduct().getName(), a.getStatus(), a.getStatus().getLabel(), a.getMonthlyPremium(), a.getSubmittedAt());
        }
    }

    public record SignatureResponse(String signerName, LocalDateTime signedAt, List<AgreementResponse> agreements,
                                    String documentHash) {
    }

    public record Detail(Summary summary, Document document, SignatureResponse signature,
                         UwController.CaseResponse uwCase, Long contractId) {
    }

    @PostMapping("/preview")
    @Transactional(readOnly = true)
    public Document preview(@Valid @RequestBody PreviewRequest req) {
        var draft = applicationService.preview(req.proposalId(), req.questionnaireId(), req.answers());
        return Document.of(draft.application(), draft.requiredAgreements(), draft.documentHash());
    }

    @PostMapping
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public Detail submit(@Valid @RequestBody SubmitRequest req) {
        var submitted = applicationService.submit(req.proposalId(), req.questionnaireId(), req.answers(), req.signature());
        return detail(submitted.application());
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Summary> list(@RequestParam(required = false) Long customerId) {
        return applicationService.list(customerId).stream().map(Summary::from).toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public Detail get(@PathVariable Long id) {
        return detail(applicationService.get(id));
    }

    private Detail detail(Application a) {
        List<Agreement> agreed = a.getAgreements() == null ? List.of() : a.getAgreements();
        var signature = a.getSignedAt() == null ? null : new SignatureResponse(a.getSignerName(), a.getSignedAt(),
                agreed.stream().map(AgreementResponse::from).toList(), a.getDocumentHash());
        var uwCase = uwCaseRepository.findFirstByApplicationIdOrderByIdDesc(a.getId())
                .map(UwController.CaseResponse::from).orElse(null);
        Long contractId = contractRepository.findByApplicationId(a.getId()).map(c -> c.getId()).orElse(null);
        return new Detail(Summary.from(a), Document.of(a, agreed, a.getDocumentHash()), signature, uwCase, contractId);
    }
}
