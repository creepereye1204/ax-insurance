package com.ax.core.product;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoverageRuleRepository extends JpaRepository<CoverageRule, Long> {

    @EntityGraph(attributePaths = {"coverage", "target"})
    List<CoverageRule> findByProductIdOrderById(Long productId);

    @EntityGraph(attributePaths = {"coverage", "target"})
    List<CoverageRule> findAllByOrderById();

    default List<RiderRules.Rule> specsOf(Long productId) {
        return findByProductIdOrderById(productId).stream().map(CoverageRule::spec).toList();
    }
}
