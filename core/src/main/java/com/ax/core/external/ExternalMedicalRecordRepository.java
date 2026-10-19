package com.ax.core.external;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalMedicalRecordRepository extends JpaRepository<ExternalMedicalRecord, Long> {

    List<ExternalMedicalRecord> findByCustomerIdOrderByDiagnosedOnDesc(Long customerId);
}
