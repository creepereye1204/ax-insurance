package com.ax.core.uw.engine;

import java.util.Collection;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 심사 신호등. 설계사가 선심사·전산심사 결과를 한눈에 판단하도록 담보별 결과를 세 단계로 묶는다.
 * 전체 신호는 청약 제출 시의 처리 흐름과 같게 정한다 (UwService.autoUnderwrite 분기와 일치).
 */
@Getter
@RequiredArgsConstructor
public enum Signal {
    GREEN("초록", "표준체 인수 예상"),
    YELLOW("노랑", "조건부 인수 또는 본사 심사"),
    RED("빨강", "연기·거절 예상");

    private final String color;
    private final String label;

    public static Signal of(CoverageOutcome outcome) {
        return switch (outcome) {
            case STANDARD -> GREEN;
            case CONDITIONAL, REFER -> YELLOW;
            case POSTPONE, DECLINE -> RED;
        };
    }

    /** 한 담보라도 연기면 청약 전체가 연기되고, 전 담보 거절이면 거절이다. 일부 거절은 나머지 담보로 인수되므로 노랑. */
    public static Signal overall(Collection<CoverageOutcome> outcomes) {
        if (outcomes.contains(CoverageOutcome.POSTPONE)
                || (!outcomes.isEmpty() && outcomes.stream().allMatch(o -> o == CoverageOutcome.DECLINE))) {
            return RED;
        }
        return outcomes.stream().allMatch(o -> o == CoverageOutcome.STANDARD) ? GREEN : YELLOW;
    }
}
