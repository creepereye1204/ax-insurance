package com.ax.core.uw;

import com.ax.core.product.Coverage;
import com.ax.core.uw.engine.CoverageOutcome;
import com.ax.core.uw.engine.Exclusion;
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
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 담보별 심사 결과 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UwCoverageResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uw_case_id")
    private UwCase uwCase;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coverage_id")
    private Coverage coverage;

    @Column(nullable = false)
    private long insuredAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CoverageOutcome outcome;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<Exclusion> exclusions;

    @Column(nullable = false)
    private int ratingPct;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> matchedRules;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> reasons;

    public UwCoverageResult(Coverage coverage, long insuredAmount, CoverageOutcome outcome, List<Exclusion> exclusions,
                            int ratingPct, List<String> matchedRules, List<String> reasons) {
        this.coverage = coverage;
        this.insuredAmount = insuredAmount;
        this.outcome = outcome;
        this.exclusions = exclusions;
        this.ratingPct = ratingPct;
        this.matchedRules = matchedRules;
        this.reasons = reasons;
    }

    void attachTo(UwCase uwCase) {
        this.uwCase = uwCase;
    }

    /** 본사 심사자 결정 반영 */
    void decide(CoverageOutcome outcome, List<Exclusion> exclusions, int ratingPct) {
        this.outcome = outcome;
        this.exclusions = exclusions;
        this.ratingPct = ratingPct;
    }
}
