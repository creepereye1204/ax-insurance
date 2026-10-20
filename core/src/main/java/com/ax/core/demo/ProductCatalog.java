package com.ax.core.demo;

import static com.ax.core.product.CoverageCategory.ACCIDENT;
import static com.ax.core.product.CoverageCategory.ANNUITY;
import static com.ax.core.product.CoverageCategory.CANCER;
import static com.ax.core.product.CoverageCategory.CI;
import static com.ax.core.product.CoverageCategory.DEATH;
import static com.ax.core.product.CoverageCategory.DENTAL;
import static com.ax.core.product.CoverageCategory.HOSPITAL;
import static com.ax.core.product.CoverageCategory.MEDICAL;
import static com.ax.core.product.CoverageCategory.SAVINGS;
import static com.ax.core.product.CoverageCategory.SURGERY;

import com.ax.core.product.CoverageCategory;
import com.ax.core.product.Fund;
import com.ax.core.product.Product;
import com.ax.core.product.ProductType;
import com.ax.core.product.SaleStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 데모용 상품 카탈로그 (가상). 유형별 템플릿에 변형(간편심사형·갱신형 등)과 요율 편차를 줘서 200개를 만든다.
 * 같은 시드로 항상 같은 상품이 나온다.
 */
final class ProductCatalog {

    static final String SAMPLE_CODE = "SAMPLE-MED-01";
    private static final long MAN = 10_000;          // 1만원
    private static final long CHEON = 10_000_000;    // 1천만원
    private static final long EOK = 100_000_000;     // 1억원

    record Cov(String code, String name, CoverageCategory category, boolean required, long min, long max, long def,
               long step, long rate, int growth) {
    }

    record Tpl(ProductType type, int count, String shortName, int minAge, int maxAge, List<Integer> terms, String period,
               List<Cov> coverages, Long minContribution, Long maxContribution, List<Integer> annuityAges) {
    }

