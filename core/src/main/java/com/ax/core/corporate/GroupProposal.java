package com.ax.core.corporate;

import com.ax.core.common.BaseTimeEntity;
import com.ax.core.product.Product;
import com.ax.core.product.ProductPlan;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 단체 가입설계서. 발행 시점의 종업원별 보험나이·보험료·제외 사유를 스냅샷으로 고정한다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupProposal extends BaseTimeEntity {

    public static final int VALID_DAYS = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String proposalNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "corporate_id")
    private Corporate corporate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false)
    private int paymentTerm;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<ProductPlan.Item> items;

    @Column(nullable = false)
    private int insuredCount;

    @Column(nullable = false)
    private int excludedCount;

    /** 할인 전 월 보험료 합계 */
    @Column(nullable = false)
    private long grossPremium;

    @Column(nullable = false)
    private int discountPct;

    /** 법인이 내는 월 보험료 */
    @Column(nullable = false)
    private long monthlyPremium;

    @Column(nullable = false)
    private LocalDateTime issuedAt;

    @Column(nullable = false)
    private LocalDate validUntil;

    @OneToMany(mappedBy = "groupProposal", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<GroupProposalMember> members = new ArrayList<>();

    GroupProposal(String proposalNo, GroupQuoteService.GroupQuote q) {
        this.proposalNo = proposalNo;
        this.corporate = q.corporate();
        this.product = q.product();
        this.paymentTerm = q.paymentTerm();
        this.items = q.items();
        this.insuredCount = q.insuredCount();
        this.excludedCount = q.lines().size() - q.insuredCount();
        this.grossPremium = q.grossPremium();
        this.discountPct = q.discountPct();
        this.monthlyPremium = q.monthlyPremium();
        this.issuedAt = LocalDateTime.now();
        this.validUntil = issuedAt.toLocalDate().plusDays(VALID_DAYS);
        for (GroupQuoteService.Line l : q.lines()) {
            members.add(new GroupProposalMember(this, l));
        }
    }
}
