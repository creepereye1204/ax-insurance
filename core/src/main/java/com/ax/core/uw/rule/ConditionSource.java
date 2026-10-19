package com.ax.core.uw.rule;

/** 룰 조건이 평가하는 데이터 */
public enum ConditionSource {
    /** 고지 문항 응답 (전산심사에서만 존재) */
    ANSWER,
    /** 의료 이력: 전산심사는 고지 상세, 선심사는 청구이력·외부기관 조회 결과 */
    MEDICAL,
    /** 내부 보험금 청구 횟수 */
    CLAIM_COUNT,
    /** 피보험자 나이 */
    AGE
}
