package com.ax.core.uw.rule;

/** 룰 조건이 평가하는 데이터 */
public enum ConditionSource {
    /** 고지 문항 응답 (전산심사, 고지를 입력한 선심사) */
    ANSWER,
    /** 의료 이력: 고지 상세 + (선심사) 청구이력·외부기관 조회 결과 */
    MEDICAL,
    /** 내부 보험금 청구 횟수 */
    CLAIM_COUNT,
    /** 피보험자 나이 */
    AGE,
    /** 직업 위험등급 */
    JOB_RISK,
    /** 운전 여부·차종 */
    DRIVING
}
