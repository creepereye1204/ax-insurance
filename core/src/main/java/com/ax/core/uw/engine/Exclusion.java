package com.ax.core.uw.engine;

import java.util.List;

/** 부담보 조건: 해당 질병코드 패턴은 지정 기간 동안 보장하지 않는다. */
public record Exclusion(String label, List<String> kcd, int months) {
}
