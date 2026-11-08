package com.ax.core.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 배치 실행 이력 (기준일 · 처리 건수 · 소요 시간). 원장–DW 대사의 기준일 확인에도 쓴다 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BatchRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String job;

    @Column(nullable = false)
    private LocalDate asOf;

    @Column(nullable = false, length = 20)
    private String triggeredBy;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    @Column(nullable = false)
    private long elapsedMs;

    @Column(nullable = false, length = 300)
    private String summary;

    public BatchRun(String job, LocalDate asOf, String triggeredBy, LocalDateTime startedAt, long elapsedMs, String summary) {
        this.job = job;
        this.asOf = asOf;
        this.triggeredBy = triggeredBy;
        this.startedAt = startedAt;
        this.elapsedMs = elapsedMs;
        this.summary = summary;
    }
}
