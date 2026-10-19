package com.ax.core.uw;

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
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 심사 결정 로그. 수정하지 않고 추가만 한다(누가, 어떤 룰로, 왜). */
@Getter
@Entity
@Immutable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UwDecisionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uw_case_id")
    private UwCase uwCase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UwStage stage;

    /** SYSTEM / AI / 심사자 이름 */
    @Column(nullable = false, length = 50)
    private String actor;

    @Column(nullable = false, length = 50)
    private String decision;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> matchedRules;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public UwDecisionLog(UwStage stage, String actor, String decision, List<String> matchedRules, String reason) {
        this.stage = stage;
        this.actor = actor;
        this.decision = decision;
        this.matchedRules = matchedRules;
        this.reason = reason;
        this.createdAt = LocalDateTime.now();
    }

    void attachTo(UwCase uwCase) {
        this.uwCase = uwCase;
    }
}
