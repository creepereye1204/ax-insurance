package com.ax.core.demo;

import static com.ax.core.common.TreatmentType.INPATIENT;
import static com.ax.core.common.TreatmentType.MEDICATION;
import static com.ax.core.common.TreatmentType.OUTPATIENT;
import static com.ax.core.product.CoverageCategory.CANCER;
import static com.ax.core.product.CoverageCategory.MEDICAL;
import static com.ax.core.product.CoverageCategory.SURGERY;

import com.ax.core.application.ApplicationService;
import com.ax.core.application.ApplicationService.AnswerInput;
import com.ax.core.application.ApplicationService.CoverageInput;
import com.ax.core.common.TreatmentType;
import com.ax.core.customer.Customer;
import com.ax.core.customer.CustomerRepository;
import com.ax.core.customer.Gender;
import com.ax.core.disclosure.Questionnaire;
import com.ax.core.disclosure.QuestionnaireController;
import com.ax.core.disclosure.QuestionnaireRepository;
import com.ax.core.external.ExternalMedicalRecord;
import com.ax.core.external.ExternalMedicalRecordRepository;
import com.ax.core.kcd.Kcd;
import com.ax.core.kcd.KcdRepository;
import com.ax.core.product.Product;
import com.ax.core.product.ProductRepository;
import com.ax.core.uw.rule.RuleCondition;
import com.ax.core.uw.rule.RuleParams;
import com.ax.core.uw.rule.UwAction;
import com.ax.core.uw.rule.UwRule;
import com.ax.core.uw.rule.UwRuleSet;
import com.ax.core.uw.rule.UwRuleSetRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 데모 기준 데이터와 시나리오별 가상 고객. 심사 기준은 포트폴리오용 가상 기준이다.
 * 상품은 AI 서비스의 data/policies/샘플_실손의료비보험.txt 와 짝을 이룬다.
 */
@Component
@RequiredArgsConstructor
public class DemoDataLoader implements ApplicationRunner {

    private static final String PRODUCT_CODE = "SAMPLE-MED-01";
    private static final String EXTERNAL = "타사A생명(Mock)";

