package com.ax.core.customer;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RrnAccessLogRepository extends JpaRepository<RrnAccessLog, Long> {

    List<RrnAccessLog> findTop20ByCustomerIdOrderByIdDesc(Long customerId);
}
