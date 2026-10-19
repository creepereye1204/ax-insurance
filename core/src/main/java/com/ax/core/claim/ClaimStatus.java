package com.ax.core.claim;

/**
 * 청구 처리 상태.
 * RECEIVED → AI_REVIEWING → AI_REVIEWED ─┬→ APPROVED
 *                       └→ MANUAL_REVIEW ─┴→ REJECTED
 * AI 는 의견만 제시하고, 최종 승인/거절은 항상 심사자가 한다.
 */
public enum ClaimStatus {
    RECEIVED,       // 접수
    AI_REVIEWING,   // AI 검토 중
    AI_REVIEWED,    // AI 검토 완료, 심사자 결정 대기
    MANUAL_REVIEW,  // AI 검토 실패 → 수동 심사 대기
    APPROVED,       // 지급 승인
    REJECTED;       // 부지급

    public boolean isDecidable() {
        return this == AI_REVIEWED || this == MANUAL_REVIEW;
    }
}
