package com.ax.core.demo;

import static com.ax.core.common.TreatmentType.INPATIENT;
import static com.ax.core.common.TreatmentType.MEDICATION;
import static com.ax.core.common.TreatmentType.OUTPATIENT;
import static com.ax.core.product.CoverageCategory.ACCIDENT;
import static com.ax.core.product.CoverageCategory.CANCER;
import static com.ax.core.product.CoverageCategory.CI;
import static com.ax.core.product.CoverageCategory.DEATH;
import static com.ax.core.product.CoverageCategory.HOSPITAL;
import static com.ax.core.product.CoverageCategory.MEDICAL;
import static com.ax.core.product.CoverageCategory.SURGERY;

import com.ax.core.application.Agreement;
import com.ax.core.application.ApplicationService;
import com.ax.core.application.DisclosureForm.AnswerInput;
import com.ax.core.application.ApplicationService.Signature;
import com.ax.core.claim.Claim;
import com.ax.core.claim.ClaimRepository;
import com.ax.core.claim.ClaimType;
import com.ax.core.common.TreatmentType;
import com.ax.core.contract.Contract;
import com.ax.core.contract.ContractCoverage;
import com.ax.core.contract.ContractRepository;
import com.ax.core.corporate.CorporateRepository;
import com.ax.core.corporate.CorporateService;
import com.ax.core.customer.ConsumerType;
import com.ax.core.customer.Customer;
import com.ax.core.customer.CustomerRepository;
import com.ax.core.customer.DrivingType;
import com.ax.core.customer.Gender;
import com.ax.core.customer.InvestmentProfile;
import com.ax.core.disclosure.Questionnaire;
import com.ax.core.disclosure.QuestionnaireRepository;
import com.ax.core.external.ExternalMedicalRecord;
import com.ax.core.external.ExternalMedicalRecordRepository;
import com.ax.core.kcd.Kcd;
import com.ax.core.kcd.KcdRepository;
import com.ax.core.product.Coverage;
import com.ax.core.product.CoverageRule;
import com.ax.core.product.CoverageRuleRepository;
import com.ax.core.product.PlanService;
import com.ax.core.product.FundRepository;
import com.ax.core.product.Product;
import com.ax.core.product.ProductPlan;
import com.ax.core.product.ProductPlanRepository;
import com.ax.core.product.ProductRepository;
import com.ax.core.product.RiderRules;
import com.ax.core.proposal.PremiumCalculator;
import com.ax.core.proposal.Proposal;
import com.ax.core.proposal.ProposalService;
import com.ax.core.uw.rule.RuleCondition;
import com.ax.core.uw.rule.RuleParams;
import com.ax.core.uw.rule.RuleScope;
import com.ax.core.uw.rule.UwAction;
import com.ax.core.uw.rule.UwRule;
import com.ax.core.uw.rule.UwRuleSet;
import com.ax.core.uw.rule.UwRuleSetRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기준 데이터(질병코드·상품·펀드·요율·질문지·심사 룰)와 시나리오 고객. 모든 데이터는 가상이다.
 * 시나리오 설명: docs/scenarios.md
 */
@Slf4j
@Order(1)
@Component
@RequiredArgsConstructor
public class DemoDataLoader implements ApplicationRunner {

    private static final String EXTERNAL = "타사A생명(Mock)";

    private final KcdRepository kcdRepository;
    private final ProductRepository productRepository;
    private final FundRepository fundRepository;
    private final QuestionnaireRepository questionnaireRepository;
    private final UwRuleSetRepository ruleSetRepository;
    private final CoverageRuleRepository coverageRuleRepository;
    private final ProductPlanRepository planRepository;
    private final CorporateRepository corporateRepository;
    private final CorporateService corporateService;
    private final CustomerRepository customerRepository;
    private final ExternalMedicalRecordRepository externalRecords;
    private final ContractRepository contractRepository;
    private final ClaimRepository claimRepository;
    private final ProposalService proposalService;
    private final ApplicationService applicationService;
    private final JdbcTemplate jdbc;
    private final EntityManager em;

