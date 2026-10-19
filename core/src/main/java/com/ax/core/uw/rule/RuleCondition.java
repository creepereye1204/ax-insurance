package com.ax.core.uw.rule;

import com.ax.core.common.TreatmentType;
import java.util.List;

/**
 * 룰 조건 (JSON 으로 저장). source 에 따라 사용하는 필드가 다르다.
 *
 * <pre>
 * ANSWER      : questionCode
 * MEDICAL     : kcd(패턴 목록), withinMonths, treatments(선택), cured(선택)
 * CLAIM_COUNT : withinMonths, claimCount(이상)
 * AGE         : ageOver(초과)
 * </pre>
 */
public record RuleCondition(
        ConditionSource source,
        String questionCode,
        List<String> kcd,
        Integer withinMonths,
        List<TreatmentType> treatments,
        Boolean cured,
        Integer claimCount,
        Integer ageOver) {

    public static RuleCondition answer(String questionCode) {
        return new RuleCondition(ConditionSource.ANSWER, questionCode, null, null, null, null, null, null);
    }

    public static RuleCondition medical(List<String> kcd, int withinMonths, List<TreatmentType> treatments, Boolean cured) {
        return new RuleCondition(ConditionSource.MEDICAL, null, kcd, withinMonths, treatments, cured, null, null);
    }

    public static RuleCondition claimCount(int withinMonths, int count) {
        return new RuleCondition(ConditionSource.CLAIM_COUNT, null, null, withinMonths, null, null, count, null);
    }

    public static RuleCondition ageOver(int age) {
        return new RuleCondition(ConditionSource.AGE, null, null, null, null, null, null, age);
    }
}
