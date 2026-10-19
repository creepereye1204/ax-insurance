package com.ax.core.contract;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 계약은 청약 → 심사를 거쳐서만 생성된다 (직접 생성 API 없음). */
@RestController
@RequestMapping("/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;
    private final ContractRepository contractRepository;

    public record CoverageResponse(String name, String categoryLabel, long insuredAmount, int ratingPct,
                                   List<ContractExclusion> exclusions, long monthlyPremium) {
        static CoverageResponse from(ContractCoverage c) {
            return new CoverageResponse(c.getCoverage().getName(), c.getCoverage().getCategory().getLabel(),
                    c.getInsuredAmount(), c.getRatingPct(), c.getExclusions(), c.getMonthlyPremium());
        }
    }

    public record Response(Long id, String contractNo, Long customerId, String customerName,
                           String productName, ContractStatus status, LocalDate startDate, LocalDate endDate,
                           long monthlyPremium, String applicationNo, List<CoverageResponse> coverages) {
        public static Response from(Contract c) {
            return new Response(c.getId(), c.getContractNo(), c.getCustomer().getId(), c.getCustomer().getName(),
                    c.getProduct().getName(), c.getStatus(), c.getStartDate(), c.getEndDate(), c.getMonthlyPremium(),
                    c.getApplication() == null ? null : c.getApplication().getApplicationNo(),
                    c.getCoverages().stream().map(CoverageResponse::from).toList());
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Response> list(@RequestParam(required = false) Long customerId) {
        List<Contract> found = customerId == null
                ? contractRepository.findTop100ByOrderByIdDesc()
                : contractRepository.findByCustomerIdOrderByIdDesc(customerId);
        return found.stream().map(Response::from).toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public Response get(@PathVariable Long id) {
        return Response.from(contractService.get(id));
    }

    @PostMapping("/{id}/terminate")
    @Transactional
    public Response terminate(@PathVariable Long id) {
        return Response.from(contractService.terminate(id));
    }
}
