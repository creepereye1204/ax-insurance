package com.ax.core.corporate;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupProposalRepository extends JpaRepository<GroupProposal, Long> {

    List<GroupProposal> findByCorporateIdOrderByIdDesc(Long corporateId);
}
