package com.ax.core.payment;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PremiumPaymentRepository extends JpaRepository<PremiumPayment, Long> {

    List<PremiumPayment> findByContractIdOrderByInstallmentNoDesc(Long contractId);

    List<PremiumPayment> findByContractIdAndStatusOrderByInstallmentNo(Long contractId, PaymentStatus status);
}
