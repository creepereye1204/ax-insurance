package com.ax.core.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RiderRulesTest {

    static final long CHEON = 10_000_000;

    // 1 암진단, 2 암수술, 3 암입원일당, 4 표준형, 5 선택형
    final List<RiderRules.Rule> rules = List.of(
            new RiderRules.Rule(CoverageRuleType.REQUIRES, 3L, "암입원일당", 2L, "암수술", null),
            new RiderRules.Rule(CoverageRuleType.MAX_RATIO, 2L, "암수술", 1L, "암진단", 50),
            new RiderRules.Rule(CoverageRuleType.EXCLUSIVE, 4L, "표준형", 5L, "선택형", null));

    @Test
    void 규칙을_지키면_위반_없음() {
        assertThat(RiderRules.violations(rules, Map.of(1L, 3 * CHEON, 2L, CHEON, 3L, 30_000L))).isEmpty();
    }

    @Test
    void 필수_동반_특약이_없으면_위반() {
        assertThat(RiderRules.violations(rules, Map.of(1L, 3 * CHEON, 3L, 30_000L)))
                .containsExactly("'암입원일당' 특약은 '암수술'와(과) 함께 가입해야 합니다.");
    }

    @Test
    void 가입금액_비율_한도는_경계값까지_허용() {
        assertThat(RiderRules.violations(rules, Map.of(1L, 2 * CHEON, 2L, CHEON))).isEmpty();
        assertThat(RiderRules.violations(rules, Map.of(1L, 2 * CHEON, 2L, CHEON + 1_000_000)))
                .singleElement().asString().contains("50% 이하");
    }

    @Test
    void 동시_가입_불가() {
        assertThat(RiderRules.violations(rules, Map.of(4L, CHEON, 5L, CHEON))).hasSize(1);
        assertThat(RiderRules.violations(rules, Map.of(4L, CHEON))).isEmpty();
    }

    @Test
    void 대상_담보를_고르지_않으면_비율_한도는_검사하지_않는다() {
        assertThat(RiderRules.violations(rules, Map.of(2L, CHEON))).isEmpty();
    }
}
