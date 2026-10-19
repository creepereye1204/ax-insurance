package com.ax.core.uw;

import com.ax.core.application.Application;
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
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 심사 케이스. 선심사는 고객 단위(청약 없음), 전산/본사심사는 청약 단위.
 * 단계가 넘어가도 같은 케이스에 결정 로그가 누적된다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UwCase extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private Application application;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UwStage stage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UwCaseStatus status;

    @Column(nullable = false)
    private int ruleSetVersion;

    /** 본사 이관 사유 요약 */
    @Column(length = 500)
    private String referReason;

    @Column(columnDefinition = "text")
    private String aiOpinion;

    @OneToMany(mappedBy = "uwCase", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<UwCoverageResult> results = new ArrayList<>();

    @OneToMany(mappedBy = "uwCase", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<UwDecisionLog> logs = new ArrayList<>();

    public UwCase(Customer customer, Product product, Application application, UwStage stage, int ruleSetVersion) {
        this.customer = customer;
        this.product = product;
        this.application = application;
        this.stage = stage;
        this.status = UwCaseStatus.COMPLETED;
        this.ruleSetVersion = ruleSetVersion;
    }

    public void addResult(UwCoverageResult result) {
        result.attachTo(this);
        results.add(result);
    }

    /** 결정 로그는 추가만 한다 (감사 추적) */
    public void log(UwDecisionLog log) {
        log.attachTo(this);
        logs.add(log);
    }

    public void referToManual(String reason) {
        this.stage = UwStage.MANUAL_UW;
        this.status = UwCaseStatus.PENDING;
        this.referReason = reason;
    }

    public void completeManual() {
        if (stage != UwStage.MANUAL_UW || status != UwCaseStatus.PENDING) {
            throw new BusinessException("본사 심사 대기 건이 아닙니다.");
        }
        this.status = UwCaseStatus.COMPLETED;
    }

    public void writeAiOpinion(String opinion) {
        this.aiOpinion = opinion;
    }
}
