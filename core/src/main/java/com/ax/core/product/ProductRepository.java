package com.ax.core.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsByCode(String code);

    @Query("""
            select p from Product p
            where (:type is null or p.type = :type)
              and (:q = '' or p.name like concat('%', :q, '%') or p.code like concat('%', :q, '%'))
              and (:onSaleOnly = false or p.saleStatus = com.ax.core.product.SaleStatus.ON_SALE)
            """)
    Page<Product> search(ProductType type, String q, boolean onSaleOnly, Pageable pageable);
}
