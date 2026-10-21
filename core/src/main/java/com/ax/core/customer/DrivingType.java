package com.ax.core.customer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 운전 여부·차종. 상해 위험 심사(할증)와 운전자보험 가입 가능 여부에 쓴다. */
@Getter
@RequiredArgsConstructor
public enum DrivingType {
    NONE("비운전"),
    PRIVATE("자가용"),
    COMMERCIAL("영업용"),
    MOTORCYCLE("이륜자동차");

    private final String label;
}
