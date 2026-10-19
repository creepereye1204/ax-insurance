package com.ax.core.uw.engine;

import com.ax.core.kcd.Kcd;
import com.ax.core.product.CoverageCategory;
import com.ax.core.uw.rule.ConditionSource;
import com.ax.core.uw.rule.RuleCondition;
import com.ax.core.uw.rule.RuleParams;
import com.ax.core.uw.rule.UwRule;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import org.springframework.stereotype.Component;

/**
 * 심사 룰 엔진. DB·스프링에 의존하지 않는 순수 로직이라 단위 테스트로 룰별 검증이 가능하다.
 *
 * <p>조건 평가는 source 별 평가기(전략)로 분리했다. 새 조건 유형이 필요하면 평가기만 추가한다.
 * 담보별 결과는 매칭된 룰 중 가장 보수적인 것을 택한다:
 * DECLINE > POSTPONE > REFER > (EXCLUSION, RATING → CONDITIONAL) > STANDARD
 */
@Component
public class RuleEngine {

    private final Map<ConditionSource, BiPredicate<RuleCondition, UwInput>> evaluators = new EnumMap<>(Map.of(
            ConditionSource.ANSWER, RuleEngine::matchAnswer,
            ConditionSource.MEDICAL, RuleEngine::matchMedical,
            ConditionSource.CLAIM_COUNT, RuleEngine::matchClaimCount,
            ConditionSource.AGE, (c, in) -> in.age() > c.ageOver()));

    public UwResult evaluate(int ruleSetVersion, List<UwRule> rules, UwInput input) {
        List<UwRule> matched = rules.stream()
                .filter(r -> evaluators.get(r.getCondition().source()).test(r.getCondition(), input))
                .toList();

        Map<CoverageCategory, CoverageDecision> decisions = new LinkedHashMap<>();
        for (CoverageCategory category : input.coverages()) {
            decisions.put(category, decide(category, matched));
        }
        return new UwResult(ruleSetVersion, decisions);
    }

    private CoverageDecision decide(CoverageCategory category, List<UwRule> matched) {
        CoverageOutcome outcome = CoverageOutcome.STANDARD;
        List<Exclusion> exclusions = new ArrayList<>();
        int rating = 0;
        List<String> codes = new ArrayList<>();
        List<String> reasons = new ArrayList<>();

        for (UwRule rule : matched) {
            if (!rule.appliesTo(category)) {
                continue;
            }
            RuleParams p = rule.getParams();
            codes.add(rule.getCode());
            if (p.reason() != null) {
                reasons.add(p.reason());
            }
            outcome = outcome.max(switch (rule.getAction()) {
                case STANDARD -> CoverageOutcome.STANDARD;
                case RATING -> {
                    rating = Math.max(rating, p.ratingPct());
                    yield CoverageOutcome.CONDITIONAL;
                }
                case EXCLUSION -> {
                    exclusions.add(new Exclusion(p.exclusionLabel(), p.exclusionKcd(), p.exclusionMonths()));
                    yield CoverageOutcome.CONDITIONAL;
                }
                case REFER -> CoverageOutcome.REFER;
                case POSTPONE -> CoverageOutcome.POSTPONE;
                case DECLINE -> CoverageOutcome.DECLINE;
            });
        }
        // 연기·거절이면 부담보·할증 조건은 의미가 없다 (본사 이관 시에는 심사자에게 제안으로 남긴다)
        if (outcome == CoverageOutcome.POSTPONE || outcome == CoverageOutcome.DECLINE) {
            exclusions.clear();
            rating = 0;
        }
        return new CoverageDecision(category, outcome, List.copyOf(exclusions), rating, codes, reasons);
    }

    // ---------------------------------------------------------------- 조건 평가기

    private static boolean matchAnswer(RuleCondition c, UwInput in) {
        return Boolean.TRUE.equals(in.answers().get(c.questionCode()));
    }

    private static boolean matchMedical(RuleCondition c, UwInput in) {
        var from = in.asOf().minusMonths(c.withinMonths());
        return in.records().stream().anyMatch(r ->
                c.kcd().stream().anyMatch(p -> Kcd.matches(r.kcdCode(), p))
                        && !r.diagnosedOn().isBefore(from)
                        && (c.treatments() == null || c.treatments().contains(r.treatment()))
                        && (c.cured() == null || c.cured() == r.cured()));
    }

    private static boolean matchClaimCount(RuleCondition c, UwInput in) {
        var from = in.asOf().minusMonths(c.withinMonths());
        return in.claimDates().stream().filter(d -> !d.isBefore(from)).count() >= c.claimCount();
    }
}
