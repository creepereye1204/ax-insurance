package com.ax.core.uw.rule;

/**
 * 룰 적용 범위. 간편심사형(3-2-5) 상품은 고지 범위가 좁아 표준 심사 룰(당뇨·고혈압 등)을 적용하지 않고
 * 간편고지 해당 여부로만 인수한다.
 */
public enum RuleScope {
    ALL,
    STANDARD,
    SIMPLIFIED;

    public boolean appliesTo(boolean simplifiedUw) {
        return this == ALL || (this == SIMPLIFIED) == simplifiedUw;
    }
}
