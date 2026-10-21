package com.ax.core.uw.rule;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UwRuleSetRepository extends JpaRepository<UwRuleSet, Long> {

    @EntityGraph(attributePaths = "rules")
    Optional<UwRuleSet> findFirstByActiveTrueOrderByVersionDesc();

    List<UwRuleSet> findByActiveTrue();

    boolean existsByVersion(int version);
}
