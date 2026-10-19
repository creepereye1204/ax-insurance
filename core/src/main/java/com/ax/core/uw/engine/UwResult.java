package com.ax.core.uw.engine;

import com.ax.core.product.CoverageCategory;
import java.util.List;
import java.util.Map;

public record UwResult(int ruleSetVersion, Map<CoverageCategory, CoverageDecision> decisions) {

    public boolean anyReferred() {
        return has(CoverageOutcome.REFER);
    }

    public boolean anyPostponed() {
        return has(CoverageOutcome.POSTPONE);
    }

    public boolean allDeclined() {
        return decisions.values().stream().allMatch(d -> d.outcome() == CoverageOutcome.DECLINE);
    }

    public List<String> matchedRules() {
        return decisions.values().stream().flatMap(d -> d.matchedRules().stream()).distinct().sorted().toList();
    }

    private boolean has(CoverageOutcome outcome) {
        return decisions.values().stream().anyMatch(d -> d.outcome() == outcome);
    }
}
