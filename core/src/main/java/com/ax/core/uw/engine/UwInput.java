package com.ax.core.uw.engine;

import com.ax.core.product.CoverageCategory;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 룰 엔진 입력.
 *
 * @param simplifiedUw 간편심사형 상품 여부 (룰 적용 범위 결정)
 * @param answers      고지 응답 (문항코드 → 예/아니오). 고지 없이 조회만 한 선심사는 비어 있다.
 */
public record UwInput(
        LocalDate asOf,
        int age,
        int jobRiskGrade,
        boolean simplifiedUw,
        Map<String, Boolean> answers,
        List<MedicalRecord> records,
        List<LocalDate> claimDates,
        List<CoverageCategory> coverages) {
}
