package com.ax.core.application;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 청약 상태.
 * 제출 → 전산심사 ─┬→ ISSUED (자동 승인 후 계약 성립)
 *                ├→ UNDER_REVIEW (본사 이관) ─→ ISSUED / DECLINED
 *                ├→ POSTPONED
 *                └→ DECLINED
 */
@Getter
@RequiredArgsConstructor
public enum ApplicationStatus {
    UNDER_REVIEW("본사심사 중"),
    ISSUED("계약 성립"),
    POSTPONED("연기"),
    DECLINED("거절");

    private final String label;
}
