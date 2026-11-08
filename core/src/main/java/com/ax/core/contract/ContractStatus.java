package com.ax.core.contract;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ContractStatus {
    ACTIVE("유지"),
    LAPSED("실효"),        // 납입유예기간이 지나도록 보험료 미납 → 효력 상실 (부활 가능)
    TERMINATED("해지"),
    WITHDRAWN("청약철회");

    private final String label;
}
