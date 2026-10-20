package com.ax.core.proposal;

import com.ax.core.proposal.PremiumCalculator.CoverageAmount;
import jakarta.validation.Valid;
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
@RequestMapping("/proposals")
@RequiredArgsConstructor
public class ProposalController {

    private final ProposalService proposalService;

    public record DesignRequest(@NotNull Long customerId, @NotNull Long productId, @NotNull Integer paymentTerm,
                                List<CoverageAmount> coverages, Long monthlyContribution, Integer annuityStartAge,
                                List<FundAllocation> funds, boolean unsuitableAcknowledged) {
        PremiumCalculator.Input input() {
            return new PremiumCalculator.Input(paymentTerm, coverages, monthlyContribution, annuityStartAge, funds,
                    unsuitableAcknowledged);
        }
    }

    public record CoverageLine(Long coverageId, String name, String categoryLabel, boolean required,
                               long insuredAmount, long monthlyPremium) {
    }

    public record QuoteResponse(int age, int paymentTerm, List<CoverageLine> coverages, Long monthlyContribution,
                                Integer annuityStartAge, List<FundAllocation> funds, long monthlyPremium,
                                Illustration illustration, String suitability, String suitabilityLabel,
                                List<String> suitabilityIssues) {
        static QuoteResponse from(PremiumCalculator.Quote q) {
            return new QuoteResponse(q.age(), q.paymentTerm(), q.coverages().stream()
                    .map(c -> new CoverageLine(c.coverage().getId(), c.coverage().getName(),
                            c.coverage().getCategory().getLabel(), c.coverage().isRequired(), c.amount(), c.monthlyPremium()))
                    .toList(), q.monthlyContribution(), q.annuityStartAge(), q.funds(), q.monthlyPremium(),
                    q.illustration(), q.suitability().name(), q.suitability().getLabel(), q.suitabilityIssues());
        }
    }

    public record Response(Long id, String proposalNo, String status, String statusLabel, Long customerId,
                           String customerName, String consumerType, Long productId, String productName,
                           String productType, String insurancePeriod, int insuredAge, int paymentTerm,
                           Long monthlyContribution, Integer annuityStartAge, List<FundAllocation> funds,
                           long monthlyPremium, Illustration illustration, String suitabilityLabel,
                           List<String> suitabilityIssues, LocalDateTime issuedAt, LocalDate validUntil,
                           boolean expired, List<CoverageLine> coverages) {
        public static Response from(Proposal p) {
            return new Response(p.getId(), p.getProposalNo(), p.getStatus().name(), p.getStatus().getLabel(),
                    p.getCustomer().getId(), p.getCustomer().getName(), p.getCustomer().getConsumerType().getLabel(),
                    p.getProduct().getId(), p.getProduct().getName(), p.getProduct().getType().getLabel(),
                    p.getProduct().getInsurancePeriod(), p.getInsuredAge(), p.getPaymentTerm(),
                    p.getMonthlyContribution(), p.getAnnuityStartAge(), p.getFunds(), p.getMonthlyPremium(),
                    p.getIllustration(), p.getSuitability().getLabel(), p.getSuitabilityIssues(), p.getIssuedAt(),
                    p.getValidUntil(), p.isExpired(LocalDate.now()),
                    p.getCoverages().stream().map(c -> new CoverageLine(c.getCoverage().getId(), c.getCoverage().getName(),
                            c.getCoverage().getCategory().getLabel(), c.getCoverage().isRequired(), c.getInsuredAmount(),
                            c.getMonthlyPremium())).toList());
        }
    }

    public record Summary(Long id, String proposalNo, String statusLabel, String customerName, String productName,
                          long monthlyPremium, LocalDateTime issuedAt, LocalDate validUntil) {
        public static Summary from(Proposal p) {
            return new Summary(p.getId(), p.getProposalNo(), p.getStatus().getLabel(), p.getCustomer().getName(),
                    p.getProduct().getName(), p.getMonthlyPremium(), p.getIssuedAt(), p.getValidUntil());
        }
    }

    @PostMapping("/quote")
    public QuoteResponse quote(@Valid @RequestBody DesignRequest req) {
        return QuoteResponse.from(proposalService.quote(req.customerId(), req.productId(), req.input()));
    }

    @PostMapping
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public Response issue(@Valid @RequestBody DesignRequest req) {
        return Response.from(proposalService.issue(req.customerId(), req.productId(), req.input()));
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Summary> list(@RequestParam(required = false) Long customerId) {
        return proposalService.list(customerId).stream().map(Summary::from).toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public Response get(@PathVariable Long id) {
        return Response.from(proposalService.get(id));
    }
}
