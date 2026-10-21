package com.ax.core.customer;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRelationRepository extends JpaRepository<CustomerRelation, Long> {

    @EntityGraph(attributePaths = "relative")
    List<CustomerRelation> findByCustomerIdOrderById(Long customerId);

    boolean existsByCustomerIdAndRelativeId(Long customerId, Long relativeId);

    long countByCustomerIdAndRelation(Long customerId, Relation relation);

    void deleteByCustomerIdAndRelativeId(Long customerId, Long relativeId);
}
