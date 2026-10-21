package com.ax.core.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    @Query("""
            select c from Customer c
            where (:q = '' or c.name like concat('%', :q, '%') or c.phone like concat('%', :q, '%'))
              and (:status is null or c.status = :status)
            """)
    Page<Customer> search(String q, CustomerStatus status, Pageable pageable);

    boolean existsByRrnHash(String rrnHash);

    /** 고객 유형 동기화: 유지 중인 계약 유무로 가망/계약을 맞춘다 (바뀐 행만 갱신, 반복 실행 안전) */
    @Modifying
    @Query(value = """
            update customer c set status = case when exists (
                select 1 from contract k where k.customer_id = c.id and k.status = 'ACTIVE') then 'CONTRACTED' else 'PROSPECT' end
            where c.status is distinct from case when exists (
                select 1 from contract k where k.customer_id = c.id and k.status = 'ACTIVE') then 'CONTRACTED' else 'PROSPECT' end
            """, nativeQuery = true)
    int syncStatus();
}
