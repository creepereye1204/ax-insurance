package com.ax.core.external;

import com.ax.core.common.TreatmentType;
import com.ax.core.customer.Customer;
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
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 외부기관(타사 보험금 지급 이력 등) 조회 결과를 흉내 내는 Mock 데이터.
 * 실제 시스템에서는 대외계를 통해 조회한다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExternalMedicalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(nullable = false, length = 50)
    private String institution;

    @Column(nullable = false, length = 10)
    private String kcdCode;

    @Column(nullable = false)
    private LocalDate diagnosedOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TreatmentType treatment;

    @Column(nullable = false)
    private boolean cured;

    public ExternalMedicalRecord(Customer customer, String institution, String kcdCode, LocalDate diagnosedOn,
                                 TreatmentType treatment, boolean cured) {
        this.customer = customer;
        this.institution = institution;
        this.kcdCode = kcdCode;
        this.diagnosedOn = diagnosedOn;
        this.treatment = treatment;
        this.cured = cured;
    }
}
