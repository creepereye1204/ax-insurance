package com.ax.core.claim;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ClaimType {
    INPATIENT("입원"),
    OUTPATIENT("통원"),
    PRESCRIPTION("처방조제");

    private final String label;
}
