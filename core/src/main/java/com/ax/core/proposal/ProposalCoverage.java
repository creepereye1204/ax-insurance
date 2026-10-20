package com.ax.core.proposal;

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
public class ProposalCoverage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposal_id")
    private Proposal proposal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coverage_id")
    private Coverage coverage;

    @Column(nullable = false)
    private long insuredAmount;

    @Column(nullable = false)
    private long monthlyPremium;

    ProposalCoverage(Proposal proposal, Coverage coverage, long insuredAmount, long monthlyPremium) {
        this.proposal = proposal;
        this.coverage = coverage;
        this.insuredAmount = insuredAmount;
        this.monthlyPremium = monthlyPremium;
    }
}
