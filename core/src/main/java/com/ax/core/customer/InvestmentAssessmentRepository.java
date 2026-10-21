package com.ax.core.customer;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestmentAssessmentRepository extends JpaRepository<InvestmentAssessment, Long> {

    List<InvestmentAssessment> findTop10ByCustomerIdOrderByIdDesc(Long customerId);
}
