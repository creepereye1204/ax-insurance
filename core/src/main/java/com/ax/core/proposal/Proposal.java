package com.ax.core.proposal;

import com.ax.core.common.BaseTimeEntity;
import com.ax.core.common.BusinessException;
import com.ax.core.customer.Customer;
import com.ax.core.product.Product;
import jakarta.persistence.CascadeType;
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

/**
 * 가입설계서. 발행 시점의 나이·보험료·예시표를 스냅샷으로 고정한다.
 * 유효기간(30일) 안에서만 청약으로 전환할 수 있다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Proposal extends BaseTimeEntity {

    public static final int VALID_DAYS = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String proposalNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false)
    private int insuredAge;

    @Column(nullable = false)
    private int paymentTerm;

    private Long monthlyContribution;

    private Integer annuityStartAge;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<FundAllocation> funds;

    @Column(nullable = false)
    private long monthlyPremium;

    /** 다음 상령일 (설계서 유효기간은 상령일 전날을 넘지 않는다) */
    private LocalDate nextAgeUpDate;

    /** 상령일 이후 월 보험료 (상령 후 가입 불가면 null) */
    private Long premiumAfterAgeUp;

    // 아래 필드는 기존 설계서(기능 추가 전 발행분)에는 값이 없으므로 null 허용 — 운영 DB 무중단 컬럼 추가
    private Long firstPremium;

    private Integer conversionRatePct;

    private Long convertedPremium;

    @JdbcTypeCode(SqlTypes.JSON)
    private Illustration illustration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Suitability suitability;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> suitabilityIssues;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProposalStatus status;

    @Column(nullable = false)
    private LocalDateTime issuedAt;

    @Column(nullable = false)
    private LocalDate validUntil;

    @Column(length = 50)
    private String validUntilReason;

    @OneToMany(mappedBy = "proposal", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ProposalCoverage> coverages = new ArrayList<>();

    Proposal(String proposalNo, PremiumCalculator.Quote q) {
        this.proposalNo = proposalNo;
        this.customer = q.customer();
        this.product = q.product();
        this.insuredAge = q.age();
        this.paymentTerm = q.paymentTerm();
        this.monthlyContribution = q.monthlyContribution();
        this.annuityStartAge = q.annuityStartAge();
        this.funds = q.funds();
        this.monthlyPremium = q.monthlyPremium();
        this.nextAgeUpDate = q.nextAgeUpDate();
        this.premiumAfterAgeUp = q.premiumAfterAgeUp();
        this.firstPremium = q.firstPremium();
        this.conversionRatePct = q.conversionRatePct();
        this.convertedPremium = q.convertedPremium();
        this.illustration = q.illustration();
        this.suitability = q.suitability();
        this.suitabilityIssues = q.suitabilityIssues();
        this.status = ProposalStatus.ISSUED;
        this.issuedAt = LocalDateTime.now();
        // 상령일이 지나면 보험나이가 바뀌어 보험료가 달라지므로 유효기간은 상령일 전날까지로 제한
        LocalDate byDays = issuedAt.toLocalDate().plusDays(VALID_DAYS);
        LocalDate beforeAgeUp = q.nextAgeUpDate().minusDays(1);
        this.validUntil = byDays.isBefore(beforeAgeUp) ? byDays : beforeAgeUp;
        this.validUntilReason = byDays.isBefore(beforeAgeUp) ? "발행 후 " + VALID_DAYS + "일" : "상령일(" + q.nextAgeUpDate() + ") 전날";
        for (PremiumCalculator.CoverageQuote cq : q.coverages()) {
            coverages.add(new ProposalCoverage(this, cq.coverage(), cq.amount(), cq.monthlyPremium()));
        }
    }

    public boolean isExpired(LocalDate today) {
        return today.isAfter(validUntil);
    }

    public void convert(LocalDate today) {
        if (status != ProposalStatus.ISSUED) {
            throw new BusinessException("이미 청약으로 전환된 설계서입니다.");
        }
        if (isExpired(today)) {
            throw new BusinessException("유효기간(" + validUntil + ")이 지난 설계서입니다. 다시 설계하세요.");
        }
        this.status = ProposalStatus.CONVERTED;
    }
}
