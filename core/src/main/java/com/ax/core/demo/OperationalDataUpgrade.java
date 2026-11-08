package com.ax.core.demo;

import com.ax.core.agent.Agent;
import com.ax.core.agent.AgentChannel;
import com.ax.core.agent.AgentRepository;
import com.ax.core.common.Region;
import com.ax.core.payment.BillingBatch;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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
 * 정보계 적재에 필요한 운영 데이터 보강 (설계사 · 지역 · 수납 이력). 대량 적재(@Order 2) 다음에 실행한다.
 * 모든 단계가 "비어 있는 것만 채운다" → 운영 DB 에서 기동할 때마다 실행해도 안전하다 (troubleshooting #13 과 같은 방식).
 * 값은 hashtext 로 정해 매번 같은 결과가 나온다.
 */
@Slf4j
@Order(3)
@Component
@RequiredArgsConstructor
public class OperationalDataUpgrade implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final AgentRepository agentRepository;
    private final BillingBatch billingBatch;

    @Value("${ax.demo.enabled:true}")
    private boolean enabled;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        long t0 = System.currentTimeMillis();
        int agents = agentRepository.count() == 0 ? agentRepository.saveAll(agents()).size() : 0;
        int regions = jdbc.update("update customer set region = " + Region.sqlCase("abs(hashtext('rg' || id::text)) % 1000")
                + " where region is null");
        int assigned = assignAgents();
        int history = paymentHistory();
        billingBatch.run(LocalDate.now(), "STARTUP").ifPresent(r -> log.info("수납 배치(기동): {}", r));
        if (agents + regions + assigned + history > 0) {
            log.info("운영 데이터 보강: 설계사 {}명, 고객 지역 {}명, 담당 설계사 배정 {}건, 수납 이력 {}건 ({}ms)",
                    agents, regions, assigned, history, System.currentTimeMillis() - t0);
        }
    }

    /** 지역별 지점(인구 비중에 비례) × 지점당 전속 설계사 3~6명 + GA 대리점 + TM 센터 */
    static List<Agent> agents() {
        Random rnd = new Random(2026);
        String[] sur = {"김", "이", "박", "최", "정", "강", "조", "윤", "장", "임", "한", "오", "서", "신", "권", "황"};
        String[] giv = {"민", "서", "준", "지", "현", "우", "예", "도", "하", "윤", "수", "연", "은", "영", "진", "성"};
        String[] suffix = {"중앙", "동부", "서부", "남부", "북부", "제일", "신도시", "역전"};
        String[] ga = {"한빛에셋", "든든금융서비스", "바른재무설계", "미래에셋파트너스(가상)"};
        List<Agent> list = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (Region r : Region.values()) {
            int branches = Math.max(1, Math.round(r.getWeight() / 30f));
            for (int b = 0; b < branches; b++) {
                String branch = r.getLabel() + suffix[b % suffix.length] + "지점";
                for (int i = 0, n = 3 + rnd.nextInt(4); i < n; i++) {
                    list.add(agent(list.size(), sur, giv, rnd, AgentChannel.FC, branch, r, today));
                }
            }
            if (r.getWeight() >= 40) {
                String agency = ga[rnd.nextInt(ga.length)] + " " + r.getLabel() + "본부";
                for (int i = 0; i < 3; i++) {
                    list.add(agent(list.size(), sur, giv, rnd, AgentChannel.GA, agency, r, today));
                }
            }
        }
        for (Region r : List.of(Region.SEOUL, Region.DAEJEON)) {
            for (int i = 0; i < 4; i++) {
                list.add(agent(list.size(), sur, giv, rnd, AgentChannel.TM, r.getLabel() + " TM센터", r, today));
            }
        }
        return list;
    }

    private static Agent agent(int seq, String[] sur, String[] giv, Random rnd, AgentChannel channel, String branch,
                               Region region, LocalDate today) {
        String name = sur[rnd.nextInt(sur.length)] + giv[rnd.nextInt(giv.length)] + giv[rnd.nextInt(giv.length)];
        return new Agent(channel.name() + "%05d".formatted(seq + 1), name, channel, branch, region,
                today.minusDays(200 + rnd.nextInt(4000)));
    }

    /**
     * 담당 설계사 배정: 설계서는 고객 거주 지역의 설계사, 청약은 설계서를, 계약은 청약을 따른다.
     * 청약 없이 이관된 계약(LG)은 고객 지역의 설계사.
     */
    private int assignAgents() {
        String pick = """
                (select a.ids[1 + abs(hashtext('ag' || %s)) %% array_length(a.ids, 1)]
                 from (select array_agg(id order by id) ids from agent where region = c.region) a)""";
        int n = jdbc.update("""
                update proposal x set agent_id = %s from customer c
                where x.customer_id = c.id and x.agent_id is null and c.region is not null
                """.formatted(pick.formatted("x.id::text")));
        n += jdbc.update("""
                update application x set agent_id = p.agent_id from proposal p
                where x.proposal_id = p.id and x.agent_id is null and p.agent_id is not null
                """);
        n += jdbc.update("""
                update contract x set agent_id = a.agent_id from application a
                where x.application_id = a.id and x.agent_id is null and a.agent_id is not null
                """);
        n += jdbc.update("""
                update contract x set agent_id = %s from customer c
                where x.customer_id = c.id and x.agent_id is null and x.application_id is null and c.region is not null
                """.formatted(pick.formatted("x.id::text")));
        return n;
    }

    /**
     * 이관 계약(LG)의 과거 수납 이력. 대부분 자동이체로 매월 납입, 약 25%는 직접납입으로 바꾼 뒤 어느 회차부터 미납
     * → 유예기간이 지났으면 실효. 미납 시작 회차는 초기에 몰리게(세제곱 분포) 해 13회차 유지율이 업계 수준(80%대)이 되게 했다.
     * 해지 계약은 어느 회차까지 내고 해지.
     */
    private int paymentHistory() {
        jdbc.update("""
                update contract k set payment_method =
                    case when k.status = 'ACTIVE' and abs(hashtext(k.contract_no || ':stop')) % 100 < 25 then 'DIRECT' else 'AUTO_TRANSFER' end
                where k.contract_no like 'LG%' and k.payment_method is null
                """);
        String months = BillingBatch.MONTHS;
        int rows = jdbc.update("""
                insert into premium_payment (contract_id, installment_no, due_date, amount, status, paid_on, method, created_at, updated_at)
                select k.id, n, d.due, k.monthly_premium,
                       case when n < s.stop then 'PAID' else 'UNPAID' end,
                       case when n < s.stop then least(d.due + s.late, current_date) end,
                       case when n < s.stop then k.payment_method end,
                       now(), now()
                from contract k
                cross join lateral (select %s cov, %s + 1 due) m
                cross join lateral (select
                    case when k.payment_method = 'DIRECT'
                         then 2 + floor(power((abs(hashtext(k.contract_no || ':at')) %% 1000) / 1000.0, 3) * greatest(m.due - 1, 1))::int
                         else 1000000 end stop,
                    case when k.payment_method = 'DIRECT' then abs(hashtext(k.contract_no || ':late')) %% 15 else 0 end late,
                    case when k.status = 'TERMINATED' then 1 + abs(hashtext(k.contract_no || ':end')) %% greatest(m.due, 1)
                         else 1000000 end paid_until) s
                cross join lateral generate_series(1, least(%s, m.due, s.paid_until, s.stop + 1)) n
                cross join lateral (select (k.start_date + (n - 1) * interval '1 month')::date due) d
                where k.contract_no like 'LG%%' and k.start_date <= current_date
                  and not exists (select 1 from premium_payment p where p.contract_id = k.id)
                """.formatted(months.formatted("k.end_date + 1", "k.start_date"), months.formatted("current_date", "k.start_date"),
                BillingBatch.TOTAL));
        // 컬럼 추가 전에 해지·철회된 계약의 상태 변경일
        jdbc.update("update contract set status_changed_on = updated_at::date where status <> 'ACTIVE' and status_changed_on is null"
                + " and contract_no not like 'LG%'");
        if (rows == 0) {
            return 0;
        }
        // 해지일 = 마지막 납입 다음 회차 납입기일
        jdbc.update("""
                update contract k set status_changed_on = (k.start_date + x.last * interval '1 month')::date
                from (select contract_id, max(installment_no) last from premium_payment group by contract_id) x
                where x.contract_id = k.id and k.status = 'TERMINATED' and k.status_changed_on is null
                """);
        // 실효일 = 첫 미납 회차의 유예기간(다음 달 말일) 다음 날
        int lapsed = jdbc.update("""
                update contract k set status = 'LAPSED', status_changed_on = x.lapsed_on
                from (select contract_id, min((date_trunc('month', due_date) + interval '2 month')::date) lapsed_on
                      from premium_payment where status = 'UNPAID' group by contract_id) x
                where x.contract_id = k.id and k.status = 'ACTIVE' and x.lapsed_on <= current_date and k.contract_no like 'LG%'
                """);
        log.info("이관 계약 수납 이력 {}건 생성, 실효 {}건", rows, lapsed);
        return rows;
    }
}
