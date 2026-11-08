package com.ax.core.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class RegionTest {

    @Test
    void 인구_비중_합은_1000() {
        assertThat(Arrays.stream(Region.values()).mapToInt(Region::getWeight).sum()).isEqualTo(1000);
    }

    @Test
    void 누적_비중_경계로_지역을_고른다() {
        assertThat(Region.weighted(0.0)).isEqualTo(Region.SEOUL);
        assertThat(Region.weighted(0.182)).isEqualTo(Region.SEOUL);
        assertThat(Region.weighted(0.183)).isEqualTo(Region.BUSAN);
        assertThat(Region.weighted(0.999)).isEqualTo(Region.JEJU);
        assertThat(Region.sqlCase("v")).startsWith("case when v < 183 then 'SEOUL'").endsWith("else 'SEOUL' end");
    }
}
