package com.ax.core.uw.rule;

/** 룰이 매칭됐을 때의 처리 */
public enum UwAction {
    STANDARD,   // 표준체 (명시적 통과)
    RATING,     // 할증
    EXCLUSION,  // 부담보
    REFER,      // 본사 심사 이관
    POSTPONE,   // 연기
    DECLINE     // 거절
}
