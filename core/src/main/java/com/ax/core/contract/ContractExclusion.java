package com.ax.core.contract;

import java.time.LocalDate;
import java.util.List;

/** 계약에 적용된 부담보: until 까지 해당 질병코드 패턴은 보장하지 않는다. */
public record ContractExclusion(String label, List<String> kcd, LocalDate until) {
}
