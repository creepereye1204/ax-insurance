package com.ax.core.contract;

import com.ax.core.application.Application;
import com.ax.core.common.BaseTimeEntity;
import com.ax.core.common.BusinessException;
import com.ax.core.customer.Customer;
import com.ax.core.product.CoverageCategory;
import com.ax.core.product.Product;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Contract extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String contractNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    /** 이 계약을 성립시킨 청약 (고지 내역 확인용) */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", unique = true)
    private Application application;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContractStatus status;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private int paymentTerm;

    @Column(nullable = false)
    private long monthlyPremium;

    /** 청약철회 가능 기한 (증권 수령일로부터 15일 — 계약일 즉시 수령으로 단순화) */
    private LocalDate withdrawalDeadline;

    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ContractCoverage> coverages = new ArrayList<>();

    public static final int WITHDRAWAL_DAYS = 15;

    public Contract(String contractNo, Customer customer, Product product, Application application,
                    int paymentTerm, LocalDate startDate, LocalDate endDate) {
        this.contractNo = contractNo;
        this.customer = customer;
        this.product = product;
        this.application = application;
        this.status = ContractStatus.ACTIVE;
        this.paymentTerm = paymentTerm;
        this.startDate = startDate;
        this.endDate = endDate;
        this.withdrawalDeadline = startDate.plusDays(WITHDRAWAL_DAYS);
    }

    public void addCoverage(ContractCoverage coverage) {
        coverage.attachTo(this);
        coverages.add(coverage);
        monthlyPremium += coverage.getMonthlyPremium();
    }

    public Optional<ContractCoverage> coverageOf(CoverageCategory category) {
        return coverages.stream().filter(c -> c.getCoverage().getCategory() == category).findFirst();
    }

    /** 해당 날짜에 보장이 유효한지 (유지 상태 + 보장 기간 내). */
    public boolean coversDate(LocalDate date) {
        return status == ContractStatus.ACTIVE && !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    public void terminate() {
        this.status = ContractStatus.TERMINATED;
    }

    public boolean canWithdraw(LocalDate today) {
        return status == ContractStatus.ACTIVE && withdrawalDeadline != null && !today.isAfter(withdrawalDeadline);
    }

    /** 청약철회: 기한 내 유지 계약만 가능, 납입 보험료 전액 반환 대상 */
    public void withdraw(LocalDate today) {
        if (!canWithdraw(today)) {
            throw new BusinessException("청약철회 기한(" + withdrawalDeadline + ")이 지났거나 유지 중인 계약이 아닙니다.");
        }
        this.status = ContractStatus.WITHDRAWN;
    }
}
