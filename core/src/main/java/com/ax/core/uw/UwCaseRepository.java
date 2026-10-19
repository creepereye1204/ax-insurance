package com.ax.core.uw;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UwCaseRepository extends JpaRepository<UwCase, Long> {

    List<UwCase> findByStageAndStatusOrderByIdAsc(UwStage stage, UwCaseStatus status);

    List<UwCase> findByCustomerIdOrderByIdDesc(Long customerId);

    Optional<UwCase> findFirstByApplicationIdOrderByIdDesc(Long applicationId);
}
