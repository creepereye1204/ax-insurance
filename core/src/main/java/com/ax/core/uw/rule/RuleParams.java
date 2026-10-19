package com.ax.core.uw.rule;

import java.util.List;

/** 룰 결과 파라미터 (JSON 으로 저장). action 에 따라 사용하는 필드가 다르다. */
public record RuleParams(
        String reason,
        String exclusionLabel,
        List<String> exclusionKcd,
        Integer exclusionMonths,
        Integer ratingPct) {

    public static RuleParams reason(String reason) {
        return new RuleParams(reason, null, null, null, null);
    }

    public static RuleParams exclusion(String reason, String label, List<String> kcd, int months) {
        return new RuleParams(reason, label, kcd, months, null);
    }

    public static RuleParams rating(String reason, int pct) {
        return new RuleParams(reason, null, null, null, pct);
    }
}
