package com.ax.core.proposal;

import com.ax.core.common.BaseTimeEntity;
import com.ax.core.customer.Customer;
import com.ax.core.product.Product;
import com.ax.core.proposal.PremiumCalculator.CoverageAmount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 가입설계 임시저장. 고객·상품당 1건이며, 작성 중인 입력을 검증 없이 그대로 보관한다
 * (보험료 계산·검증은 설계서 발행 시점에 다시 한다). 설계서를 발행하면 삭제된다.
 */
@Getter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"customer_id", "product_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProposalDraft extends BaseTimeEntity {

    /** 작성 중 입력. planName 은 마지막으로 적용한 플랜(참고용) */
    public record Payload(Integer paymentTerm, List<CoverageAmount> coverages, Long monthlyContribution,
                          Integer annuityStartAge, List<FundAllocation> funds, String planName) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Payload payload;

    @Column(nullable = false)
    private LocalDateTime savedAt;

    public ProposalDraft(Customer customer, Product product, Payload payload) {
        this.customer = customer;
        this.product = product;
        overwrite(payload);
    }

    public void overwrite(Payload payload) {
        this.payload = payload;
        this.savedAt = LocalDateTime.now();
    }
}
