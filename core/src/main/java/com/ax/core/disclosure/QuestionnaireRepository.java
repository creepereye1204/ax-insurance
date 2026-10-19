package com.ax.core.disclosure;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionnaireRepository extends JpaRepository<Questionnaire, Long> {

    /** 기준일에 유효한 최신 버전 */
    Optional<Questionnaire> findFirstByCodeAndEffectiveFromLessThanEqualOrderByVersionDesc(String code, LocalDate date);

    boolean existsByCode(String code);
}
