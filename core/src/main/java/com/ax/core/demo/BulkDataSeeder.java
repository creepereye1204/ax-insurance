package com.ax.core.demo;

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
 * 성능 테스트용 대량 가상 데이터. JPA 로 한 건씩 넣으면 수십 분이 걸리므로 PostgreSQL 의
 * generate_series + random() 으로 한 번에 생성한다. setseed 로 매번 같은 데이터가 나온다.
 *
 * <p>성능 개선은 나중에 측정 후 진행하기로 했으므로(설계 문서 12절) 여기서는 인덱스를 추가하지 않는다.
 */
@Slf4j
@Order(2)
@Component
@RequiredArgsConstructor
public class BulkDataSeeder implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Value("${ax.seed.enabled:true}")
    private boolean enabled;
    @Value("${ax.seed.customers:30000}")
    private int customers;
    @Value("${ax.seed.external-records:50000}")
    private int externalRecords;
    @Value("${ax.seed.contracts:60000}")
    private int contracts;
    @Value("${ax.seed.claims:500000}")
    private int claims;

    private static final String SAVINGS_TYPES = "('ANNUITY', 'VARIABLE_ANNUITY', 'VARIABLE_UNIVERSAL', 'SAVINGS')";

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Integer existing = jdbc.queryForObject("select count(*) from customer", Integer.class);
        if (!enabled || existing == null || existing >= customers) {
            return;
        }
        long t0 = System.currentTimeMillis();
        jdbc.queryForObject("select setseed(0.2026)", Object.class);
        long scenarioMaxId = jdbc.queryForObject("select coalesce(max(id), 0) from customer", Long.class);

        int c = insertCustomers(customers - existing);
        long[] ids = range("customer", scenarioMaxId);
        int e = insertExternalRecords(ids);
        int k = insertLegacyContracts(ids);
        int kc = insertLegacyCoverages();
        int cl = insertClaims();
        log.info("대량 데이터 생성 완료: 고객 {}, 외부이력 {}, 기존계약 {}, 계약담보 {}, 청구 {} ({}ms)",
                c, e, k, kc, cl, System.currentTimeMillis() - t0);
    }

    private long[] range(String table, long after) {
        return jdbc.queryForObject("select min(id), max(id) from " + table + " where id > ?",
                (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2)}, after);
    }

    private int insertCustomers(int n) {
        return jdbc.update("""
                insert into customer (name, birth_date, gender, phone, consumer_type, occupation, job_risk_grade,
                                      investment_profile, profiled_at, created_at, updated_at)
                select a.sur[1 + floor(x.r1 * array_length(a.sur, 1))::int]
                         || a.giv[1 + floor(x.r2 * array_length(a.giv, 1))::int]
                         || a.giv[1 + floor(x.r3 * array_length(a.giv, 1))::int],
                       date '1945-01-01' + floor(x.r4 * (date '2020-12-31' - date '1945-01-01'))::int,
                       case when x.r5 < 0.5 then 'M' else 'F' end,
                       '010-' || lpad(floor(x.r6 * 10000)::text, 4, '0') || '-' || lpad(floor(x.r7 * 10000)::text, 4, '0'),
                       case when x.r8 < 0.03 then 'PROFESSIONAL' else 'GENERAL' end,
                       a.occ[x.oi], a.risk[x.oi],
                       case when x.r9 < 0.4 then a.prof[1 + floor(x.r10 * 5)::int] end,
                       case when x.r9 < 0.4 then now() - (floor(x.r10 * 700) || ' days')::interval end,
                       now() - (floor(x.r11 * 1500) || ' days')::interval, now()
                from (select g, random() r1, random() r2, random() r3, random() r4, random() r5, random() r6,
                             random() r7, random() r8, random() r9, random() r10, random() r11,
                             1 + floor(random() * 16)::int oi
                      from generate_series(1, ?) g) x,
                     (select array['김','이','박','최','정','강','조','윤','장','임','한','오','서','신','권','황','안',
                                   '송','류','홍','전','고','문','양','손','배','백','허','유','남','심','노','하','곽'] sur,
                             array['민','서','준','지','현','우','예','도','하','윤','수','연','은','영','진','성','호',
                                   '주','원','경','혜','동','재','승','태','희','정','나','다','유','시','아','채','소'] giv,
                             array['회사원(사무직)','공무원','교사','간호사','자영업','영업직','주부','대학생','IT 개발자',
                                   '디자이너','택배기사','건설현장 근로자','소방관','어업 종사자','금융업 종사자','무직'] occ,
                             array[1,1,1,2,1,2,1,1,1,1,2,3,3,3,1,1] risk,
                             array['STABLE','STABLE_SEEKING','NEUTRAL','ACTIVE','AGGRESSIVE'] prof) a
                """, n);
    }

    private int insertExternalRecords(long[] ids) {
        return jdbc.update("""
                insert into external_medical_record (customer_id, institution, kcd_code, diagnosed_on, treatment, cured)
                select ? + floor(x.r1 * ?)::bigint,
                       (array['타사A생명(Mock)','타사B손보(Mock)','타사C생명(Mock)'])[1 + floor(x.r2 * 3)::int],
                       k.codes[1 + floor(x.r3 * array_length(k.codes, 1))::int],
                       current_date - floor(x.r4 * 1800)::int,
                       (array['OUTPATIENT','OUTPATIENT','MEDICATION','INPATIENT','SURGERY'])[1 + floor(x.r5 * 5)::int],
                       x.r6 < 0.6
                from (select g, random() r1, random() r2, random() r3, random() r4, random() r5, random() r6
                      from generate_series(1, ?) g) x,
                     (select array_agg(code order by code) codes from kcd where code not like 'Z%%' and code not like 'O%%') k
                """, ids[0], ids[1] - ids[0] + 1, externalRecords);
    }

    /** 이관 데이터 성격의 기존 계약 (청약 없이 존재). 보장성 상품만 */
    private int insertLegacyContracts(long[] ids) {
        return jdbc.update("""
                insert into contract (contract_no, customer_id, product_id, application_id, status, start_date, end_date,
                                      payment_term, monthly_premium, withdrawal_deadline, created_at, updated_at)
                select 'LG' || lpad(x.g::text, 8, '0'), x.cid, p.id, null,
                       case when x.r1 < 0.06 then 'TERMINATED' else 'ACTIVE' end,
                       x.start, (x.start + interval '20 years')::date - 1,
                       (p.payment_terms ->> 0)::int, 0, x.start + 15, now(), now()
                from (select g, ? + floor(random() * ?)::bigint cid, random() r1,
                             current_date - (30 + floor(random() * 1800))::int start,
                             1 + floor(random() * pp.n)::int pi
                      from generate_series(1, ?) g,
                           (select count(*) n from product where type not in %s) pp) x
                join (select id, payment_terms, row_number() over (order by id) rn from product where type not in %s) p
                  on p.rn = x.pi
                """.formatted(SAVINGS_TYPES, SAVINGS_TYPES), ids[0], ids[1] - ids[0] + 1, contracts);
    }

    /** 주계약은 항상, 특약은 50% 확률로 기본 가입금액에 가입. 보험료는 가입 당시 나이의 요율로 계산 */
    private int insertLegacyCoverages() {
        int n = jdbc.update("""
                insert into contract_coverage (contract_id, coverage_id, insured_amount, rating_pct, exclusions, monthly_premium)
                select k.id, cv.id, cv.default_amount, 0, '[]'::jsonb,
                       coalesce(round(cv.default_amount::numeric * pr.rate_per10m / 10000000 / 10) * 10, 0)
                from contract k
                join product p on p.id = k.product_id
                join customer cu on cu.id = k.customer_id
                join coverage cv on cv.product_id = k.product_id
                left join premium_rate pr on pr.coverage_id = cv.id and pr.gender = cu.gender
                     and pr.payment_term = k.payment_term
                     and pr.age = least(greatest(date_part('year', age(k.start_date, cu.birth_date))::int, p.min_entry_age),
                                        p.max_entry_age)
                where k.contract_no like 'LG0%' and (cv.required or random() < 0.5)
                """);
        jdbc.update("""
                update contract k set monthly_premium = s.total
                from (select contract_id, sum(monthly_premium) total from contract_coverage group by contract_id) s
                where s.contract_id = k.id and k.contract_no like 'LG0%'
                """);
        return n;
    }

    /** 과거 보험금 청구 (지급·부지급 종결 건) */
    private int insertClaims() {
        long[] k = jdbc.queryForObject("select min(id), max(id) from contract where contract_no like 'LG0%'",
                (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2)});
        return jdbc.update("""
                insert into claim (claim_no, contract_id, type, treatment_date, diagnosis, kcd_code, claimed_amount,
                                   status, check_notes, paid_amount, reviewer_comment, decided_at, created_at, updated_at)
                select 'LC' || lpad(x.g::text, 9, '0'), k.id,
                       (array['OUTPATIENT','OUTPATIENT','OUTPATIENT','PRESCRIPTION','INPATIENT'])[1 + floor(x.r1 * 5)::int],
                       k.start_date + floor(x.r2 * (current_date - 1 - k.start_date))::int,
                       kc.name || ' 진료', kc.code, x.amount,
                       case when x.r3 < 0.88 then 'APPROVED' else 'REJECTED' end,
                       '[]'::jsonb,
                       case when x.r3 < 0.88 then round(x.amount * 0.8 / 100) * 100 else 0 end,
                       '기존 처리 건(이관 데이터)',
                       (k.start_date + floor(x.r2 * (current_date - 1 - k.start_date))::int + 5)::timestamp,
                       now(), now()
                from (select g, ? + floor(random() * ?)::bigint kid, random() r1, random() r2, random() r3,
                             1 + floor(random() * kk.n)::int ki, (10000 + floor(random() * 150) * 10000)::bigint amount
                      from generate_series(1, ?) g,
                           (select count(*) n from kcd where code not like 'Z%%') kk) x
                join contract k on k.id = x.kid
                join (select code, name, row_number() over (order by code) rn from kcd where code not like 'Z%%') kc
                  on kc.rn = x.ki
                """, k[0], k[1] - k[0] + 1, claims);
    }
}
