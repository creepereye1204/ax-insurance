package com.ax.core.product;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 상품 유형.
 * savings: 저축성(월 납입액을 고객이 정하고 적립금이 쌓이는 상품) — 해지환급금 예시표를 보여준다.
 * variable: 변액(펀드에 투자) — 펀드 선택·투자수익률 시나리오가 필요하다.
 */
@Getter
@RequiredArgsConstructor
public enum ProductType {
    WHOLE_LIFE("종신보험", false, false),
    TERM_LIFE("정기보험", false, false),
    HEALTH("건강보험", false, false),
    CANCER("암보험", false, false),
    MEDICAL("실손의료보험", false, false),
    CHILD("어린이보험", false, false),
    DRIVER("운전자보험", false, false),
    DENTAL("치아보험", false, false),
    ANNUITY("연금보험", true, false),
    VARIABLE_ANNUITY("변액연금보험", true, true),
    VARIABLE_UNIVERSAL("변액유니버설보험", true, true),
    SAVINGS("저축보험", true, false);

    private final String label;
    private final boolean savings;
    private final boolean variable;
}
