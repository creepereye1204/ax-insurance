package com.ax.core.application;

import com.ax.core.application.ApplicationService.AnswerInput;
import com.ax.core.application.ApplicationService.CoverageInput;
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

    public record SubmitRequest(@NotNull Long customerId, @NotNull Long productId, @NotNull Long questionnaireId,
                                @NotEmpty List<CoverageInput> coverages, @NotEmpty List<AnswerInput> answers) {
    }

    public record DetailResponse(String kcdCode, LocalDate diagnosedOn, String treatment, int treatmentDays, boolean cured) {
    }

    public record AnswerResponse(String questionCode, String questionText, boolean answer, List<DetailResponse> details) {
    }

    public record Summary(Long id, String applicationNo, Long customerId, String customerName, String productName,
                          ApplicationStatus status, String statusLabel, LocalDateTime submittedAt) {
        public static Summary from(Application a) {
            return new Summary(a.getId(), a.getApplicationNo(), a.getCustomer().getId(), a.getCustomer().getName(),
                    a.getProduct().getName(), a.getStatus(), a.getStatus().getLabel(), a.getSubmittedAt());
        }
    }

    public record Detail(Summary summary, int questionnaireVersion, List<AnswerResponse> answers,
                         UwController.CaseResponse uwCase, Long contractId) {
    }

    @PostMapping
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public Detail submit(@Valid @RequestBody SubmitRequest req) {
        var submitted = applicationService.submit(req.customerId(), req.productId(), req.questionnaireId(),
                req.coverages(), req.answers());
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
        List<AnswerResponse> answers = a.getAnswers().stream()
                .map(x -> new AnswerResponse(x.getQuestion().getCode(), x.getQuestion().getText(), x.isAnswer(),
                        x.getDetails().stream().map(d -> new DetailResponse(d.getKcdCode(), d.getDiagnosedOn(),
                                d.getTreatment().getLabel(), d.getTreatmentDays(), d.isCured())).toList()))
                .toList();
        var uwCase = uwCaseRepository.findFirstByApplicationIdOrderByIdDesc(a.getId())
                .map(UwController.CaseResponse::from).orElse(null);
        Long contractId = contractRepository.findByApplicationId(a.getId()).map(c -> c.getId()).orElse(null);
        return new Detail(Summary.from(a), a.getQuestionnaire().getVersion(), answers, uwCase, contractId);
    }
}
