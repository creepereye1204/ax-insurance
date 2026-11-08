package com.ax.core.agent;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 판매 채널 */
@Getter
@RequiredArgsConstructor
public enum AgentChannel {
    FC("전속 설계사"),
    GA("법인대리점(GA)"),
    TM("텔레마케팅");

    private final String label;
}
