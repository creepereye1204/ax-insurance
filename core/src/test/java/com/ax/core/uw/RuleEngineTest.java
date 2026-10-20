package com.ax.core.uw;

import static com.ax.core.common.TreatmentType.INPATIENT;
import static com.ax.core.common.TreatmentType.MEDICATION;
import static com.ax.core.common.TreatmentType.OUTPATIENT;
import static com.ax.core.product.CoverageCategory.ACCIDENT;
import static com.ax.core.product.CoverageCategory.ANNUITY;
import static com.ax.core.product.CoverageCategory.CANCER;
import static com.ax.core.product.CoverageCategory.MEDICAL;
import static com.ax.core.product.CoverageCategory.SURGERY;
import static org.assertj.core.api.Assertions.assertThat;

import com.ax.core.common.TreatmentType;
import com.ax.core.uw.engine.CoverageOutcome;
import com.ax.core.uw.engine.MedicalRecord;
import com.ax.core.uw.engine.RuleEngine;
import com.ax.core.uw.engine.UwInput;
import com.ax.core.uw.engine.UwResult;
import com.ax.core.uw.rule.RuleCondition;
import com.ax.core.uw.rule.RuleParams;
import com.ax.core.uw.rule.UwAction;
import com.ax.core.uw.rule.UwRule;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuleEngineTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 19);
    static final List<String> STOMACH = List.of("K25*", "K26*", "K29*");

    RuleEngine engine = new RuleEngine();

    List<UwRule> rules = List.of(
            new UwRule("R-AGE-01", "", 10, RuleCondition.ageOver(65), List.of(), UwAction.DECLINE, RuleParams.reason("나이")),
            new UwRule("R-CA-01", "", 30, RuleCondition.medical(List.of("C*"), 60, null, null), List.of(CANCER),
                    UwAction.DECLINE, RuleParams.reason("암")),
            new UwRule("R-CA-02", "", 31, RuleCondition.medical(List.of("C*"), 60, null, null), List.of(MEDICAL, SURGERY),
                    UwAction.REFER, RuleParams.reason("암")),
            new UwRule("R-DM-01", "", 40, RuleCondition.medical(List.of("E11*"), 60, List.of(MEDICATION, INPATIENT), null),
                    List.of(), UwAction.REFER, RuleParams.reason("당뇨")),
            new UwRule("R-HT-01", "", 50, RuleCondition.medical(List.of("I10*"), 60, List.of(MEDICATION), null),
                    List.of(SURGERY), UwAction.RATING, RuleParams.rating("고혈압", 20)),
            new UwRule("R-GI-01", "", 60, RuleCondition.medical(STOMACH, 12, null, false), List.of(MEDICAL, SURGERY),
                    UwAction.EXCLUSION, RuleParams.exclusion("위", "위·십이지장", STOMACH, 24)),
            new UwRule("R-PRG-01", "", 80, RuleCondition.answer("Q5"), List.of(MEDICAL),
                    UwAction.EXCLUSION, RuleParams.exclusion("임신", "임신·출산", List.of("O*"), 12)),
            new UwRule("R-CLM-01", "", 90, RuleCondition.claimCount(12, 3), List.of(MEDICAL),
                    UwAction.REFER, RuleParams.reason("청구")));

    static MedicalRecord record(String kcd, LocalDate date, TreatmentType t, boolean cured) {
        return new MedicalRecord("고지", kcd, date, t, cured);
    }

    UwResult run(int age, Map<String, Boolean> answers, List<MedicalRecord> records, List<LocalDate> claims) {
        return engine.evaluate(1, rules, new UwInput(TODAY, age, 1, answers, records, claims, List.of(MEDICAL, SURGERY, CANCER)));
    }

    @Test
    void 이력이_없으면_전_담보_표준체() {
        UwResult r = run(30, Map.of(), List.of(), List.of());
        assertThat(r.decisions().values()).allMatch(d -> d.outcome() == CoverageOutcome.STANDARD);
    }

    @Test
    void 고위험_직업은_상해만_할증() {
        var jobRule = new UwRule("R-JOB-01", "", 95, RuleCondition.jobRisk(3), List.of(ACCIDENT),
                UwAction.RATING, RuleParams.rating("직업", 50));
        UwResult r = engine.evaluate(1, List.of(jobRule),
                new UwInput(TODAY, 35, 3, Map.of(), List.of(), List.of(), List.of(ACCIDENT, MEDICAL)));
        assertThat(r.decisions().get(ACCIDENT).ratingPct()).isEqualTo(50);
        assertThat(r.decisions().get(MEDICAL).outcome()).isEqualTo(CoverageOutcome.STANDARD);
    }

    @Test
    void 적립_담보는_병력이_있어도_심사하지_않는다() {
        UwResult r = engine.evaluate(1, rules, new UwInput(TODAY, 70, 1, Map.of(),
                List.of(record("C16.9", TODAY.minusYears(1), INPATIENT, false)), List.of(), List.of(ANNUITY)));
        assertThat(r.decisions().get(ANNUITY).outcome()).isEqualTo(CoverageOutcome.STANDARD);
        assertThat(r.decisions().get(ANNUITY).matchedRules()).isEmpty();
    }

    @Test
    void 가입나이_초과면_전_담보_거절() {
        UwResult r = run(70, Map.of(), List.of(), List.of());
        assertThat(r.allDeclined()).isTrue();
    }

    @Test
    void 암_병력은_암진단비_거절_나머지는_본사이관() {
        UwResult r = run(40, Map.of(), List.of(record("C16.9", TODAY.minusYears(2), INPATIENT, true)), List.of());
        assertThat(r.decisions().get(CANCER).outcome()).isEqualTo(CoverageOutcome.DECLINE);
        assertThat(r.decisions().get(MEDICAL).outcome()).isEqualTo(CoverageOutcome.REFER);
        assertThat(r.anyReferred()).isTrue();
    }

    @Test
    void 조회기간이_지난_병력은_반영하지_않는다() {
        UwResult r = run(40, Map.of(), List.of(record("C16.9", TODAY.minusYears(6), INPATIENT, true)), List.of());
        assertThat(r.decisions().get(CANCER).outcome()).isEqualTo(CoverageOutcome.STANDARD);
    }

    @Test
    void 위염_치료중이면_실손과_수술비에_부담보() {
        UwResult r = run(35, Map.of(), List.of(record("K29.7", TODAY.minusMonths(5), OUTPATIENT, false)), List.of());
        var medical = r.decisions().get(MEDICAL);
        assertThat(medical.outcome()).isEqualTo(CoverageOutcome.CONDITIONAL);
        assertThat(medical.exclusions()).singleElement().satisfies(e -> {
            assertThat(e.label()).isEqualTo("위·십이지장");
            assertThat(e.months()).isEqualTo(24);
        });
        assertThat(r.decisions().get(CANCER).outcome()).isEqualTo(CoverageOutcome.STANDARD);
    }

    @Test
    void 위염_완치면_부담보_없음() {
        UwResult r = run(35, Map.of(), List.of(record("K29.7", TODAY.minusMonths(5), OUTPATIENT, true)), List.of());
        assertThat(r.decisions().get(MEDICAL).outcome()).isEqualTo(CoverageOutcome.STANDARD);
    }

    @Test
    void 고혈압_투약은_질병수술비만_할증() {
        UwResult r = run(50, Map.of(), List.of(record("I10", TODAY.minusYears(1), MEDICATION, false)), List.of());
        assertThat(r.decisions().get(SURGERY).ratingPct()).isEqualTo(20);
        assertThat(r.decisions().get(MEDICAL).outcome()).isEqualTo(CoverageOutcome.STANDARD);
    }

    @Test
    void 가장_보수적인_결과를_택한다_부담보와_본사이관이_겹치면_이관() {
        UwResult r = run(50, Map.of(), List.of(
                record("K29.7", TODAY.minusMonths(2), OUTPATIENT, false),
                record("E11.9", TODAY.minusYears(2), MEDICATION, false)), List.of());
        var medical = r.decisions().get(MEDICAL);
        assertThat(medical.outcome()).isEqualTo(CoverageOutcome.REFER);
        assertThat(medical.exclusions()).hasSize(1); // 이관 시 부담보는 심사자에게 제안으로 남긴다
        assertThat(medical.matchedRules()).containsExactly("R-DM-01", "R-GI-01");
    }

    @Test
    void 임신_고지는_실손에만_임신출산_부담보() {
        UwResult r = run(30, Map.of("Q5", true), List.of(), List.of());
        assertThat(r.decisions().get(MEDICAL).exclusions()).extracting("label").containsExactly("임신·출산");
        assertThat(r.decisions().get(SURGERY).outcome()).isEqualTo(CoverageOutcome.STANDARD);
    }

    @Test
    void 최근_1년_청구_3회_이상이면_실손_본사이관() {
        UwResult r = run(30, Map.of(), List.of(),
                List.of(TODAY.minusMonths(1), TODAY.minusMonths(4), TODAY.minusMonths(11), TODAY.minusMonths(20)));
        assertThat(r.decisions().get(MEDICAL).outcome()).isEqualTo(CoverageOutcome.REFER);
        assertThat(r.decisions().get(SURGERY).outcome()).isEqualTo(CoverageOutcome.STANDARD);
    }
}
