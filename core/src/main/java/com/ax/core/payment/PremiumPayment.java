package com.ax.core.payment;

import com.ax.core.common.BaseTimeEntity;
import com.ax.core.common.BusinessException;
import com.ax.core.contract.Contract;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 수납 원장: 계약의 회차별 보험료. 납입기일이 오면 수납 배치가 미납으로 만들고, 자동이체·직접납입으로 납입된다.
 * 정보계의 수입보험료·유지율은 이 원장에서 계산한다.
 */
@Getter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"contract_id", "installment_no"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PremiumPayment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id")
    private Contract contract;

    /** 회차 (1부터) */
    @Column(name = "installment_no", nullable = false)
    private int installmentNo;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PaymentStatus status;

    private LocalDate paidOn;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PaymentMethod method;

    /** 계약 성립 시 초회보험료 (1회차, 납입 완료) */
    public static PremiumPayment initial(Contract contract, LocalDate paidOn, PaymentMethod method) {
        PremiumPayment p = new PremiumPayment();
        p.contract = contract;
        p.installmentNo = 1;
        p.dueDate = contract.getStartDate();
        p.amount = contract.getMonthlyPremium();
        p.status = PaymentStatus.PAID;
        p.paidOn = paidOn;
        p.method = method;
        return p;
    }

    /** 납입기일이 온 회차 (미납) */
    public static PremiumPayment billed(Contract contract, int installment) {
        PremiumPayment p = new PremiumPayment();
        p.contract = contract;
        p.installmentNo = installment;
        p.dueDate = PaymentSchedule.dueDate(contract.getStartDate(), installment);
        p.amount = contract.getMonthlyPremium();
        p.status = PaymentStatus.UNPAID;
        return p;
    }

    public void pay(LocalDate on, PaymentMethod method) {
        if (status == PaymentStatus.PAID) {
            throw new BusinessException(installmentNo + "회차는 이미 납입되었습니다.");
        }
        this.status = PaymentStatus.PAID;
        this.paidOn = on;
        this.method = method;
    }

    public LocalDate graceEnd() {
        return PaymentSchedule.graceEnd(dueDate);
    }
}
