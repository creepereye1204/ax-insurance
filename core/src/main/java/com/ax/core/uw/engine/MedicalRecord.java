package com.ax.core.uw.engine;

import com.ax.core.common.TreatmentType;
import java.time.LocalDate;

/** 심사에 쓰는 의료 이력 한 건. 출처(source)는 고지 / 내부청구 / 외부기관 */
public record MedicalRecord(String source, String kcdCode, LocalDate diagnosedOn, TreatmentType treatment, boolean cured) {
}
