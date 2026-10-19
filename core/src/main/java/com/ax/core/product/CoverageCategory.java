package com.ax.core.product;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 담보 분류. 심사 룰은 담보 개별이 아니라 분류 단위로 적용한다. */
@Getter
@RequiredArgsConstructor
public enum CoverageCategory {
    MEDICAL("실손의료비"),
    SURGERY("질병수술비"),
    CANCER("암진단비");

    private final String label;
}
