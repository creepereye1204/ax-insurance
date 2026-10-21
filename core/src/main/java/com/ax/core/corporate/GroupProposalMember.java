package com.ax.core.corporate;

import com.ax.core.customer.Customer;
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

/** 단체 설계서의 종업원별 줄 (가입 대상이면 보험료, 아니면 제외 사유) */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupProposalMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_proposal_id")
    private GroupProposal groupProposal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(nullable = false)
    private int insuranceAge;

    @Column(nullable = false)
    private boolean included;

    @Column(nullable = false)
    private long monthlyPremium;

    @Column(length = 300)
    private String excludedReason;

    GroupProposalMember(GroupProposal proposal, GroupQuoteService.Line line) {
        this.groupProposal = proposal;
        this.customer = line.member().getCustomer();
        this.insuranceAge = line.insuranceAge();
        this.included = line.included();
        this.monthlyPremium = line.monthlyPremium();
        this.excludedReason = line.excludedReason();
    }
}
