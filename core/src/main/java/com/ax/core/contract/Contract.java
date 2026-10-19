package com.ax.core.contract;

import com.ax.core.application.Application;
import com.ax.core.common.BaseTimeEntity;
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
    private long monthlyPremium;

    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ContractCoverage> coverages = new ArrayList<>();

    public Contract(String contractNo, Customer customer, Product product, Application application,
                    LocalDate startDate, LocalDate endDate) {
        this.contractNo = contractNo;
        this.customer = customer;
        this.product = product;
        this.application = application;
        this.status = ContractStatus.ACTIVE;
        this.startDate = startDate;
        this.endDate = endDate;
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
}
