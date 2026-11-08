package com.ax.core.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PaymentScheduleTest {

    @Test
    void 응당일이_없는_달은_말일이_납입기일() {
        LocalDate start = LocalDate.of(2026, 1, 31);
        assertThat(PaymentSchedule.dueDate(start, 1)).isEqualTo(start);
        assertThat(PaymentSchedule.dueDate(start, 2)).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(PaymentSchedule.dueDate(start, 3)).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void 납입유예기간은_납입기일_다음_달_말일까지() {
        assertThat(PaymentSchedule.graceEnd(LocalDate.of(2026, 1, 15))).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(PaymentSchedule.graceEnd(LocalDate.of(2026, 12, 31))).isEqualTo(LocalDate.of(2027, 1, 31));
    }

    @Test
    void 회차_수는_납입기간과_보험기간_중_짧은_쪽() {
        LocalDate start = LocalDate.of(2026, 3, 10);
        LocalDate end10y = start.plusYears(10).minusDays(1);
        assertThat(PaymentSchedule.totalInstallments(start, end10y, 20)).isEqualTo(120);   // 20년납이지만 10년 만기
        assertThat(PaymentSchedule.totalInstallments(start, end10y, 5)).isEqualTo(60);
        assertThat(PaymentSchedule.totalInstallments(start, end10y, 1)).isEqualTo(120);    // 갱신형은 보험기간 내내
    }

    @Test
    void 기준일까지_도래한_회차() {
        LocalDate start = LocalDate.of(2026, 3, 10);
        assertThat(PaymentSchedule.dueCount(start, start.minusDays(1), 120)).isZero();
        assertThat(PaymentSchedule.dueCount(start, start, 120)).isEqualTo(1);
        assertThat(PaymentSchedule.dueCount(start, LocalDate.of(2026, 4, 9), 120)).isEqualTo(1);
        assertThat(PaymentSchedule.dueCount(start, LocalDate.of(2026, 4, 10), 120)).isEqualTo(2);
        assertThat(PaymentSchedule.dueCount(start, LocalDate.of(2040, 1, 1), 120)).isEqualTo(120);
    }
}
