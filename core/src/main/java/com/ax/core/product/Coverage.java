package com.ax.core.product;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 담보 (주계약 또는 특약).
 * 보험료는 요율표(PremiumRate)에서 성별·나이·납입기간으로 찾는다.
 * baseRatePer10M / ageGrowthPermille 은 요율표를 산출하는 기초값이다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coverage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CoverageCategory category;

    /** 주계약(필수) 여부 */
    @Column(nullable = false)
    private boolean required;

    @Column(nullable = false)
    private long minAmount;

    @Column(nullable = false)
    private long maxAmount;

    @Column(nullable = false)
    private long defaultAmount;

    @Column(nullable = false)
    private long amountStep;

    @Column(nullable = false)
    private long baseRatePer10M;

    /** 나이 1세 증가당 요율 증가율 (천분율, 45 = 4.5%) */
    @Column(nullable = false)
    private int ageGrowthPermille;

    Coverage(Product product, String code, String name, CoverageCategory category, boolean required,
             long minAmount, long maxAmount, long defaultAmount, long amountStep,
             long baseRatePer10M, int ageGrowthPermille) {
        this.product = product;
        this.code = code;
        this.name = name;
        this.category = category;
        this.required = required;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.defaultAmount = defaultAmount;
        this.amountStep = amountStep;
        this.baseRatePer10M = baseRatePer10M;
        this.ageGrowthPermille = ageGrowthPermille;
    }

    /** 적립형 담보(연금·저축)는 가입금액이 아니라 월 납입액으로 설계한다. */
    public boolean isAccumulation() {
        return !category.isUnderwritten();
    }

    public boolean isValidAmount(long amount) {
        return amount >= minAmount && amount <= maxAmount && (amount - minAmount) % amountStep == 0;
    }
}
