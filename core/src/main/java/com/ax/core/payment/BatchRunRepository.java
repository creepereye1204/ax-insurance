package com.ax.core.payment;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BatchRunRepository extends JpaRepository<BatchRun, Long> {

    List<BatchRun> findTop20ByJobOrderByIdDesc(String job);
}
