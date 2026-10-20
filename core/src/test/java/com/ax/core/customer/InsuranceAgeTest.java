package com.ax.core.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.ax.core.product.PremiumConversion;
import com.ax.core.product.ProductType;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class InsuranceAgeTest {

    static Customer born(LocalDate birth) {
        return new Customer("테스트", birth, Gender.F, null, ConsumerType.GENERAL, "회사원", 1);
    }

    @Test
    void 만나이_끝수가_6개월_미만이면_버리고_이상이면_올린다() {
        Customer c = born(LocalDate.of(1990, 3, 15));
        assertThat(c.insuranceAgeAt(LocalDate.of(2026, 9, 14))).isEqualTo(36);  // 만 36세 5개월
        assertThat(c.insuranceAgeAt(LocalDate.of(2026, 9, 15))).isEqualTo(37);  // 만 36세 6개월 → 상령
        assertThat(c.ageAt(LocalDate.of(2026, 9, 15))).isEqualTo(36);
    }

    @Test
    void 상령일은_생일에서_6개월_뒤이며_오늘_이후의_가장_가까운_날() {
        Customer c = born(LocalDate.of(1990, 3, 15));
        assertThat(c.nextAgeUpDate(LocalDate.of(2026, 9, 14))).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(c.nextAgeUpDate(LocalDate.of(2026, 9, 15))).isEqualTo(LocalDate.of(2027, 9, 15));
        assertThat(c.nextAgeUpDate(LocalDate.of(2026, 1, 1))).isEqualTo(LocalDate.of(2026, 9, 15));
    }

    @Test
    void 월말_생일은_2월이_아니라_3월_1일에_상령한다() {
        Customer c = born(LocalDate.of(1985, 8, 31));
        assertThat(c.nextAgeUpDate(LocalDate.of(2026, 1, 10))).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    void 상령일_전날과_당일의_보험나이는_1세_차이() {
        // 1년 동안 매일, 그리고 월말·윤년 생일까지 상령일과 보험나이 계산이 항상 일치하는지
        for (LocalDate birth : new LocalDate[] {LocalDate.of(1985, 8, 31), LocalDate.of(1992, 2, 29), LocalDate.of(2000, 1, 1)}) {
            Customer c = born(birth);
            for (LocalDate d = LocalDate.of(2026, 1, 1); d.getYear() == 2026; d = d.plusDays(1)) {
                LocalDate ageUp = c.nextAgeUpDate(d);
                assertThat(ageUp).isAfter(d);
                assertThat(c.insuranceAgeAt(ageUp) - c.insuranceAgeAt(ageUp.minusDays(1))).as("%s %s", birth, d).isEqualTo(1);
                assertThat(c.insuranceAgeAt(ageUp.minusDays(1))).isEqualTo(c.insuranceAgeAt(d));
            }
        }
    }

    @Test
    void 환산율은_보장성_장기납이_높고_저축성_변액_갱신형은_낮다() {
        assertThat(PremiumConversion.ratePct(ProductType.WHOLE_LIFE, 20, false)).isEqualTo(100);
        assertThat(PremiumConversion.ratePct(ProductType.HEALTH, 10, false)).isEqualTo(70);
        assertThat(PremiumConversion.ratePct(ProductType.HEALTH, 20, true)).isEqualTo(50);
        assertThat(PremiumConversion.ratePct(ProductType.SAVINGS, 10, false)).isEqualTo(30);
        assertThat(PremiumConversion.ratePct(ProductType.VARIABLE_ANNUITY, 20, false)).isEqualTo(50);
        assertThat(PremiumConversion.converted(123_450, 70)).isEqualTo(86_420);
    }
}
