package com.ax.core.uw;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UwCaseStatus {
    PENDING("심사 대기"),
    COMPLETED("완료");

    private final String label;
}
