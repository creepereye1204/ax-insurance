package com.ax.core.application;

import com.ax.core.common.TreatmentType;
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

/** "예" 로 답한 고지 문항의 상세: 어떤 질병으로, 언제, 어떤 치료를 받았는가 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DisclosureDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "answer_id")
    private DisclosureAnswer answer;

    @Column(nullable = false, length = 10)
    private String kcdCode;

    @Column(nullable = false)
    private LocalDate diagnosedOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TreatmentType treatment;

    @Column(nullable = false)
    private int treatmentDays;

    @Column(nullable = false)
    private boolean cured;

    public DisclosureDetail(String kcdCode, LocalDate diagnosedOn, TreatmentType treatment, int treatmentDays, boolean cured) {
        this.kcdCode = kcdCode;
        this.diagnosedOn = diagnosedOn;
        this.treatment = treatment;
        this.treatmentDays = treatmentDays;
        this.cured = cured;
    }

    void attachTo(DisclosureAnswer answer) {
        this.answer = answer;
    }
}
