package com.ax.core.contract;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractRepository extends JpaRepository<Contract, Long> {

    List<Contract> findByCustomerIdOrderByIdDesc(Long customerId);

    List<Contract> findTop100ByOrderByIdDesc();

    Optional<Contract> findByApplicationId(Long applicationId);

    boolean existsByCustomerIdAndStatus(Long customerId, ContractStatus status);
}
