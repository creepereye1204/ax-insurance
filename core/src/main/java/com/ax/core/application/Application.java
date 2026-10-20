package com.ax.core.application;

import com.ax.core.common.BaseTimeEntity;
import com.ax.core.common.BusinessException;
import com.ax.core.customer.Customer;
import com.ax.core.disclosure.Questionnaire;
import com.ax.core.product.Product;
import com.ax.core.proposal.Proposal;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 청약. 가입설계서에서 전환되며, 계약자 = 피보험자로 단순화했다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Application extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String applicationNo;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposal_id", unique = true)
    private Proposal proposal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    /** 청약 시점의 질문지 버전 (고지의무 판단 증빙) */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "questionnaire_id")
    private Questionnaire questionnaire;

    @Column(nullable = false)
    private int paymentTerm;

    @Column(nullable = false)
    private long monthlyPremium;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ApplicationStatus status;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    // ---- 전자서명 (모의) ----
    @Column(length = 50)
    private String signerName;

    private LocalDateTime signedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<Agreement> agreements;

    /** 서명 대상 청약서 내용의 SHA-256 (서명 후 위변조 확인용) */
    @Column(length = 64)
    private String documentHash;

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ApplicationCoverage> coverages = new ArrayList<>();

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<DisclosureAnswer> answers = new ArrayList<>();

    public Application(String applicationNo, Proposal proposal, Questionnaire questionnaire, LocalDateTime submittedAt) {
        this.applicationNo = applicationNo;
        this.proposal = proposal;
        this.customer = proposal.getCustomer();
        this.product = proposal.getProduct();
        this.questionnaire = questionnaire;
        this.paymentTerm = proposal.getPaymentTerm();
        this.monthlyPremium = proposal.getMonthlyPremium();
        this.submittedAt = submittedAt;
    }

    public void addCoverage(ApplicationCoverage coverage) {
        coverage.attachTo(this);
        coverages.add(coverage);
    }

    public void addAnswer(DisclosureAnswer answer) {
        answer.attachTo(this);
        answers.add(answer);
    }

    public void sign(String signerName, List<Agreement> agreements, String documentHash, LocalDateTime signedAt) {
        this.signerName = signerName;
        this.agreements = List.copyOf(agreements);
        this.documentHash = documentHash;
        this.signedAt = signedAt;
    }

    public void changeStatus(ApplicationStatus next) {
        if (status == ApplicationStatus.ISSUED || status == ApplicationStatus.DECLINED) {
            throw new BusinessException("이미 종결된 청약입니다. (현재: " + status.getLabel() + ")");
        }
        this.status = next;
    }

    public List<DisclosureDetail> allDetails() {
        return answers.stream().flatMap(a -> a.getDetails().stream()).toList();
    }
}
