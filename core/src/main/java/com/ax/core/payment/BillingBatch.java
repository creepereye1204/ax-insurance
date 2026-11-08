package com.ax.core.payment;

import com.ax.core.common.BusinessException;
import com.ax.core.customer.CustomerRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일 수납 배치 (기간계 배치). 계약 6만 건을 한 건씩 읽지 않고 집합 단위 SQL 3개로 처리한다.
 * <ol>
 *   <li>청구: 유지 계약 중 납입기일이 온 회차를 미납으로 생성 (마지막 회차 다음부터, 중복은 unique 제약으로 무시)</li>
 *   <li>자동이체: 자동이체 계약의 미납 회차를 납입기일자로 납입 처리</li>
 *   <li>실효: 납입유예기간(납입기일 다음 달 말일)이 지난 미납이 있으면 실효</li>
 * </ol>
 * 기간계가 2대라 둘 다 스케줄이 돈다 → PostgreSQL advisory lock 으로 한 대만 실행하고, 같은 기준일은 다시 돌지 않는다.
 * 반복 실행해도 결과가 같다 (멱등).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingBatch {

    public static final String JOB = "BILLING";
    private static final long LOCK_KEY = 7_340_001L;

    private final JdbcTemplate jdbc;
    private final BatchRunRepository runRepository;
    private final CustomerRepository customerRepository;

    public record Result(LocalDate asOf, int billed, int autoPaid, int lapsed, long elapsedMs) {
        String summary() {
            return "청구 %d건 · 자동이체 %d건 · 실효 %d건".formatted(billed, autoPaid, lapsed);
        }
    }

    @Scheduled(cron = "${ax.billing.cron:0 10 1 * * *}")
    public void nightly() {
        run(LocalDate.now(), "SCHEDULED").ifPresent(r -> log.info("수납 배치 {}: {} ({}ms)", r.asOf(), r.summary(), r.elapsedMs()));
    }

    /** 기준일은 오늘 이전만 (미래 기준일로 돌리면 아직 오지 않은 회차가 청구된다) */
    @Transactional
    public Optional<Result> run(LocalDate asOf, String trigger) {
        if (asOf.isAfter(LocalDate.now())) {
            throw new BusinessException("수납 배치 기준일은 오늘 이후일 수 없습니다.");
        }
        if (!Boolean.TRUE.equals(jdbc.queryForObject("select pg_try_advisory_xact_lock(?)", Boolean.class, LOCK_KEY))) {
            return Optional.empty();   // 다른 기간계가 실행 중
        }
        if ("SCHEDULED".equals(trigger) && Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from batch_run where job = ? and as_of = ? and triggered_by = 'SCHEDULED')",
                Boolean.class, JOB, asOf))) {
            return Optional.empty();   // 이미 이 기준일로 실행됨
        }
        LocalDateTime started = LocalDateTime.now();
        long t0 = System.currentTimeMillis();
        int billed = jdbc.update(BILL, asOf);
        int autoPaid = jdbc.update(AUTO_TRANSFER, asOf);
        int lapsed = jdbc.update(LAPSE, asOf, asOf);
        if (lapsed > 0) {
            customerRepository.syncStatus();
        }
        Result r = new Result(asOf, billed, autoPaid, lapsed, System.currentTimeMillis() - t0);
        runRepository.save(new BatchRun(JOB, asOf, trigger, started, r.elapsedMs(), r.summary()));
        return Optional.of(r);
    }

    /** 회차 수: 갱신형(1년납)은 보험기간 개월 수, 그 외 min(납입기간×12, 보험기간 개월 수). PaymentSchedule 과 같은 규칙 */
    public static final String MONTHS = """
            (extract(year from age(%1$s, %2$s)) * 12 + extract(month from age(%1$s, %2$s)))::int""";
    public static final String TOTAL = "case when k.payment_term = 1 then m.cov else least(k.payment_term * 12, m.cov) end";

    private static final String BILL = """
            insert into premium_payment (contract_id, installment_no, due_date, amount, status, created_at, updated_at)
            select k.id, n, (k.start_date + (n - 1) * interval '1 month')::date, k.monthly_premium, 'UNPAID', now(), now()
            from contract k
            cross join (select ?::date as_of) q
            cross join lateral (select %s cov, %s + 1 due) m
            cross join lateral (select coalesce(max(p.installment_no), 0) last from premium_payment p where p.contract_id = k.id) l
            cross join lateral generate_series(l.last + 1, least(%s, m.due)) n
            where k.status = 'ACTIVE' and k.start_date <= q.as_of
            on conflict (contract_id, installment_no) do nothing
            """.formatted(MONTHS.formatted("k.end_date + 1", "k.start_date"), MONTHS.formatted("q.as_of", "k.start_date"), TOTAL);

    private static final String AUTO_TRANSFER = """
            update premium_payment p set status = 'PAID', paid_on = p.due_date, method = 'AUTO_TRANSFER', updated_at = now()
            from contract k
            where p.contract_id = k.id and k.status = 'ACTIVE' and coalesce(k.payment_method, 'AUTO_TRANSFER') = 'AUTO_TRANSFER'
              and p.status = 'UNPAID' and p.due_date <= ?
            """;

    private static final String LAPSE = """
            update contract k set status = 'LAPSED', status_changed_on = ?, updated_at = now()
            where k.status = 'ACTIVE' and exists (
                select 1 from premium_payment p where p.contract_id = k.id and p.status = 'UNPAID'
                  and (date_trunc('month', p.due_date) + interval '2 month' - interval '1 day')::date < ?)
            """;
}
