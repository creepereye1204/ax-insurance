package com.ax.core.customer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 투자성향. 펀드 위험등급(1 매우높은위험 ~ 6 매우낮은위험)과 비교해 적합성을 판단한다.
 * maxRiskGrade 보다 숫자가 작은(더 위험한) 펀드는 부적합.
 */
@Getter
@RequiredArgsConstructor
public enum InvestmentProfile {
    STABLE("안정형", 5),
    STABLE_SEEKING("안정추구형", 4),
    NEUTRAL("위험중립형", 3),
    ACTIVE("적극투자형", 2),
    AGGRESSIVE("공격투자형", 1);

    private final String label;
    /** 가입 가능한 가장 위험한 등급 */
    private final int maxRiskGrade;

    /** 투자성향 진단 점수(5문항 × 1~5점 = 5~25점)로 성향 산출 */
    public static InvestmentProfile fromScore(int score) {
        if (score <= 8) return STABLE;
        if (score <= 12) return STABLE_SEEKING;
        if (score <= 16) return NEUTRAL;
        if (score <= 20) return ACTIVE;
        return AGGRESSIVE;
    }

    public boolean allows(int fundRiskGrade) {
        return fundRiskGrade >= maxRiskGrade;
    }
}
