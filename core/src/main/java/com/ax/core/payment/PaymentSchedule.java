package com.ax.core.payment;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/**
 * 납입 일정 계산 (순수 함수). 수납 배치의 SQL 도 같은 규칙을 쓴다.
 * <ul>
 *   <li>납입기일: 계약일의 매월 응당일. 응당일이 없는 달은 그 달 말일 (1/31 계약 → 2/28)</li>
 *   <li>납입유예기간: 납입기일이 속한 달의 다음 달 말일까지. 그때까지 내지 않으면 실효</li>
 * </ul>
 */
public final class PaymentSchedule {

    private PaymentSchedule() {
    }

    /** n회차(1부터) 납입기일 */
    public static LocalDate dueDate(LocalDate start, int installment) {
        return start.plusMonths(installment - 1L);
    }

    /** 납입유예기간 마지막 날 */
    public static LocalDate graceEnd(LocalDate dueDate) {
        return dueDate.plusMonths(1).with(TemporalAdjusters.lastDayOfMonth());
    }

    /** 갱신형(1년납)은 보험기간 끝까지 매월, 그 외는 납입기간 × 12. 보험기간을 넘지 않는다 */
    public static int totalInstallments(LocalDate start, LocalDate end, int paymentTermYears) {
        int coverageMonths = (int) ChronoUnit.MONTHS.between(start, end.plusDays(1));
        return paymentTermYears == 1 ? coverageMonths : Math.min(paymentTermYears * 12, coverageMonths);
    }

    /** asOf 까지 납입기일이 온 회차 수 */
    public static int dueCount(LocalDate start, LocalDate asOf, int total) {
        if (asOf.isBefore(start)) {
            return 0;
        }
        return (int) Math.min(total, ChronoUnit.MONTHS.between(start, asOf) + 1);
    }
}