    static final List<Tpl> TEMPLATES = List.of(
            new Tpl(ProductType.WHOLE_LIFE, 22, "종신보험", 15, 65, List.of(10, 20, 30), "종신", List.of(
                    new Cov("DTH", "일반사망", DEATH, true, CHEON, 5 * EOK, EOK, CHEON, 14_000, 45),
                    new Cov("ADT", "재해사망", ACCIDENT, false, CHEON, 3 * EOK, EOK, CHEON, 900, 10),
                    new Cov("CI", "뇌·심장 진단", CI, false, CHEON, EOK, 3 * CHEON, CHEON, 7_000, 55)), null, null, null),
            new Tpl(ProductType.TERM_LIFE, 15, "정기보험", 20, 60, List.of(10, 20), "20년만기", List.of(
                    new Cov("DTH", "사망", DEATH, true, CHEON, 10 * EOK, 3 * EOK, CHEON, 2_500, 70)), null, null, null),
            new Tpl(ProductType.HEALTH, 30, "건강보험", 15, 70, List.of(10, 20, 30), "100세만기", List.of(
                    new Cov("SUR", "질병수술비", SURGERY, true, 100 * MAN, 3 * CHEON, CHEON, 100 * MAN, 3_000, 40),
                    new Cov("HOS", "질병입원일당", HOSPITAL, false, MAN, 10 * MAN, 3 * MAN, MAN, 2_000_000, 35),
                    new Cov("CI", "뇌·심장 진단", CI, false, CHEON, 5 * CHEON, 2 * CHEON, CHEON, 7_000, 55),
                    new Cov("ACC", "상해후유장해", ACCIDENT, false, CHEON, EOK, 5 * CHEON, CHEON, 600, 5)), null, null, null),
            new Tpl(ProductType.CANCER, 25, "암보험", 15, 70, List.of(10, 20, 30), "100세만기", List.of(
                    new Cov("CAN", "암진단", CANCER, true, CHEON, EOK, 3 * CHEON, CHEON, 6_000, 50),
                    new Cov("CSU", "암수술", SURGERY, false, 100 * MAN, CHEON, 500 * MAN, 100 * MAN, 4_000, 45),
                    new Cov("CHO", "암입원일당", HOSPITAL, false, MAN, 10 * MAN, 3 * MAN, MAN, 900_000, 40)), null, null, null),
            new Tpl(ProductType.MEDICAL, 10, "실손의료보험", 0, 70, List.of(1), "1년갱신(최대 100세)", List.of(
                    new Cov("MED", "실손의료비", MEDICAL, true, 5 * CHEON, 5 * CHEON, 5 * CHEON, CHEON, 1_800, 30)), null, null, null),
            new Tpl(ProductType.CHILD, 18, "어린이보험", 0, 30, List.of(10, 20, 30), "100세만기", List.of(
                    new Cov("ACC", "상해후유장해", ACCIDENT, true, CHEON, EOK, 5 * CHEON, CHEON, 500, 3),
                    new Cov("HOS", "질병입원일당", HOSPITAL, false, MAN, 5 * MAN, 2 * MAN, MAN, 1_200_000, 20),
                    new Cov("SUR", "질병수술비", SURGERY, false, 100 * MAN, CHEON, 500 * MAN, 100 * MAN, 2_000, 30),
                    new Cov("CAN", "암진단", CANCER, false, CHEON, 5 * CHEON, 3 * CHEON, CHEON, 2_500, 50)), null, null, null),
            new Tpl(ProductType.DRIVER, 15, "운전자보험", 19, 70, List.of(10, 20), "20년만기", List.of(
                    new Cov("TDT", "교통상해사망", ACCIDENT, true, CHEON, 2 * EOK, EOK, CHEON, 400, 5),
                    new Cov("SUP", "교통사고처리지원", ACCIDENT, false, CHEON, 2 * EOK, EOK, CHEON, 300, 2)), null, null, null),
            new Tpl(ProductType.DENTAL, 10, "치아보험", 15, 65, List.of(10, 20), "20년만기", List.of(
                    new Cov("DEN", "치아치료비", DENTAL, true, 50 * MAN, 300 * MAN, 100 * MAN, 50 * MAN, 120_000, 15)), null, null, null),
            new Tpl(ProductType.ANNUITY, 18, "연금보험", 19, 65, List.of(5, 10, 20), "종신(연금)", List.of(
                    new Cov("ANN", "연금적립", ANNUITY, true, 0, 0, 0, 1, 0, 0)), 10 * MAN, 300 * MAN, List.of(55, 60, 65, 70)),
            new Tpl(ProductType.VARIABLE_ANNUITY, 14, "변액연금보험", 19, 65, List.of(10, 20), "종신(연금)", List.of(
                    new Cov("ANN", "연금적립(변액)", ANNUITY, true, 0, 0, 0, 1, 0, 0)), 10 * MAN, 300 * MAN, List.of(55, 60, 65, 70)),
            new Tpl(ProductType.VARIABLE_UNIVERSAL, 13, "변액유니버설보험", 15, 65, List.of(10, 20), "종신", List.of(
                    new Cov("DTH", "일반사망", DEATH, true, CHEON, 3 * EOK, 5 * CHEON, CHEON, 12_000, 45),
                    new Cov("SAV", "투자적립", SAVINGS, true, 0, 0, 0, 1, 0, 0)), 10 * MAN, 200 * MAN, null),
            new Tpl(ProductType.SAVINGS, 10, "저축보험", 15, 70, List.of(5, 10), "10년만기", List.of(
                    new Cov("SAV", "저축적립", SAVINGS, true, 0, 0, 0, 1, 0, 0)), 10 * MAN, 500 * MAN, null));

    private static final String[] BRANDS = {"Ax", "든든", "튼튼", "행복한", "미래", "케어", "플러스", "바로", "참좋은", "올바른", "누리", "새봄"};

    static List<Fund> funds() {
        return List.of(
                new Fund("F-KEQ", "국내주식형", "주식", 2, 90),
                new Fund("F-GEQ", "글로벌주식형", "주식", 2, 110),
                new Fund("F-USIX", "미국주식인덱스형", "주식", 2, 60),
                new Fund("F-EMEQ", "신흥국주식형", "주식", 1, 130),
                new Fund("F-ESG", "ESG주식형", "주식", 2, 100),
                new Fund("F-MIX", "혼합형(주식 50%)", "혼합", 3, 70),
                new Fund("F-BOND", "채권형", "채권", 5, 40),
                new Fund("F-MMF", "머니마켓형", "단기금융", 6, 20));
    }

