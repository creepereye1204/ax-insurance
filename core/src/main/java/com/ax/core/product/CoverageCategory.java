package com.ax.core.product;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 담보 분류. 심사 룰은 담보 개별이 아니라 분류 단위로 적용한다.
 * underwritten=false 인 적립 담보(연금·저축)는 위험 심사 대상이 아니다.
 */
@Getter
@RequiredArgsConstructor
public enum CoverageCategory {
    DEATH("사망", true),
    ACCIDENT("상해", true),
    MEDICAL("실손의료비", true),
    SURGERY("수술비", true),
    HOSPITAL("입원일당", true),
    CI("뇌·심장 진단비", true),
    CANCER("암진단비", true),
    DENTAL("치아치료비", true),
    ANNUITY("연금 적립", false),
    SAVINGS("저축 적립", false);

    private final String label;
    private final boolean underwritten;
}
