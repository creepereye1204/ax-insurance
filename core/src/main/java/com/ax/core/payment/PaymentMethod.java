package com.ax.core.payment;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentMethod {
    AUTO_TRANSFER("자동이체"),
    DIRECT("직접납입(가상계좌)"),
    CARD("카드");

    private final String label;
}
