package com.ax.core.corporate;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CorporateMemberRepository extends JpaRepository<CorporateMember, Long> {

    @EntityGraph(attributePaths = "customer")
    List<CorporateMember> findByCorporateIdOrderById(Long corporateId);

    long countByCorporateId(Long corporateId);

    boolean existsByCorporateIdAndCustomerId(Long corporateId, Long customerId);

    void deleteByCorporateIdAndCustomerId(Long corporateId, Long customerId);
}
