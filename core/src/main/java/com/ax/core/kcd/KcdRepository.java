package com.ax.core.kcd;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface KcdRepository extends JpaRepository<Kcd, String> {

    @Query("select k from Kcd k where upper(k.code) like upper(concat(:q, '%')) or k.name like concat('%', :q, '%') order by k.code")
    List<Kcd> search(String q);
}
