package com.ax.core.claim;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimRepository extends JpaRepository<Claim, Long> {

    List<Claim> findByContractCustomerIdOrderByIdDesc(Long customerId);

    List<Claim> findTop100ByOrderByIdDesc();
}
