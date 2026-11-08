package com.ax.core.payment;

import com.ax.core.common.NotFoundException;
import com.ax.core.contract.Contract;
import com.ax.core.contract.ContractController;
import com.ax.core.contract.ContractRepository;
import com.ax.core.contract.ContractStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PremiumPaymentRepository paymentRepository;
    private final ContractRepository contractRepository;
    private final PaymentService paymentService;
    private final BillingBatch billingBatch;
    private final BatchRunRepository runRepository;

    public record Line(int installmentNo, LocalDate dueDate, LocalDate graceEnd, long amount, PaymentStatus status,
                       String statusLabel, boolean overdue, LocalDate paidOn, String methodLabel) {
        static Line from(PremiumPayment p, LocalDate today) {
            return new Line(p.getInstallmentNo(), p.getDueDate(), p.graceEnd(), p.getAmount(), p.getStatus(),
                    p.getStatus().getLabel(), p.getStatus() == PaymentStatus.UNPAID && today.isAfter(p.getDueDate()),
                    p.getPaidOn(), p.getMethod() == null ? null : p.getMethod().getLabel());
        }
    }

    /** 수납 현황: paidCount 회차 납입 / 전체 totalInstallments 회차, 미납 · 다음 납입기일 */
    public record Ledger(ContractController.Response contract, String paymentMethodLabel, int totalInstallments,
                         int paidCount, long paidAmount, int unpaidCount, long unpaidAmount, LocalDate nextDueDate,
                         LocalDate graceEnd, List<Line> lines) {
    }

    public record PayRequest(PaymentMethod method) {
    }

    public record MethodRequest(@NotNull PaymentMethod method) {
    }

    public record RunResponse(LocalDate asOf, String triggeredBy, LocalDateTime startedAt, long elapsedMs, String summary) {
        static RunResponse from(BatchRun r) {
            return new RunResponse(r.getAsOf(), r.getTriggeredBy(), r.getStartedAt(), r.getElapsedMs(), r.getSummary());
        }
    }

    @GetMapping("/contracts/{id}/payments")
    @Transactional(readOnly = true)
    public Ledger ledger(@PathVariable Long id) {
        Contract c = contractRepository.findById(id).orElseThrow(() -> new NotFoundException("계약", id));
        LocalDate today = LocalDate.now();
        List<PremiumPayment> rows = paymentRepository.findByContractIdOrderByInstallmentNoDesc(id);
        List<PremiumPayment> paid = rows.stream().filter(p -> p.getStatus() == PaymentStatus.PAID).toList();
        List<PremiumPayment> unpaid = rows.stream().filter(p -> p.getStatus() == PaymentStatus.UNPAID).toList();
        int last = rows.isEmpty() ? 0 : rows.get(0).getInstallmentNo();
        // 유지 계약만 다음 회차가 청구된다 (실효·해지·철회는 청구 중지)
        LocalDate next = c.getStatus() == ContractStatus.ACTIVE && last < c.totalInstallments()
                ? PaymentSchedule.dueDate(c.getStartDate(), last + 1) : null;
        LocalDate grace = unpaid.isEmpty() ? null : unpaid.get(unpaid.size() - 1).graceEnd();
        return new Ledger(ContractController.Response.from(c), c.paymentMethodOrDefault().getLabel(), c.totalInstallments(),
                paid.size(), paid.stream().mapToLong(PremiumPayment::getAmount).sum(),
                unpaid.size(), unpaid.stream().mapToLong(PremiumPayment::getAmount).sum(), next, grace,
                rows.stream().map(p -> Line.from(p, today)).toList());
    }

    @PostMapping("/contracts/{id}/payments")
    @Transactional
    public Line pay(@PathVariable Long id, @RequestBody(required = false) PayRequest req) {
        return Line.from(paymentService.payNext(id, req == null ? null : req.method()), LocalDate.now());
    }

    @PostMapping("/contracts/{id}/reinstate")
    @Transactional
    public Ledger reinstate(@PathVariable Long id, @RequestBody(required = false) PayRequest req) {
        paymentService.reinstate(id, req == null ? null : req.method());
        return ledger(id);
    }

    @PutMapping("/contracts/{id}/payment-method")
    @Transactional
    public Ledger changeMethod(@PathVariable Long id, @Valid @RequestBody MethodRequest req) {
        paymentService.changeMethod(id, req.method());
        return ledger(id);
    }

    @GetMapping("/ops/billing")
    @Transactional(readOnly = true)
    public List<RunResponse> runs() {
        return runRepository.findTop20ByJobOrderByIdDesc(BillingBatch.JOB).stream().map(RunResponse::from).toList();
    }

    /** 수동 실행 (기준일 기본 오늘). 다른 기간계가 실행 중이면 건너뛴다 */
    @PostMapping("/ops/billing/run")
    public RunResponse runNow(@RequestParam(required = false) LocalDate asOf) {
        LocalDate date = asOf == null ? LocalDate.now() : asOf;
        return billingBatch.run(date, "MANUAL")
                .map(r -> new RunResponse(r.asOf(), "MANUAL", LocalDateTime.now(), r.elapsedMs(), r.summary()))
                .orElseGet(() -> new RunResponse(date, "MANUAL", LocalDateTime.now(), 0, "다른 기간계에서 실행 중이라 건너뜀"));
    }
}
