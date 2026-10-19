package com.ax.core.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TreatmentType {
    OUTPATIENT("외래"),
    INPATIENT("입원"),
    SURGERY("수술"),
    MEDICATION("투약");

    private final String label;
}
