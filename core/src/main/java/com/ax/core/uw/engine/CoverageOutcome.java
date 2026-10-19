package com.ax.core.uw.engine;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 담보별 최종 심사 결과. severity 가 클수록 보수적이다. */
@Getter
@RequiredArgsConstructor
public enum CoverageOutcome {
    STANDARD(0, "표준체"),
    CONDITIONAL(1, "조건부 인수"),
    REFER(2, "본사 이관"),
    POSTPONE(3, "연기"),
    DECLINE(4, "거절");

    private final int severity;
    private final String label;

    public boolean isAccepted() {
        return this == STANDARD || this == CONDITIONAL;
    }

    public CoverageOutcome max(CoverageOutcome other) {
        return other.severity > severity ? other : this;
    }
}
