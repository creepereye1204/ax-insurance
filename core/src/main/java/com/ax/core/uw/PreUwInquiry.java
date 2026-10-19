package com.ax.core.uw;

import com.ax.core.customer.Consent;
import com.ax.core.uw.engine.MedicalRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 선심사 조회 기록: 어떤 동의에 근거해 무엇을 조회했는지 스냅샷으로 남긴다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PreUwInquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uw_case_id")
    private UwCase uwCase;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consent_id")
    private Consent consent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<MedicalRecord> snapshot;

    @Column(nullable = false)
    private LocalDateTime queriedAt;

    public PreUwInquiry(UwCase uwCase, Consent consent, List<MedicalRecord> snapshot) {
        this.uwCase = uwCase;
        this.consent = consent;
        this.snapshot = snapshot;
        this.queriedAt = LocalDateTime.now();
    }
}
