package com.ax.core.claim;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
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
@RequestMapping("/claims")
@RequiredArgsConstructor
public class ClaimController {

    private final ClaimService claimService;
    private final ClaimRepository claimRepository;

    public record SubmitRequest(@NotNull Long contractId, @NotNull ClaimType type,
                                @NotNull @PastOrPresent LocalDate treatmentDate,
                                @NotBlank String diagnosis, String kcdCode, @Positive long amount) {
    }

    public record ApproveRequest(@Positive long paidAmount, String comment) {
    }

    public record RejectRequest(@NotBlank String comment) {
    }

    public record Response(Long id, String claimNo, Long contractId, String contractNo, String customerName,
                           ClaimType type, LocalDate treatmentDate, String diagnosis, String kcdCode,
                           long claimedAmount, ClaimStatus status, List<String> checkNotes,
                           String aiOpinion, Long aiRunId,
                           Long paidAmount, String reviewerComment, LocalDateTime decidedAt) {
        static Response from(Claim c) {
            return new Response(c.getId(), c.getClaimNo(), c.getContract().getId(), c.getContract().getContractNo(),
                    c.getContract().getCustomer().getName(), c.getType(), c.getTreatmentDate(), c.getDiagnosis(),
                    c.getKcdCode(), c.getClaimedAmount(), c.getStatus(), c.getCheckNotes(),
                    c.getAiOpinion(), c.getAiRunId(), c.getPaidAmount(), c.getReviewerComment(), c.getDecidedAt());
        }
    }

    @PostMapping
    @Transactional
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Response submit(@Valid @RequestBody SubmitRequest req) {
        return Response.from(claimService.submit(
                req.contractId(), req.type(), req.treatmentDate(), req.diagnosis(),
                req.kcdCode() == null || req.kcdCode().isBlank() ? null : req.kcdCode().trim(), req.amount()));
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Response> list(@RequestParam(required = false) Long customerId) {
        List<Claim> found = customerId == null
                ? claimRepository.findTop100ByOrderByIdDesc()
                : claimRepository.findByContractCustomerIdOrderByIdDesc(customerId);
        return found.stream().map(Response::from).toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public Response get(@PathVariable Long id) {
        return Response.from(claimService.get(id));
    }

    @PostMapping("/{id}/approve")
    @Transactional
    public Response approve(@PathVariable Long id, @Valid @RequestBody ApproveRequest req) {
        return Response.from(claimService.approve(id, req.paidAmount(), req.comment()));
    }

    @PostMapping("/{id}/reject")
    @Transactional
    public Response reject(@PathVariable Long id, @Valid @RequestBody RejectRequest req) {
        return Response.from(claimService.reject(id, req.comment()));
    }
}
