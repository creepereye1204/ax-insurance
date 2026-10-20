package com.ax.core.uw.engine;

import com.ax.core.product.CoverageCategory;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 룰 엔진 입력.
 *
 * @param answers 고지 응답 (문항코드 → 예/아니오). 선심사는 고지 전이므로 비어 있다.
 */
public record UwInput(
        LocalDate asOf,
        int age,
        int jobRiskGrade,
        Map<String, Boolean> answers,
        List<MedicalRecord> records,
        List<LocalDate> claimDates,
        List<CoverageCategory> coverages) {
}
