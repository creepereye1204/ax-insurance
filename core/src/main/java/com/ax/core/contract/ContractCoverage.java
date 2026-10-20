package com.ax.core.contract;

import com.ax.core.kcd.Kcd;
import com.ax.core.product.Coverage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 계약 담보: 심사 결과(부담보·할증)가 반영된 상태로 성립한다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractCoverage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id")
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coverage_id")
    private Coverage coverage;

    @Column(nullable = false)
    private long insuredAmount;

    @Column(nullable = false)
    private int ratingPct;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<ContractExclusion> exclusions;

    @Column(nullable = false)
    private long monthlyPremium;

    /** basePremium(표준체 보험료)에 할증률을 반영해 성립한다 */
    public ContractCoverage(Coverage coverage, long insuredAmount, long basePremium, int ratingPct,
                            List<ContractExclusion> exclusions) {
        this.coverage = coverage;
        this.insuredAmount = insuredAmount;
        this.ratingPct = ratingPct;
        this.exclusions = exclusions;
        this.monthlyPremium = Math.round(basePremium * (100 + ratingPct) / 100.0 / 10) * 10;
    }

    void attachTo(Contract contract) {
        this.contract = contract;
    }

    /** 진료일에 이 질병코드가 부담보 대상인지 */
    public Optional<ContractExclusion> exclusionFor(String kcdCode, LocalDate date) {
        return exclusions.stream()
                .filter(e -> !date.isAfter(e.until()) && e.kcd().stream().anyMatch(p -> Kcd.matches(kcdCode, p)))
                .findFirst();
    }
}
