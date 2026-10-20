package com.ax.core.proposal;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 변액보험 적합성 판단 결과 (금융소비자보호법 적합성 원칙) */
@Getter
@RequiredArgsConstructor
public enum Suitability {
    NOT_APPLICABLE("해당 없음"),
    SUITABLE("적합"),
    UNSUITABLE("부적합 — 고객 확인 필요"),
    UNSUITABLE_ACKNOWLEDGED("부적합 — 고객 확인"),
    EXEMPT_PROFESSIONAL("전문금융소비자 — 적용 제외");

    private final String label;
}
