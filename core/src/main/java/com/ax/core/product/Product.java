package com.ax.core.product;

import com.ax.core.common.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProductType type;

    /** AI 서비스에 적재된 약관 문서명 (없으면 null) */
    @Column(length = 100)
    private String policyDocument;

    @Column(nullable = false)
    private int minEntryAge;

    @Column(nullable = false)
    private int maxEntryAge;

    /** 선택 가능한 납입기간(년). 1 은 1년 갱신 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<Integer> paymentTerms;

    /** 보험기간 표시 (예: 종신, 100세만기, 20년만기) */
    @Column(nullable = false, length = 30)
    private String insurancePeriod;

    @Column(nullable = false)
    private boolean renewable;

    /** 간편심사형(유병자) 여부 */
    @Column(nullable = false)
    private boolean simplifiedUw;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SaleStatus saleStatus;

    @Column(nullable = false)
    private LocalDate launchedOn;

    /** 저축성: 월 납입액 범위 */
    private Long minMonthlyContribution;

    private Long maxMonthlyContribution;

    /** 연금: 선택 가능한 연금개시나이 */
    @JdbcTypeCode(SqlTypes.JSON)
    private List<Integer> annuityStartAges;

    /** 변액: 선택 가능한 펀드 코드 */
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> fundCodes;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<Coverage> coverages = new ArrayList<>();

    @Builder
    private Product(String code, String name, ProductType type, String policyDocument, int minEntryAge, int maxEntryAge,
                    List<Integer> paymentTerms, String insurancePeriod, boolean renewable, boolean simplifiedUw,
                    SaleStatus saleStatus, LocalDate launchedOn, Long minMonthlyContribution, Long maxMonthlyContribution,
                    List<Integer> annuityStartAges, List<String> fundCodes) {
        this.code = code;
        this.name = name;
        this.type = type;
        this.policyDocument = policyDocument;
        this.minEntryAge = minEntryAge;
        this.maxEntryAge = maxEntryAge;
        this.paymentTerms = paymentTerms;
        this.insurancePeriod = insurancePeriod;
        this.renewable = renewable;
        this.simplifiedUw = simplifiedUw;
        this.saleStatus = saleStatus == null ? SaleStatus.ON_SALE : saleStatus;
        this.launchedOn = launchedOn;
        this.minMonthlyContribution = minMonthlyContribution;
        this.maxMonthlyContribution = maxMonthlyContribution;
        this.annuityStartAges = annuityStartAges;
        this.fundCodes = fundCodes;
    }

    public Product addCoverage(String code, String name, CoverageCategory category, boolean required,
                               long minAmount, long maxAmount, long defaultAmount, long amountStep,
                               long baseRatePer10M, int ageGrowthPermille) {
        coverages.add(new Coverage(this, code, name, category, required, minAmount, maxAmount, defaultAmount,
                amountStep, baseRatePer10M, ageGrowthPermille));
        return this;
    }

    public boolean isOnSale() {
        return saleStatus == SaleStatus.ON_SALE;
    }

    public boolean acceptsAge(int age) {
        return age >= minEntryAge && age <= maxEntryAge;
    }
}
