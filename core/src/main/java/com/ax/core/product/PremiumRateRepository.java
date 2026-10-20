package com.ax.core.product;

import com.ax.core.customer.Gender;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PremiumRateRepository extends JpaRepository<PremiumRate, Long> {

    Optional<PremiumRate> findByCoverageIdAndGenderAndAgeAndPaymentTerm(Long coverageId, Gender gender, int age, int paymentTerm);
}