    private final KcdRepository kcdRepository;
    private final ProductRepository productRepository;
    private final QuestionnaireRepository questionnaireRepository;
    private final UwRuleSetRepository ruleSetRepository;
    private final CustomerRepository customerRepository;
    private final ExternalMedicalRecordRepository externalRecords;
    private final ApplicationService applicationService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (productRepository.existsByCode(PRODUCT_CODE)) {
            return;
        }
        loadKcd();
        Product product = productRepository.save(new Product(PRODUCT_CODE, "샘플 실손의료비보험", "샘플_실손의료비보험", 65)
                .addCoverage("MED-01", "실손의료비", MEDICAL, 9_000)
                .addCoverage("SUR-01", "질병수술비", SURGERY, 3_000)
                .addCoverage("CAN-01", "암진단비", CANCER, 6_000));
        Questionnaire questionnaire = questionnaireRepository.save(questionnaire());
        ruleSetRepository.save(ruleSet());
        loadCustomers(product, questionnaire);
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
                {"F32.9", "우울 에피소드"}, {"O80", "단일 자연분만"}, {"O82", "제왕절개에 의한 단일분만"},
                {"Z00.0", "일반 건강검진"},
        };
        for (String[] c : codes) {
            kcdRepository.save(new Kcd(c[0], c[1]));
        }
    }

    private Questionnaire questionnaire() {
        return new Questionnaire(QuestionnaireController.STANDARD_CODE, 1, "계약 전 알릴의무 (표준, 간소화)", LocalDate.of(2026, 1, 1))
                .addQuestion("Q1", "최근 3개월 이내에 의사로부터 진찰·검사를 통해 질병 확정진단, 질병 의심소견, 치료, 입원, 수술, 투약을 받은 사실이 있습니까?", 3, true)
                .addQuestion("Q2", "최근 1년 이내에 의사로부터 진찰·검사를 받고 그 결과 추가검사(재검사)를 받은 사실이 있습니까?", 12, true)
                .addQuestion("Q3", "최근 5년 이내에 입원, 수술, 계속하여 7일 이상 치료, 계속하여 30일 이상 투약을 받은 사실이 있습니까?", 60, true)
                .addQuestion("Q4", "최근 5년 이내에 암, 고혈압, 당뇨병, 뇌졸중, 심근경색 등 주요 질병으로 진단·치료·입원·수술·투약을 받은 사실이 있습니까?", 60, true)
                .addQuestion("Q5", "현재 임신 중입니까?", 0, false);
    }

    private UwRuleSet ruleSet() {
        List<String> diabetes = List.of("E10*", "E11*", "E12*", "E13*", "E14*");
        List<String> stomach = List.of("K25*", "K26*", "K29*");
        List<String> fracture = List.of("S42*", "S52*", "S82*");
        return new UwRuleSet(1, "데모 심사 기준 (가상)")
                .add(new UwRule("R-AGE-01", "가입나이 초과", 10, RuleCondition.ageOver(65), List.of(),
                        UwAction.DECLINE, RuleParams.reason("가입나이(만 65세) 초과")))
                .add(new UwRule("R-RCT-01", "최근 입원·수술 치료 중", 20,
                        RuleCondition.medical(List.of("*"), 3, List.of(INPATIENT, TreatmentType.SURGERY), false), List.of(),
                        UwAction.POSTPONE, RuleParams.reason("최근 3개월 내 입원·수술 후 치료 중 — 치료 종결 후 재청약")))
                .add(new UwRule("R-CA-01", "암 병력 — 암진단비", 30, RuleCondition.medical(List.of("C*"), 60, null, null),
                        List.of(CANCER), UwAction.DECLINE, RuleParams.reason("5년 내 암 병력 — 암진단비 인수 불가")))
                .add(new UwRule("R-CA-02", "암 병력 — 기타 담보", 31, RuleCondition.medical(List.of("C*"), 60, null, null),
                        List.of(MEDICAL, SURGERY), UwAction.REFER, RuleParams.reason("5년 내 암 병력 — 본사 심사")))
                .add(new UwRule("R-DM-01", "당뇨 치료 이력", 40,
                        RuleCondition.medical(diabetes, 60, List.of(MEDICATION, INPATIENT), null), List.of(),
                        UwAction.REFER, RuleParams.reason("당뇨 치료 이력 — 합병증·혈당 관리 상태 확인 필요")))
                .add(new UwRule("R-ADD-01", "추가검사 이력", 45, RuleCondition.answer("Q2"), List.of(),
                        UwAction.REFER, RuleParams.reason("1년 내 추가검사(재검사) — 검사 결과 확인 필요")))
                .add(new UwRule("R-HT-01", "고혈압 투약", 50,
                        RuleCondition.medical(List.of("I10*"), 60, List.of(MEDICATION, OUTPATIENT), null), List.of(SURGERY),
                        UwAction.RATING, RuleParams.rating("고혈압 투약 — 질병수술비 할증", 20)))
                .add(new UwRule("R-GI-01", "위·십이지장 질환 치료 중", 60,
                        RuleCondition.medical(stomach, 12, null, false), List.of(MEDICAL, SURGERY), UwAction.EXCLUSION,
                        RuleParams.exclusion("1년 내 위·십이지장 질환 치료 중", "위·십이지장", stomach, 24)))
                .add(new UwRule("R-GI-02", "위염 완치", 61,
                        RuleCondition.medical(List.of("K29*"), 60, null, true), List.of(),
                        UwAction.STANDARD, RuleParams.reason("위염 완치 — 표준체")))
                .add(new UwRule("R-FX-01", "골절 치료 중", 70,
                        RuleCondition.medical(fracture, 12, null, false), List.of(MEDICAL, SURGERY), UwAction.EXCLUSION,
                        RuleParams.exclusion("1년 내 골절 치료 중", "골절 부위", fracture, 12)))
                .add(new UwRule("R-PRG-01", "임신 중", 80, RuleCondition.answer("Q5"), List.of(MEDICAL),
                        UwAction.EXCLUSION, RuleParams.exclusion("임신 중", "임신·출산", List.of("O*"), 12)))
                .add(new UwRule("R-CLM-01", "잦은 보험금 청구", 90, RuleCondition.claimCount(12, 3), List.of(MEDICAL),
                        UwAction.REFER, RuleParams.reason("최근 1년 보험금 청구 3회 이상")));
    }

    private void loadCustomers(Product product, Questionnaire questionnaire) {
        Customer demo = customerRepository.save(new Customer("데모고객", LocalDate.of(1998, 1, 1), Gender.M, "010-1234-5678"));
        customerRepository.save(new Customer("김정상", LocalDate.of(1995, 4, 12), Gender.F, "010-2222-1001"));
        Customer gastritis = customerRepository.save(new Customer("이위염", LocalDate.of(1990, 8, 3), Gender.M, "010-2222-1002"));
        Customer diabetes = customerRepository.save(new Customer("박당뇨", LocalDate.of(1975, 11, 20), Gender.M, "010-2222-1003"));
        customerRepository.save(new Customer("최고령", LocalDate.of(1955, 2, 14), Gender.F, "010-2222-1004"));
        Customer fracture = customerRepository.save(new Customer("한골절", LocalDate.of(2000, 6, 30), Gender.M, "010-2222-1005"));

        LocalDate today = LocalDate.now();
        externalRecords.save(new ExternalMedicalRecord(gastritis, EXTERNAL, "K29.7", today.minusMonths(5), OUTPATIENT, false));
        externalRecords.save(new ExternalMedicalRecord(diabetes, EXTERNAL, "E11.9", today.minusYears(3), MEDICATION, false));
        externalRecords.save(new ExternalMedicalRecord(fracture, EXTERNAL, "S82.2", today.minusMonths(6), TreatmentType.SURGERY, false));

        // 데모고객은 고지 사항 없이 청약 → 자동 승인 → 계약 성립 (청구 데모용 계약)
        List<AnswerInput> allNo = questionnaire.getQuestions().stream()
                .map(q -> new AnswerInput(q.getId(), false, List.of())).toList();
        List<CoverageInput> coverages = product.getCoverages().stream()
                .map(c -> new CoverageInput(c.getId(), 30_000_000)).toList();
        applicationService.submit(demo.getId(), product.getId(), questionnaire.getId(), coverages, allNo);
    }
}
