package com.ax.core.product;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductPlanRepository extends JpaRepository<ProductPlan, Long> {

    List<ProductPlan> findByProductIdOrderByCustomAscIdAsc(Long productId);

    List<ProductPlan> findByProductIdInOrderByCustomAscIdAsc(Collection<Long> productIds);
}
