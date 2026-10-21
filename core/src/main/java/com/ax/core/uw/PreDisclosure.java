package com.ax.core.uw;

import com.ax.core.uw.engine.MedicalRecord;
import java.util.List;
import java.util.Map;

/**
 * 선심사 때 고객이 미리 답한 고지 (JSON 스냅샷). 청약 전이라 고지 테이블에 저장하지 않고 심사 케이스에 남긴다.
 *
 * @param questionnaire 질문지 코드와 버전 (예: SMP-DISCLOSURE v1)
 */
public record PreDisclosure(String questionnaire, Map<String, Boolean> answers, List<MedicalRecord> details) {

    public long yesCount() {
        return answers.values().stream().filter(Boolean::booleanValue).count();
    }
}
