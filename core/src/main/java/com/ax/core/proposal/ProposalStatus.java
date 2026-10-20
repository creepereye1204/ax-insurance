package com.ax.core.proposal;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProposalStatus {
    ISSUED("설계서 발행"),
    CONVERTED("청약 전환");

    private final String label;
}
