package com.ax.core.proposal;

import static org.assertj.core.api.Assertions.assertThat;

import com.ax.core.customer.InvestmentProfile;
import org.junit.jupiter.api.Test;

class IllustrationTest {

    @Test
    void 공시이율_시나리오는_최저보증보다_적립금이_크다() {
        Illustration il = Illustration.fixed(40, 300_000, 10, null);
        Illustration.Row last = il.rows().get(il.rows().size() - 1);
        assertThat(last.year()).isEqualTo(10);
        assertThat(last.totalPaid()).isEqualTo(300_000L * 12 * 10);
        assertThat(last.values().get(1)).isGreaterThan(last.values().get(0));
    }

    @Test
    void 초기에는_사업비_때문에_환급률이_100퍼센트_미만() {
        Illustration il = Illustration.fixed(40, 300_000, 10, null);
        assertThat(il.rows().get(0).refundRates()).allMatch(r -> r < 100);
    }

    @Test
    void 연금은_개시나이까지_적립하고_월연금액을_계산한다() {
        Illustration il = Illustration.variable(35, 500_000, 20, 65, 0.8);
        assertThat(il.rows().get(il.rows().size() - 1).age()).isEqualTo(65);
        assertThat(il.monthlyAnnuities()).hasSize(3);
        assertThat(il.monthlyAnnuities().get(2)).isGreaterThan(il.monthlyAnnuities().get(0));
    }

    @Test
    void 투자성향보다_위험한_펀드는_부적합() {
        assertThat(InvestmentProfile.STABLE.allows(2)).isFalse();   // 안정형 + 주식형(2등급)
        assertThat(InvestmentProfile.STABLE.allows(5)).isTrue();    // 안정형 + 채권형(5등급)
        assertThat(InvestmentProfile.AGGRESSIVE.allows(1)).isTrue();
        assertThat(InvestmentProfile.fromScore(25)).isEqualTo(InvestmentProfile.AGGRESSIVE);
        assertThat(InvestmentProfile.fromScore(5)).isEqualTo(InvestmentProfile.STABLE);
    }
}
