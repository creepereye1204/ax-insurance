package com.ax.core.customer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ConsentType {
    PRE_UW_INQUIRY("선심사를 위한 보험금 청구이력·외부기관 정보 조회", 30);

    private final String label;
    /** 동의 유효기간(일) */
    private final int validDays;
}
