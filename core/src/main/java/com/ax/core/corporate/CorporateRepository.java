package com.ax.core.corporate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CorporateRepository extends JpaRepository<Corporate, Long> {

    @Query("select c from Corporate c where :q = '' or c.name like concat('%', :q, '%') or c.bizRegNo like concat('%', :q, '%')")
    Page<Corporate> search(String q, Pageable pageable);

    boolean existsByBizRegNo(String bizRegNo);
}
