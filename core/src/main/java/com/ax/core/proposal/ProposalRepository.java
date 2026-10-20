package com.ax.core.proposal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProposalRepository extends JpaRepository<Proposal, Long> {

    List<Proposal> findByCustomerIdOrderByIdDesc(Long customerId);

    List<Proposal> findTop100ByOrderByIdDesc();
}
