package com.ax.core.uw;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PreUwInquiryRepository extends JpaRepository<PreUwInquiry, Long> {

    Optional<PreUwInquiry> findByUwCaseId(Long uwCaseId);
}
