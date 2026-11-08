package com.ax.core.contract;

import com.ax.core.agent.Agent;
import com.ax.core.application.Application;
import com.ax.core.common.BaseTimeEntity;
import com.ax.core.common.BusinessException;
import com.ax.core.customer.Customer;
import com.ax.core.payment.PaymentMethod;
import com.ax.core.payment.PaymentSchedule;
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

    /** 모집 설계사 (null 은 미지정 — 컬럼 추가 전 데이터) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private Agent agent;

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

    /** 보험료 납입방법 (null 은 자동이체 — 컬럼 추가 전 데이터) */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PaymentMethod paymentMethod;

    /** 실효·해지·철회·부활된 날 (유지율 계산용) */
    private LocalDate statusChangedOn;

    /** 청약철회 가능 기한 (증권 수령일로부터 15일 — 계약일 즉시 수령으로 단순화) */
    private LocalDate withdrawalDeadline;

    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ContractCoverage> coverages = new ArrayList<>();

    public static final int WITHDRAWAL_DAYS = 15;
    public static final int REINSTATE_YEARS = 3;

    public Contract(String contractNo, Customer customer, Product product, Application application,
                    int paymentTerm, LocalDate startDate, LocalDate endDate) {
        this.contractNo = contractNo;
        this.customer = customer;
        this.product = product;
        this.application = application;
        this.agent = application == null ? null : application.getAgent();
        this.paymentMethod = PaymentMethod.AUTO_TRANSFER;
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
        if (status != ContractStatus.ACTIVE && status != ContractStatus.LAPSED) {
            throw new BusinessException("유지 또는 실효 중인 계약만 해지할 수 있습니다.");
        }
        this.status = ContractStatus.TERMINATED;
        this.statusChangedOn = LocalDate.now();
    }

    public PaymentMethod paymentMethodOrDefault() {
        return paymentMethod == null ? PaymentMethod.AUTO_TRANSFER : paymentMethod;
    }

    public void changePaymentMethod(PaymentMethod method) {
        if (method == PaymentMethod.CARD) {
            throw new BusinessException("계속보험료 납입방법은 자동이체 또는 직접납입만 가능합니다 (카드는 초회·수시 납입).");
        }
        this.paymentMethod = method;
    }

    /** 실효: 납입유예기간이 끝날 때까지 미납 (수납 배치가 판정) */
    public void lapse(LocalDate on) {
        if (status != ContractStatus.ACTIVE) {
            throw new BusinessException("유지 중인 계약만 실효될 수 있습니다.");
        }
        this.status = ContractStatus.LAPSED;
        this.statusChangedOn = on;
    }

    /** 부활: 실효 후 3년 이내, 밀린 보험료를 모두 내면 효력이 되살아난다 (부활 심사는 단순화해 생략) */
    public void reinstate(LocalDate today) {
        if (status != ContractStatus.LAPSED) {
            throw new BusinessException("실효된 계약만 부활할 수 있습니다.");
        }
        if (statusChangedOn != null && today.isAfter(statusChangedOn.plusYears(REINSTATE_YEARS))) {
            throw new BusinessException("실효일(" + statusChangedOn + ")로부터 " + REINSTATE_YEARS + "년이 지나 부활할 수 없습니다.");
        }
        this.status = ContractStatus.ACTIVE;
        this.statusChangedOn = today;
    }

    /** 납입할 회차 수: 갱신형(1년납)은 보험기간 끝까지 매월, 그 외는 납입기간 × 12 (보험기간을 넘지 않음) */
    public int totalInstallments() {
        return PaymentSchedule.totalInstallments(startDate, endDate, paymentTerm);
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
        this.statusChangedOn = today;
    }
}
