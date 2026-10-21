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

/** 특약 연관 규칙 (상품 기준 데이터). 담보(coverage)가 대상 담보(target)에 대해 갖는 제약 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoverageRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CoverageRuleType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coverage_id")
    private Coverage coverage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_id")
    private Coverage target;

    /** MAX_RATIO 의 비율(%) */
    private Integer ratioPct;

    public RiderRules.Rule spec() {
        return new RiderRules.Rule(type, coverage.getId(), coverage.getName(), target.getId(), target.getName(), ratioPct);
    }
}
