package com.ax.core.proposal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProposalDraftRepository extends JpaRepository<ProposalDraft, Long> {

    Optional<ProposalDraft> findByCustomerIdAndProductId(Long customerId, Long productId);

    List<ProposalDraft> findByCustomerIdOrderBySavedAtDesc(Long customerId);

    List<ProposalDraft> findTop50ByOrderBySavedAtDesc();
}
