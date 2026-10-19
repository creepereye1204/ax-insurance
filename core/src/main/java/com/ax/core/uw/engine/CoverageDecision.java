package com.ax.core.uw.engine;

import com.ax.core.product.CoverageCategory;
import java.util.List;

public record CoverageDecision(
        CoverageCategory category,
        CoverageOutcome outcome,
        List<Exclusion> exclusions,
        int ratingPct,
        List<String> matchedRules,
        List<String> reasons) {
}
