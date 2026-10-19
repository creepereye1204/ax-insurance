package com.ax.core.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    @Query("select c from Customer c where :q = '' or c.name like concat('%', :q, '%') or c.phone like concat('%', :q, '%')")
    Page<Customer> search(String q, Pageable pageable);
}
