package com.ax.core.payment;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentStatus {
    UNPAID("미납"),
    PAID("납입");

    private final String label;
}
