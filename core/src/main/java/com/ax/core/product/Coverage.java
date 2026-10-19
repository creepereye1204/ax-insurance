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

/** 담보. 상품에 속하며 가입금액 1,000만원당 월 보험료(기준 요율)를 가진다. */
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

    @Column(nullable = false)
    private long monthlyRatePer10M;

    Coverage(Product product, String code, String name, CoverageCategory category, long monthlyRatePer10M) {
        this.product = product;
        this.code = code;
        this.name = name;
        this.category = category;
        this.monthlyRatePer10M = monthlyRatePer10M;
    }

    /** 가입금액과 할증률을 반영한 월 보험료 (원 단위 절사). */
    public long monthlyPremium(long insuredAmount, int ratingPct) {
        return insuredAmount / 10_000_000 * monthlyRatePer10M * (100 + ratingPct) / 100;
    }
}
