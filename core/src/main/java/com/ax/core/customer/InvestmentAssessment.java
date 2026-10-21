package com.ax.core.customer;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 투자성향 진단 이력 (추가만). 고객에는 최신 결과만 두고, 언제 어떤 응답으로 진단했는지는 여기에 남긴다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvestmentAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<Integer> scores;

    @Column(nullable = false)
    private int totalScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvestmentProfile profile;

    @Column(nullable = false)
    private LocalDateTime assessedAt;

    public InvestmentAssessment(Customer customer, List<Integer> scores) {
        this.customer = customer;
        this.scores = scores;
        this.totalScore = scores.stream().mapToInt(Integer::intValue).sum();
        this.profile = InvestmentProfile.fromScore(totalScore);
        this.assessedAt = LocalDateTime.now();
    }
}