    @Value("${ax.demo.enabled:true}")
    private boolean enabled;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        if (!productRepository.existsByCode(ProductCatalog.SAMPLE_CODE)) {
            loadInitial();
        }
        upgradeReferenceData();
    }

    private void loadInitial() {
        long started = System.currentTimeMillis();
        loadKcd();
        fundRepository.saveAll(ProductCatalog.funds());
        Product sample = productRepository.save(ProductCatalog.sample());
        productRepository.saveAll(ProductCatalog.generate());
        em.flush();
        int rates = generatePremiumRates();
        Questionnaire questionnaire = questionnaireRepository.save(questionnaire());
        ruleSetRepository.save(ruleSetV1());
        em.flush();
        loadScenarioCustomers(sample, questionnaire);
        log.info("기준 데이터 적재 완료: 상품 {}개, 요율 {}행 ({}ms)", productRepository.count(), rates,
                System.currentTimeMillis() - started);
    }

    /**
     * 기준 데이터 변경분. 운영 DB 에도 적용되도록 이미 있으면 건너뛴다(반복 실행 안전).
     * 룰셋은 수정하지 않고 새 버전을 만들어 활성 버전만 바꾼다 — 과거 심사 건은 그때의 버전 번호로 재현한다.
     */
    private void upgradeReferenceData() {
        if (!questionnaireRepository.existsByCode(Questionnaire.SIMPLIFIED_CODE)) {
            questionnaireRepository.save(simplifiedQuestionnaire());
            log.info("간편고지(3-2-5) 질문지 v1 등록");
        }
        activateRuleSet(ruleSetV2());
        activateRuleSet(ruleSetV3());
        if (coverageRuleRepository.count() == 0) {
            log.info("특약 연관 규칙 {}건 등록", generateCoverageRules());
        }
        if (planRepository.count() == 0) {
            Map<Long, List<RiderRules.Rule>> rules = coverageRuleRepository.findAllByOrderById().stream()
                    .collect(Collectors.groupingBy(r -> r.getProduct().getId(), Collectors.mapping(CoverageRule::spec, Collectors.toList())));
            List<ProductPlan> plans = new ArrayList<>();
            for (Product p : productRepository.findAll()) {
                plans.addAll(PlanService.defaultPlans(p, rules.getOrDefault(p.getId(), List.of())));
            }
            planRepository.saveAll(plans);
            log.info("상품 기본 플랜 {}건 등록", plans.size());
        }
        if (corporateRepository.count() == 0) {
            // 시나리오 법인: 일반 단체(30명) / 고위험 업종 대규모(60명) / 단체 최소 인원 미달(4명)
            corporateService.createVirtual("Ax테크(가상)", "IT 서비스", 30);
            corporateService.createVirtual("한빛물류(가상)", "물류·운송", 60);
            corporateService.createVirtual("소담카페(가상)", "요식업", 4);
            log.info("시나리오 법인 3곳 등록 (종업원 94명)");
        }
    }

    /** 새 버전 룰셋이 없으면 저장하고 활성 버전을 교체한다 */
    private void activateRuleSet(UwRuleSet next) {
        if (ruleSetRepository.existsByVersion(next.getVersion())) {
            return;
        }
        ruleSetRepository.findByActiveTrue().forEach(UwRuleSet::deactivate);
        em.flush();
        ruleSetRepository.save(next);
        log.info("심사 룰셋 v{} 활성화 — {}", next.getVersion(), next.getDescription());
    }

    /**
     * 특약 연관 규칙 (가상). 상품 템플릿의 담보 코드 접미사로 같은 상품 안의 담보끼리 연결한다.
     * <ul>
     *   <li>종신: 재해사망 ≤ 일반사망 100%</li>
     *   <li>암: 암입원일당은 암수술과 함께, 암수술 ≤ 암진단 50%</li>
     *   <li>어린이: 질병수술비는 질병입원일당과 함께</li>
     * </ul>
     */
    private int generateCoverageRules() {
        String sql = """
                insert into coverage_rule (product_id, type, coverage_id, target_id, ratio_pct)
                select a.product_id, cast(? as varchar), a.id, b.id, cast(? as integer)
                from coverage a
                join coverage b on b.product_id = a.product_id
                join product p on p.id = a.product_id
                where p.type = ? and a.code like ? and b.code like ?
                """;
        Object[][] rules = {
                {"MAX_RATIO", 100, "WHOLE_LIFE", "ADT", "DTH"},
                {"REQUIRES", null, "CANCER", "CHO", "CSU"},
                {"MAX_RATIO", 50, "CANCER", "CSU", "CAN"},
                {"REQUIRES", null, "CHILD", "SUR", "HOS"},
        };
        int total = 0;
        for (Object[] r : rules) {
            total += jdbc.update(sql, r[0], r[1], r[2], "%-" + r[3], "%-" + r[4]);
        }
        return total;
    }

    /** 요율표 생성: 담보 기초요율 × 나이 증가율 × 성별 × 납입기간 계수 */
    private int generatePremiumRates() {
        return jdbc.update("""
                insert into premium_rate (coverage_id, gender, age, payment_term, rate_per10m)
                select cv.id, g.gender, a.age, t.term::int,
                       greatest(1, round(cv.base_rate_per10m
                           * power(1 + cv.age_growth_permille / 1000.0, a.age - 30)
                           * case when cv.category in ('CANCER', 'DENTAL')
                                  then (case when g.gender = 'M' then 0.95 else 1.05 end)
                                  else (case when g.gender = 'M' then 1.08 else 0.93 end) end
                           * case when t.term::int <= 1 then 1 else power(20.0 / t.term::int, 0.85) end))
                from coverage cv
                join product p on p.id = cv.product_id
                cross join (values ('M'), ('F')) g(gender)
                cross join lateral generate_series(p.min_entry_age, p.max_entry_age) a(age)
                cross join lateral jsonb_array_elements_text(p.payment_terms) t(term)
                where cv.category not in ('ANNUITY', 'SAVINGS')
                """);
    }

    private void loadKcd() {
        String[][] codes = {
                {"J00", "급성 비인두염(감기)"}, {"J20.9", "급성 기관지염"}, {"J45.9", "천식"},
                {"A09", "감염성 위장염 및 결장염"}, {"K29.7", "위염"}, {"K25.9", "위궤양"}, {"K26.9", "십이지장궤양"},
                {"K35.8", "급성 충수염(맹장염)"}, {"K80.2", "담석증"},
                {"E10.9", "1형 당뇨병"}, {"E11.9", "2형 당뇨병"}, {"E14.9", "상세불명의 당뇨병"},
                {"I10", "본태성 고혈압"}, {"I21.9", "급성 심근경색증"}, {"I63.9", "뇌경색증"},
                {"C16.9", "위암"}, {"C18.9", "결장암"}, {"C34.9", "폐암"}, {"C50.9", "유방암"}, {"C73", "갑상선암"},
                {"S42.0", "쇄골 골절"}, {"S52.5", "요골 하단 골절"}, {"S82.2", "경골 골절"},
                {"M51.2", "추간판 장애(디스크)"}, {"M17.9", "무릎관절증"}, {"H25.9", "노년 백내장"},
                {"N20.0", "신장결석"}, {"D25.9", "자궁근종"}, {"L30.1", "한포진"}, {"B35.3", "발백선(무좀)"},
                {"F32.9", "우울 에피소드"}, {"K02.9", "치아우식(충치)"}, {"O80", "단일 자연분만"},
                {"O82", "제왕절개에 의한 단일분만"}, {"Z00.0", "일반 건강검진"},
        };
        for (String[] c : codes) {
            kcdRepository.save(new Kcd(c[0], c[1]));
        }
    }

    private Questionnaire questionnaire() {
        return new Questionnaire(Questionnaire.STANDARD_CODE, 1, "계약 전 알릴의무 (표준, 간소화)", LocalDate.of(2026, 1, 1))
                .addQuestion("Q1", "최근 3개월 이내에 의사로부터 진찰·검사를 통해 질병 확정진단, 질병 의심소견, 치료, 입원, 수술, 투약을 받은 사실이 있습니까?", 3, true)
                .addQuestion("Q2", "최근 1년 이내에 의사로부터 진찰·검사를 받고 그 결과 추가검사(재검사)를 받은 사실이 있습니까?", 12, true)
                .addQuestion("Q3", "최근 5년 이내에 입원, 수술, 계속하여 7일 이상 치료, 계속하여 30일 이상 투약을 받은 사실이 있습니까?", 60, true)
                .addQuestion("Q4", "최근 5년 이내에 암, 고혈압, 당뇨병, 뇌졸중, 심근경색 등 주요 질병으로 진단·치료·입원·수술·투약을 받은 사실이 있습니까?", 60, true)
                .addQuestion("Q5", "현재 임신 중입니까?", 0, false);
    }

    /** 간편고지 3-2-5: 3개월 내 입원·수술·추가검사 소견 / 2년 내 입원·수술 / 5년 내 3대 질병 */
    private Questionnaire simplifiedQuestionnaire() {
        return new Questionnaire(Questionnaire.SIMPLIFIED_CODE, 1, "계약 전 알릴의무 (간편고지 3-2-5)", LocalDate.of(2026, 1, 1))
                .addQuestion("S1", "최근 3개월 이내에 의사로부터 입원, 수술(제왕절개 포함), 추가검사(재검사)가 필요하다는 소견을 받은 사실이 있습니까?", 3, true)
                .addQuestion("S2", "최근 2년 이내에 질병이나 사고로 입원 또는 수술(제왕절개 포함)을 받은 사실이 있습니까?", 24, true)
                .addQuestion("S3", "최근 5년 이내에 암, 뇌졸중, 심근경색으로 진단, 입원 또는 수술을 받은 사실이 있습니까?", 60, true);
    }

    private UwRuleSet ruleSetV1() {
        UwRuleSet set = new UwRuleSet(1, "데모 심사 기준 (가상)");
        standardRules(RuleScope.ALL).forEach(set::add);
        return set;
    }

    /**
     * v2: 표준 심사 룰은 일반심사 상품에만, 간편고지 룰은 간편심사형 상품에만 적용한다.
     * 간편고지 룰은 고지 응답과 함께 의료 이력(고지 상세·선심사 조회 이력)도 본다 —
     * 선심사 때 조회 이력만으로 "간편고지 해당 가능성"을 미리 알려 주기 위해서다.
     */
    private UwRuleSet ruleSetV2() {
        UwRuleSet set = new UwRuleSet(2, "데모 심사 기준 (가상) — 간편심사형 3-2-5 분리");
        simplifiedRules().forEach(set::add);
        standardRules(RuleScope.STANDARD).forEach(set::add);
        return set;
    }

    /** v3: v2 + 운전 위험 (이륜자동차·영업용 운전은 상해 담보 할증, 심사 유형 무관) */
    private UwRuleSet ruleSetV3() {
        UwRuleSet set = new UwRuleSet(3, "데모 심사 기준 (가상) — 운전 위험 추가");
        simplifiedRules().forEach(set::add);
        standardRules(RuleScope.STANDARD).forEach(set::add);
        set.add(new UwRule("R-DRV-01", "이륜자동차 운전", 96, RuleCondition.driving(List.of(DrivingType.MOTORCYCLE)),
                        List.of(ACCIDENT), UwAction.RATING, RuleParams.rating("이륜자동차 운전 — 상해 담보 할증", 30), RuleScope.ALL))
                .add(new UwRule("R-DRV-02", "영업용 운전", 97, RuleCondition.driving(List.of(DrivingType.COMMERCIAL)),
                        List.of(ACCIDENT), UwAction.RATING, RuleParams.rating("영업용 차량 운전 — 상해 담보 할증", 10), RuleScope.ALL));
        return set;
    }

    private List<UwRule> simplifiedRules() {
        List<String> major = List.of("C*", "I21*", "I22*", "I60*", "I61*", "I62*", "I63*", "I64*");
        List<TreatmentType> admitted = List.of(INPATIENT, TreatmentType.SURGERY);
        return List.of(new UwRule("R-SMP-01", "간편고지 1 해당", 10, RuleCondition.answer("S1"), List.of(), UwAction.DECLINE,
                        RuleParams.reason("간편고지 1(3개월 내 입원·수술·추가검사 소견) 해당 — 간편심사형 인수 불가"), RuleScope.SIMPLIFIED),
                new UwRule("R-SMP-02", "간편고지 2 해당", 11, RuleCondition.answer("S2"), List.of(), UwAction.DECLINE,
                        RuleParams.reason("간편고지 2(2년 내 입원·수술) 해당 — 간편심사형 인수 불가"), RuleScope.SIMPLIFIED),
                new UwRule("R-SMP-03", "간편고지 3 해당", 12, RuleCondition.answer("S3"), List.of(), UwAction.DECLINE,
                        RuleParams.reason("간편고지 3(5년 내 암·뇌졸중·심근경색) 해당 — 간편심사형 인수 불가"), RuleScope.SIMPLIFIED),
                new UwRule("R-SMP-11", "3개월 내 입원·수술 이력", 13, RuleCondition.medical(List.of("*"), 3, admitted, null),
                        List.of(), UwAction.DECLINE, RuleParams.reason("3개월 내 입원·수술 이력 — 간편고지 1 해당"), RuleScope.SIMPLIFIED),
                new UwRule("R-SMP-12", "2년 내 입원·수술 이력", 14, RuleCondition.medical(List.of("*"), 24, admitted, null),
                        List.of(), UwAction.DECLINE, RuleParams.reason("2년 내 입원·수술 이력 — 간편고지 2 해당"), RuleScope.SIMPLIFIED),
                new UwRule("R-SMP-13", "5년 내 3대 질병 이력", 15, RuleCondition.medical(major, 60, null, null),
                        List.of(), UwAction.DECLINE, RuleParams.reason("5년 내 암·뇌졸중·심근경색 이력 — 간편고지 3 해당"), RuleScope.SIMPLIFIED));
    }

    /** 표준 심사 룰. 잦은 청구·고위험 직업은 심사 유형과 무관하므로 항상 전체 적용 */
    private List<UwRule> standardRules(RuleScope medical) {
        List<String> diabetes = List.of("E10*", "E11*", "E12*", "E13*", "E14*");
        List<String> stomach = List.of("K25*", "K26*", "K29*");
        List<String> fracture = List.of("S42*", "S52*", "S82*");
        return List.of(
                new UwRule("R-RCT-01", "최근 입원·수술 치료 중", 20,
                        RuleCondition.medical(List.of("*"), 3, List.of(INPATIENT, TreatmentType.SURGERY), false), List.of(),
                        UwAction.POSTPONE, RuleParams.reason("최근 3개월 내 입원·수술 후 치료 중 — 치료 종결 후 재청약"), medical),
                new UwRule("R-CA-01", "암 병력 — 암진단", 30, RuleCondition.medical(List.of("C*"), 60, null, null),
                        List.of(CANCER), UwAction.DECLINE, RuleParams.reason("5년 내 암 병력 — 암진단 담보 인수 불가"), medical),
                new UwRule("R-CA-02", "암 병력 — 기타 담보", 31, RuleCondition.medical(List.of("C*"), 60, null, null),
                        List.of(DEATH, MEDICAL, SURGERY, HOSPITAL, CI), UwAction.REFER, RuleParams.reason("5년 내 암 병력 — 본사 심사"), medical),
                new UwRule("R-DM-01", "당뇨 치료 이력", 40,
                        RuleCondition.medical(diabetes, 60, List.of(MEDICATION, INPATIENT), null), List.of(),
                        UwAction.REFER, RuleParams.reason("당뇨 치료 이력 — 합병증·혈당 관리 상태 확인 필요"), medical),
                new UwRule("R-ADD-01", "추가검사 이력", 45, RuleCondition.answer("Q2"), List.of(),
                        UwAction.REFER, RuleParams.reason("1년 내 추가검사(재검사) — 검사 결과 확인 필요"), medical),
                new UwRule("R-HT-01", "고혈압 투약", 50,
                        RuleCondition.medical(List.of("I10*"), 60, List.of(MEDICATION, OUTPATIENT), null), List.of(SURGERY, CI),
                        UwAction.RATING, RuleParams.rating("고혈압 투약 — 수술비·뇌심장 진단 할증", 20), medical),
                new UwRule("R-GI-01", "위·십이지장 질환 치료 중", 60,
                        RuleCondition.medical(stomach, 12, null, false), List.of(MEDICAL, SURGERY, HOSPITAL), UwAction.EXCLUSION,
                        RuleParams.exclusion("1년 내 위·십이지장 질환 치료 중", "위·십이지장", stomach, 24), medical),
                new UwRule("R-GI-02", "위염 완치", 61,
                        RuleCondition.medical(List.of("K29*"), 60, null, true), List.of(),
                        UwAction.STANDARD, RuleParams.reason("위염 완치 — 표준체"), medical),
                new UwRule("R-FX-01", "골절 치료 중", 70,
                        RuleCondition.medical(fracture, 12, null, false), List.of(MEDICAL, SURGERY, HOSPITAL, ACCIDENT),
                        UwAction.EXCLUSION, RuleParams.exclusion("1년 내 골절 치료 중", "골절 부위", fracture, 12), medical),
                new UwRule("R-PRG-01", "임신 중", 80, RuleCondition.answer("Q5"), List.of(MEDICAL, SURGERY, HOSPITAL),
                        UwAction.EXCLUSION, RuleParams.exclusion("임신 중", "임신·출산", List.of("O*"), 12), medical),
                new UwRule("R-CLM-01", "잦은 보험금 청구", 90, RuleCondition.claimCount(12, 3), List.of(MEDICAL, HOSPITAL),
                        UwAction.REFER, RuleParams.reason("최근 1년 보험금 청구 3회 이상"), RuleScope.ALL),
                new UwRule("R-JOB-01", "고위험 직업", 95, RuleCondition.jobRisk(3), List.of(ACCIDENT, DEATH),
                        UwAction.RATING, RuleParams.rating("고위험 직업(3등급) — 상해·사망 할증", 50), RuleScope.ALL));
    }

    // ------------------------------------------------------------------ 시나리오 고객 (docs/scenarios.md)

    private Customer customer(String name, LocalDate birth, Gender g, String phone, ConsumerType type, String job, int risk,
                              InvestmentProfile profile) {
        Customer c = new Customer(name, birth, g, phone, type, job, risk);
        if (profile != null) {
            c.assessInvestmentProfile(profile);
        }
        return customerRepository.save(c);
    }

    private void loadScenarioCustomers(Product sample, Questionnaire questionnaire) {
        LocalDate today = LocalDate.now();
        ConsumerType gen = ConsumerType.GENERAL;
        Customer demo = customer("데모고객", LocalDate.of(1998, 1, 1), Gender.M, "010-1234-5678", gen, "회사원(사무직)", 1, null);
        customer("김정상", LocalDate.of(1995, 4, 12), Gender.F, "010-2222-1001", gen, "회사원(사무직)", 1, InvestmentProfile.NEUTRAL);
        Customer gastritis = customer("이위염", LocalDate.of(1990, 8, 3), Gender.M, "010-2222-1002", gen, "영업직", 1, null);
        Customer diabetes = customer("박당뇨", LocalDate.of(1975, 11, 20), Gender.M, "010-2222-1003", gen, "자영업", 1, null);
        customer("최고령", LocalDate.of(1955, 2, 14), Gender.F, "010-2222-1004", gen, "주부", 1, null);
        Customer fracture = customer("한골절", LocalDate.of(2000, 6, 30), Gender.M, "010-2222-1005", gen, "대학생", 1, null);
        customer("정전문", LocalDate.of(1980, 3, 2), Gender.M, "010-2222-1006", ConsumerType.PROFESSIONAL, "금융투자업 종사자", 1, null);
        customer("오안정", LocalDate.of(1985, 9, 9), Gender.F, "010-2222-1007", gen, "교사", 1, InvestmentProfile.STABLE);
        customer("강공격", LocalDate.of(1992, 12, 1), Gender.M, "010-2222-1008", gen, "IT 개발자", 1, InvestmentProfile.AGGRESSIVE);
        customer("윤위험", LocalDate.of(1988, 5, 5), Gender.M, "010-2222-1009", gen, "건설현장 근로자", 3, null);
        customer("서임신", LocalDate.of(1993, 7, 7), Gender.F, "010-2222-1010", gen, "디자이너", 1, null);
        Customer frequent = customer("문다청구", LocalDate.of(1982, 2, 28), Gender.F, "010-2222-1011", gen, "간호사", 2, null);
        customer("유어린", today.minusYears(10).withDayOfYear(1), Gender.M, "010-2222-1012", gen, "학생", 1, null);

        externalRecords.save(new ExternalMedicalRecord(gastritis, EXTERNAL, "K29.7", today.minusMonths(5), OUTPATIENT, false));
        externalRecords.save(new ExternalMedicalRecord(diabetes, EXTERNAL, "E11.9", today.minusYears(3), MEDICATION, false));
        externalRecords.save(new ExternalMedicalRecord(fracture, EXTERNAL, "S82.2", today.minusMonths(6), TreatmentType.SURGERY, false));

        // 문다청구: 2년 전 가입한 기존 실손 계약에 최근 1년 청구 4건 → 새 실손 청약 시 R-CLM-01 본사 이관
        Contract legacy = new Contract("LG-SCENARIO-01", frequent, sample, null, 20, today.minusYears(2), today.plusYears(18));
        Coverage medical = sample.getCoverages().get(0);
        legacy.addCoverage(new ContractCoverage(medical, medical.getDefaultAmount(), 12_000, 0, List.of()));
        contractRepository.save(legacy);
        String[][] claims = {{"J20.9", "급성 기관지염 외래"}, {"A09", "장염 외래"}, {"M51.2", "디스크 물리치료"}, {"J00", "감기 외래"}};
        for (int i = 0; i < claims.length; i++) {
            Claim c = new Claim("CL-SCENARIO-0" + (i + 1), legacy, ClaimType.OUTPATIENT, today.minusMonths(2 + i * 2),
                    claims[i][1], claims[i][0], 60_000);
            c.attachCheckNotes(List.of());
            c.startAiReview();
            c.completeAiReview("이관 데이터", null);
            c.approve(50_000, "기존 지급 건");
            claimRepository.save(c);
        }

        // 데모고객: 설계 → 청약(고지 없음) → 자동 승인 → 계약 성립 (청구 데모용)
        List<PremiumCalculator.CoverageAmount> amounts = new ArrayList<>();
        sample.getCoverages().forEach(cv -> amounts.add(new PremiumCalculator.CoverageAmount(cv.getId(), cv.getDefaultAmount())));
        Proposal proposal = proposalService.issue(demo.getId(), sample.getId(),
                new PremiumCalculator.Input(20, amounts, null, null, null, false));
        List<AnswerInput> allNo = questionnaire.getQuestions().stream()
                .map(q -> new AnswerInput(q.getId(), false, List.of())).toList();
        applicationService.submit(proposal.getId(), questionnaire.getId(), allNo,
                new Signature(demo.getName(), List.of(Agreement.values())));
    }
}
