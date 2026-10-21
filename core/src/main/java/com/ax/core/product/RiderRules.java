package com.ax.core.product;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 특약 연관 규칙 검사. DB·스프링에 의존하지 않는 순수 로직 — 보험료 계산, 플랜 저장, 플랜 생성이 같은 검사를 쓴다.
 */
public final class RiderRules {

    public record Rule(CoverageRuleType type, Long coverageId, String coverageName, Long targetId, String targetName,
                       Integer ratioPct) {

        public String text() {
            return switch (type) {
                case REQUIRES -> "'%s' 특약은 '%s'와(과) 함께 가입해야 합니다.".formatted(coverageName, targetName);
                case EXCLUSIVE -> "'%s'와(과) '%s'는(은) 함께 가입할 수 없습니다.".formatted(coverageName, targetName);
                case MAX_RATIO -> "'%s' 가입금액은 '%s' 가입금액의 %d%% 이하여야 합니다.".formatted(coverageName, targetName, ratioPct);
            };
        }
    }

    /** @param amounts 선택한 담보 → 가입금액 */
    public static List<String> violations(List<Rule> rules, Map<Long, Long> amounts) {
        List<String> found = new ArrayList<>();
        for (Rule r : rules) {
            Long amount = amounts.get(r.coverageId());
            if (amount == null) {
                continue;
            }
            Long target = amounts.get(r.targetId());
            boolean violated = switch (r.type()) {
                case REQUIRES -> target == null;
                case EXCLUSIVE -> target != null;
                case MAX_RATIO -> target != null && amount * 100 > target * r.ratioPct();
            };
            if (violated) {
                found.add(r.text());
            }
        }
        return found;
    }

    private RiderRules() {
    }
}