    /** AI 약관 Q&A 와 연결된 샘플 상품 (data/policies/샘플_실손의료비보험.txt) */
    static Product sample() {
        return Product.builder().code(SAMPLE_CODE).name("샘플 실손의료비보험").type(ProductType.MEDICAL)
                .policyDocument("샘플_실손의료비보험").minEntryAge(0).maxEntryAge(70).paymentTerms(List.of(10, 20))
                .insurancePeriod("20년만기").renewable(false).simplifiedUw(false).saleStatus(SaleStatus.ON_SALE)
                .launchedOn(LocalDate.of(2026, 1, 1)).build()
                .addCoverage("SAMPLE-MED", "실손의료비", MEDICAL, true, 5 * CHEON, 5 * CHEON, 5 * CHEON, CHEON, 1_800, 30)
                .addCoverage("SAMPLE-SUR", "질병수술비", SURGERY, false, 100 * MAN, 3 * CHEON, CHEON, 100 * MAN, 3_000, 40)
                .addCoverage("SAMPLE-CAN", "암진단비", CANCER, false, CHEON, 5 * CHEON, 3 * CHEON, CHEON, 6_000, 50);
    }

    static List<Product> generate() {
        Random rnd = new Random(20261020L);
        List<String> fundCodes = funds().stream().map(Fund::getCode).toList();
        List<Product> products = new ArrayList<>();
        int typeIndex = 0;
        for (Tpl t : TEMPLATES) {
            typeIndex++;
            for (int i = 1; i <= t.count(); i++) {
                boolean simplified = !t.type().isSavings() && t.type() != ProductType.MEDICAL && i % 4 == 0;
                boolean renewable = !t.type().isSavings() && !simplified && i % 5 == 0;
                boolean noRefund = !t.type().isSavings() && !simplified && !renewable && i % 3 == 0;
                double rateFactor = (0.85 + rnd.nextDouble() * 0.3) * (simplified ? 1.3 : 1) * (renewable ? 0.7 : 1)
                        * (noRefund ? 0.8 : 1);
                int maxAge = Math.min(simplified ? t.maxAge() + 10 : t.maxAge() - rnd.nextInt(6), 80);

                String variant = simplified ? "(간편심사형)" : renewable ? "(갱신형)" : noRefund ? "(무해지환급형)" : "";
                String brand = BRANDS[(i + typeIndex) % BRANDS.length];
                String name = "%s %s%s 무배당 %d".formatted(brand, t.shortName(), variant, 2019 + (i % 8));

                List<String> funds = null;
                if (t.type().isVariable()) {
                    List<String> pool = new ArrayList<>(fundCodes);
                    java.util.Collections.shuffle(pool, rnd);
                    funds = new ArrayList<>(pool.subList(0, 4 + rnd.nextInt(4)));
                    for (String must : List.of("F-BOND", "F-MMF")) {
                        if (!funds.contains(must)) {
                            funds.add(must);
                        }
                    }
                }
                Product p = Product.builder()
                        .code("%s-%03d".formatted(t.type().name().substring(0, 3), i + typeIndex * 100))
                        .name(name).type(t.type())
                        .minEntryAge(t.minAge()).maxEntryAge(maxAge).paymentTerms(t.terms())
                        .insurancePeriod(renewable ? "20년갱신(최대 100세)" : t.period())
                        .renewable(renewable || t.type() == ProductType.MEDICAL).simplifiedUw(simplified)
                        .saleStatus(rnd.nextDouble() < 0.15 ? SaleStatus.DISCONTINUED : SaleStatus.ON_SALE)
                        .launchedOn(LocalDate.of(2019, 1, 1).plusDays(rnd.nextInt(2800)))
                        .minMonthlyContribution(t.minContribution()).maxMonthlyContribution(t.maxContribution())
                        .annuityStartAges(t.annuityAges()).fundCodes(funds)
                        .build();
                for (Cov c : t.coverages()) {
                    p.addCoverage(p.getCode() + "-" + c.code(), c.name(), c.category(), c.required(), c.min(), c.max(),
                            c.def(), c.step(), Math.round(c.rate() * rateFactor), c.growth());
                }
                products.add(p);
            }
        }
        return products;
    }

    private ProductCatalog() {
    }
}
