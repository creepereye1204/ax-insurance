package com.ax.core.application;

import com.ax.core.product.Coverage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationCoverage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id")
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coverage_id")
    private Coverage coverage;

    @Column(nullable = false)
    private long insuredAmount;

    /** 표준체 기준 월 보험료 (적립 담보는 월 납입액) */
    @Column(nullable = false)
    private long basePremium;

    public ApplicationCoverage(Coverage coverage, long insuredAmount, long basePremium) {
        this.coverage = coverage;
        this.insuredAmount = insuredAmount;
        this.basePremium = basePremium;
    }

    void attachTo(Application application) {
        this.application = application;
    }
}
