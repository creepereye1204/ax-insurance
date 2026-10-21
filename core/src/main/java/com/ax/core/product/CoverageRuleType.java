package com.ax.core.product;

/** 특약 연관 규칙 유형 */
public enum CoverageRuleType {
    /** 담보 A 는 담보 B 와 함께 가입해야 한다 (예: 암입원일당 → 암수술) */
    REQUIRES,
    /** 담보 A 와 B 는 함께 가입할 수 없다 (예: 표준형 / 선택형) */
    EXCLUSIVE,
    /** 담보 A 가입금액 ≤ 담보 B 가입금액 × ratioPct% (예: 재해사망 ≤ 일반사망) */
    MAX_RATIO
}
