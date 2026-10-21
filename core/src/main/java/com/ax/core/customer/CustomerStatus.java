package com.ax.core.customer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 고객 유형: 유지 중인 계약이 있으면 계약고객, 없으면 가망고객 */
@Getter
@RequiredArgsConstructor
public enum CustomerStatus {
    PROSPECT("가망고객"),
    CONTRACTED("계약고객");

    private final String label;
}
