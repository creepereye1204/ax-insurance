package com.ax.core.application;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    List<Application> findByCustomerIdOrderByIdDesc(Long customerId);

    List<Application> findTop100ByOrderByIdDesc();
}
