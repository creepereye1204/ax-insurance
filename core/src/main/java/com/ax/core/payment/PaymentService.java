package com.ax.core.payment;

import com.ax.core.common.BusinessException;
import com.ax.core.common.NotFoundException;
import com.ax.core.contract.Contract;
import com.ax.core.contract.ContractRepository;
import com.ax.core.contract.ContractStatus;
import com.ax.core.customer.CustomerStatus;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 수납: 초회보험료 · 계속보험료 납입 · 납입방법 변경 · 부활 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PremiumPaymentRepository paymentRepository;
    private final ContractRepository contractRepository;

    /** 계약 성립 시 초회보험료(1회차)는 청약 때 받은 것으로 본다 */
    @Transactional
    public void recordInitial(Contract contract) {
        paymentRepository.save(PremiumPayment.initial(contract, contract.getStartDate(), PaymentMethod.CARD));
    }

    /** 가장 오래된 미납 회차를 납입 (직접납입·카드) */
    @Transactional
    public PremiumPayment payNext(Long contractId, PaymentMethod method) {
        Contract contract = contract(contractId);
        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw new BusinessException(contract.getStatus().getLabel() + " 상태의 계약입니다."
                    + (contract.getStatus() == ContractStatus.LAPSED ? " 실효 계약은 부활 신청으로 밀린 보험료를 냅니다." : ""));
        }
        List<PremiumPayment> unpaid = paymentRepository.findByContractIdAndStatusOrderByInstallmentNo(contractId, PaymentStatus.UNPAID);
        if (unpaid.isEmpty()) {
            LocalDate next = PaymentSchedule.dueDate(contract.getStartDate(), lastInstallment(contractId) + 1);
            throw new BusinessException("납입할 보험료가 없습니다. 다음 납입기일은 " + next + " 입니다.");
        }
        PremiumPayment p = unpaid.get(0);
        p.pay(LocalDate.now(), method == null ? PaymentMethod.DIRECT : method);
        return p;
    }

    /**
     * 부활: 실효 기간 중 납입기일이 온 회차까지 청구해 밀린 보험료를 모두 받고 효력을 되살린다.
     * 실제로는 부활 시 알릴의무 고지·심사를 다시 하지만 여기서는 생략한다.
     */
    @Transactional
    public int reinstate(Long contractId, PaymentMethod method) {
        Contract contract = contract(contractId);
        LocalDate today = LocalDate.now();
        contract.reinstate(today);
        int due = PaymentSchedule.dueCount(contract.getStartDate(), today, contract.totalInstallments());
        for (int n = lastInstallment(contractId) + 1; n <= due; n++) {
            paymentRepository.save(PremiumPayment.billed(contract, n));
        }
        List<PremiumPayment> unpaid = paymentRepository.findByContractIdAndStatusOrderByInstallmentNo(contractId, PaymentStatus.UNPAID);
        unpaid.forEach(p -> p.pay(today, method == null ? PaymentMethod.DIRECT : method));
        contract.getCustomer().changeStatus(CustomerStatus.CONTRACTED);
        return unpaid.size();
    }

    @Transactional
    public Contract changeMethod(Long contractId, PaymentMethod method) {
        Contract contract = contract(contractId);
        contract.changePaymentMethod(method);
        return contract;
    }

    private int lastInstallment(Long contractId) {
        return paymentRepository.findByContractIdOrderByInstallmentNoDesc(contractId).stream()
                .mapToInt(PremiumPayment::getInstallmentNo).findFirst().orElse(0);
    }

    private Contract contract(Long id) {
        return contractRepository.findById(id).orElseThrow(() -> new NotFoundException("계약", id));
    }
}
