package com.ax.core.proposal;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * 저축성·변액 상품의 적립금(해지환급금) 예시표.
 * 실제 상품의 사업비 구조·공시이율과 다른 단순화된 가정이다.
 *
 * @param scenarios        시나리오 이름 (예: 공시이율 2.75%)
 * @param rows             경과년도별 납입보험료 누계와 시나리오별 적립금·환급률
 * @param monthlyAnnuities 연금개시 시점 적립금으로 20년 확정 지급 시 월 연금액 (연금 상품만)
 */
public record Illustration(List<String> scenarios, List<Row> rows, List<Long> monthlyAnnuities, String assumption) {

    public record Row(int year, int age, long totalPaid, List<Long> values, List<Integer> refundRates) {
    }

    record Scenario(String name, double annualRatePct) {
    }

    static final double LOADING = 0.07;          // 납입보험료 대비 사업비 7% (단순 가정)
    static final int ANNUITY_MONTHS = 20 * 12;   // 20년 확정 연금

    static Illustration fixed(int age, long contribution, int paymentTerm, Integer annuityStartAge) {
        return compute(age, contribution, paymentTerm, annuityStartAge,
                List.of(new Scenario("최저보증 1.0%", 1.0), new Scenario("공시이율 2.75%", 2.75)),
                "사업비 7%, 공시이율이 변하지 않는다고 가정");
    }

    static Illustration variable(int age, long contribution, int paymentTerm, Integer annuityStartAge, double fundFeePct) {
        List<Scenario> scenarios = new ArrayList<>();
        for (double r : new double[] {0.0, 3.75, 7.5}) {
            scenarios.add(new Scenario("투자수익률 %.2f%%".formatted(r), r - fundFeePct));
        }
        return compute(age, contribution, paymentTerm, annuityStartAge, scenarios,
                "사업비 7%%, 펀드 운용보수 연 %.2f%% 차감. 원금 손실 가능".formatted(fundFeePct));
    }

    private static Illustration compute(int age, long contribution, int paymentTerm, Integer annuityStartAge,
                                        List<Scenario> scenarios, String assumption) {
        int horizon = annuityStartAge != null ? annuityStartAge - age : Math.max(paymentTerm, 10);
        TreeSet<Integer> years = new TreeSet<>(List.of(1, 3, 5, 10, 15, 20, 30, paymentTerm, horizon));
        years.removeIf(y -> y > horizon);

        double[] values = new double[scenarios.size()];
        List<Row> rows = new ArrayList<>();
        long paid = 0;
        for (int month = 1; month <= horizon * 12; month++) {
            boolean paying = month <= paymentTerm * 12;
            if (paying) {
                paid += contribution;
            }
            for (int s = 0; s < scenarios.size(); s++) {
                double monthlyRate = scenarios.get(s).annualRatePct() / 100 / 12;
                values[s] = values[s] * (1 + monthlyRate) + (paying ? contribution * (1 - LOADING) : 0);
            }
            if (month % 12 == 0 && years.contains(month / 12)) {
                List<Long> v = new ArrayList<>();
                List<Integer> rates = new ArrayList<>();
                for (double value : values) {
                    long rounded = Math.round(value / 1000) * 1000;
                    v.add(rounded);
                    rates.add(paid == 0 ? 0 : (int) Math.round(rounded * 100.0 / paid));
                }
                rows.add(new Row(month / 12, age + month / 12, paid, v, rates));
            }
        }
        List<Long> annuities = null;
        if (annuityStartAge != null) {
            annuities = new ArrayList<>();
            for (double value : values) {
                annuities.add(Math.round(value / ANNUITY_MONTHS / 100) * 100);
            }
        }
        return new Illustration(scenarios.stream().map(Scenario::name).toList(), rows, annuities, assumption);
    }
}
