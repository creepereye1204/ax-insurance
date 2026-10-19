package com.ax.core.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ax.core.common.BusinessException;
import com.ax.core.contract.Contract;
import com.ax.core.customer.Customer;
import com.ax.core.customer.Gender;
import com.ax.core.product.Product;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ClaimTest {

    static Contract contract() {
        return new Contract("CT1", new Customer("홍길동", LocalDate.of(1995, 1, 1), Gender.M, null),
                new Product("P1", "샘플 실손", "샘플_실손의료비보험", 65), null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2030, 12, 31));
    }

    static Claim claim(long amount) {
        return new Claim("CL1", contract(), ClaimType.OUTPATIENT, LocalDate.of(2026, 10, 1), "급성 위염", "K29.7", amount);
    }

    @Test
    void 보장기간_밖의_진료는_접수할_수_없다() {
        assertThatThrownBy(() ->
                new Claim("CL1", contract(), ClaimType.OUTPATIENT, LocalDate.of(2025, 12, 31), "감기", "J00", 10_000))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 해지된_계약으로는_접수할_수_없다() {
        Contract contract = contract();
        contract.terminate();
        assertThatThrownBy(() ->
                new Claim("CL1", contract, ClaimType.OUTPATIENT, LocalDate.of(2026, 10, 1), "감기", "J00", 10_000))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void AI_검토_완료_후_심사자가_승인한다() {
        Claim claim = claim(50_000);
        claim.startAiReview();
        claim.completeAiReview("공제 1만원 차감 후 4만원 지급 예상", 1L);
        claim.approve(40_000, "AI 의견과 동일");

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(claim.getPaidAmount()).isEqualTo(40_000);
    }

    @Test
    void AI_검토_전에는_심사_결정을_할_수_없다() {
        Claim claim = claim(50_000);
        assertThatThrownBy(() -> claim.approve(40_000, null)).isInstanceOf(BusinessException.class);
    }

    @Test
    void 지급액은_청구액을_넘을_수_없다() {
        Claim claim = claim(50_000);
        claim.startAiReview();
        claim.fallbackToManualReview("timeout");
        assertThatThrownBy(() -> claim.approve(60_000, null)).isInstanceOf(BusinessException.class);
    }
}
