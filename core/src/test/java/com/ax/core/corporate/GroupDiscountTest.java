package com.ax.core.corporate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GroupDiscountTest {

    @Test
    void 단체_할인율은_인원_구간_경계에서_바뀐다() {
        assertThat(GroupQuoteService.discountPct(5)).isZero();
        assertThat(GroupQuoteService.discountPct(9)).isZero();
        assertThat(GroupQuoteService.discountPct(10)).isEqualTo(5);
        assertThat(GroupQuoteService.discountPct(49)).isEqualTo(5);
        assertThat(GroupQuoteService.discountPct(50)).isEqualTo(10);
        assertThat(GroupQuoteService.discountPct(99)).isEqualTo(10);
        assertThat(GroupQuoteService.discountPct(100)).isEqualTo(15);
    }
}
