package com.ax.core.uw;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UwStage {
    PRE_UW("선심사"),
    AUTO_UW("전산심사"),
    MANUAL_UW("본사심사");

    private final String label;
}
