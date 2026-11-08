package com.ax.core.common;

import jakarta.persistence.EntityManagerFactory;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.mapping.BasicValuedModelPart;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * enum 컬럼의 CHECK 제약을 현재 enum 값에 맞춘다 (troubleshooting #15).
 * Hibernate 는 @Enumerated(STRING) 컬럼에 "값 IN (...)" CHECK 제약을 만들지만, ddl-auto=update 는 기존 제약을 고치지 않는다.
 * enum 에 값을 추가하면(예: 계약 상태 LAPSED) 운영 DB 에서 그 값을 저장할 수 없다.
 * 모든 엔티티의 문자열 enum 컬럼을 훑어 값이 빠진 제약만 다시 만든다. 기간계 2대가 동시에 떠도 advisory lock 으로 한 번만.
 */
@Slf4j
@Order(0)
@Component
@RequiredArgsConstructor
public class EnumCheckConstraintSync implements ApplicationRunner {

    private static final long LOCK_KEY = 7_340_000L;

    private final EntityManagerFactory emf;
    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        jdbc.queryForObject("select pg_advisory_xact_lock(?)::text", String.class, LOCK_KEY);
        emf.unwrap(SessionFactoryImplementor.class).getMappingMetamodel().forEachEntityDescriptor(entity ->
                entity.forEachAttributeMapping(attribute -> {
                    BasicValuedModelPart basic = attribute.asBasicValuedModelPart();
                    if (basic != null && basic.getJavaType().getJavaTypeClass().isEnum()
                            && basic.getJdbcMapping().getJdbcType().isString()) {
                        sync(basic.getContainingTableExpression(), basic.getSelectionExpression(),
                                basic.getJavaType().getJavaTypeClass().getEnumConstants());
                    }
                }));
    }

    private void sync(String table, String column, Object[] constants) {
        // 테이블 식은 스키마를 포함한다 (core.contract) → 제약 이름은 테이블 이름만으로 (contract_status_check)
        String name = table.substring(table.lastIndexOf('.') + 1) + "_" + column + "_check";
        List<String> defs = jdbc.queryForList(
                "select pg_get_constraintdef(oid) from pg_constraint where conname = ? and conrelid = to_regclass(?)",
                String.class, name, table);
        if (defs.isEmpty()) {
            return;   // 제약이 없는 컬럼은 건드리지 않는다
        }
        List<String> values = Arrays.stream(constants).map(c -> ((Enum<?>) c).name()).toList();
        List<String> missing = values.stream().filter(v -> !defs.get(0).contains("'" + v + "'")).toList();
        if (missing.isEmpty()) {
            return;
        }
        String in = values.stream().map(v -> "'" + v + "'").collect(Collectors.joining(", "));
        jdbc.execute("alter table " + table + " drop constraint " + name);
        jdbc.execute("alter table " + table + " add constraint " + name + " check (" + column + " in (" + in + "))");
        log.info("enum CHECK 제약 갱신: {}.{} + {}", table, column, missing);
    }
}
