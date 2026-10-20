package com.ax.core.customer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 금융소비자보호법상 소비자 구분.
 * 일반금융소비자에게는 적합성·적정성 원칙, 설명의무 등이 적용되고 전문금융소비자는 일부 면제된다.
 */
@Getter
@RequiredArgsConstructor
public enum ConsumerType {
    GENERAL("일반금융소비자"),
    PROFESSIONAL("전문금융소비자");

    private final String label;
}
