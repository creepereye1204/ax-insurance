package com.ax.core.product;

/**
 * 환산보험료(영업 실적 환산) 기준 — 회사마다 다르므로 포트폴리오용 가상 기준.
 * 보장성 장기납일수록 높게, 저축성·갱신형은 낮게 인정한다.
 */
public final class PremiumConversion {

    public static int ratePct(ProductType type, int paymentTerm, boolean renewable) {
        if (type.isVariable()) {
            return 50;
        }
        if (type.isSavings()) {
            return 30;
        }
        if (renewable || paymentTerm <= 1) {
            return 50;
        }
        return paymentTerm >= 20 ? 100 : paymentTerm >= 10 ? 70 : 50;
    }

    public static long converted(long monthlyPremium, int ratePct) {
        return Math.round(monthlyPremium * ratePct / 100.0 / 10) * 10;
    }

    private PremiumConversion() {
    }
}
