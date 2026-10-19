package com.ax.core.claim;

import com.ax.core.common.BaseTimeEntity;
import com.ax.core.common.BusinessException;
import com.ax.core.contract.Contract;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Claim extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String claimNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id")
    private Contract contract;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClaimType type;

    @Column(nullable = false)
    private LocalDate treatmentDate;

    @Column(nullable = false, length = 200)
    private String diagnosis;

    @Column(nullable = false)
    private long claimedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClaimStatus status;

    // AI 검토 결과 (참고용 의견)
    @Column(columnDefinition = "text")
    private String aiOpinion;

    private Long aiRunId;

    // 심사자 결정
    private Long paidAmount;

    @Column(length = 500)
    private String reviewerComment;

    private LocalDateTime decidedAt;

    public Claim(String claimNo, Contract contract, ClaimType type, LocalDate treatmentDate,
                 String diagnosis, long claimedAmount) {
        if (!contract.coversDate(treatmentDate)) {
            throw new BusinessException("진료일(" + treatmentDate + ")에 유효한 계약이 아닙니다.");
        }
        this.claimNo = claimNo;
        this.contract = contract;
        this.type = type;
        this.treatmentDate = treatmentDate;
        this.diagnosis = diagnosis;
        this.claimedAmount = claimedAmount;
        this.status = ClaimStatus.RECEIVED;
    }

    public void startAiReview() {
        requireStatus(ClaimStatus.RECEIVED);
        this.status = ClaimStatus.AI_REVIEWING;
    }

    public void completeAiReview(String opinion, Long runId) {
        requireStatus(ClaimStatus.AI_REVIEWING);
        this.aiOpinion = opinion;
        this.aiRunId = runId;
        this.status = ClaimStatus.AI_REVIEWED;
    }

    /** AI 서비스 장애 시 수동 심사로 넘긴다. 기간계 업무는 AI 장애와 무관하게 계속 진행된다. */
    public void fallbackToManualReview(String reason) {
        requireStatus(ClaimStatus.AI_REVIEWING);
        this.aiOpinion = "[AI 검토 실패] " + reason;
        this.status = ClaimStatus.MANUAL_REVIEW;
    }

    public void approve(long paidAmount, String comment) {
        requireDecidable();
        if (paidAmount <= 0 || paidAmount > claimedAmount) {
            throw new BusinessException("지급액은 0원 초과, 청구액(" + claimedAmount + "원) 이하여야 합니다.");
        }
        this.paidAmount = paidAmount;
        decide(ClaimStatus.APPROVED, comment);
    }

    public void reject(String comment) {
        requireDecidable();
        this.paidAmount = 0L;
        decide(ClaimStatus.REJECTED, comment);
    }

    private void decide(ClaimStatus result, String comment) {
        this.status = result;
        this.reviewerComment = comment;
        this.decidedAt = LocalDateTime.now();
    }

    private void requireStatus(ClaimStatus expected) {
        if (status != expected) {
            throw new BusinessException("청구 상태가 " + expected + " 가 아닙니다. (현재: " + status + ")");
        }
    }

    private void requireDecidable() {
        if (!status.isDecidable()) {
            throw new BusinessException("심사 결정을 할 수 없는 상태입니다. (현재: " + status + ")");
        }
    }
}
