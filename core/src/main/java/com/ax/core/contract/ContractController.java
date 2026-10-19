package com.ax.core.contract;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;

    public record CreateRequest(@NotNull Long customerId, @NotNull Long productId,
                                @NotNull LocalDate startDate, @NotNull LocalDate endDate,
                                @Positive long monthlyPremium) {
    }

    public record Response(Long id, String contractNo, Long customerId, String customerName,
                           String productName, ContractStatus status,
                           LocalDate startDate, LocalDate endDate, long monthlyPremium) {
        static Response from(Contract c) {
            return new Response(c.getId(), c.getContractNo(), c.getCustomer().getId(), c.getCustomer().getName(),
                    c.getProduct().getName(), c.getStatus(), c.getStartDate(), c.getEndDate(), c.getMonthlyPremium());
        }
    }

    // 응답 변환 시 LAZY 연관(고객, 상품)을 읽으므로 트랜잭션 안에서 처리
    @PostMapping
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@Valid @RequestBody CreateRequest req) {
        return Response.from(contractService.create(
                req.customerId(), req.productId(), req.startDate(), req.endDate(), req.monthlyPremium()));
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
