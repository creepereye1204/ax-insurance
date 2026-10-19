package com.ax.core.customer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentRepository extends JpaRepository<Consent, Long> {

    Optional<Consent> findFirstByCustomerIdAndTypeAndExpiresAtAfterOrderByAgreedAtDesc(
            Long customerId, ConsentType type, LocalDateTime now);

    List<Consent> findByCustomerIdOrderByIdDesc(Long customerId);
}
