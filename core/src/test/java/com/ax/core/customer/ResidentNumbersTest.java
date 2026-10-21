package com.ax.core.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Base64;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class ResidentNumbersTest {

    final ResidentNumbers numbers = new ResidentNumbers(Base64.getEncoder().encodeToString(new byte[32]));

    @RepeatedTest(20)
    void 가상번호는_생년월일_성별과_맞고_검증번호가_틀리다() {
        String m90 = ResidentNumbers.generateVirtual(LocalDate.of(1990, 12, 31), Gender.M);
        String f05 = ResidentNumbers.generateVirtual(LocalDate.of(2005, 3, 1), Gender.F);
        assertThat(m90).matches("901231-199\\d{4}");
        assertThat(f05).matches("050301-499\\d{4}");
        assertThat(ResidentNumbers.isVirtual(m90)).isTrue();
        assertThat(ResidentNumbers.isVirtual(f05)).isTrue();
    }

    @Test
    void 검증번호가_맞는_번호는_가상번호가_아니므로_저장_거부() {
        String twelve = "901231199123";
        String valid = twelve.substring(0, 6) + "-" + twelve.substring(6) + ResidentNumbers.checkDigit(twelve);
        assertThat(ResidentNumbers.isVirtual(valid)).isFalse();
        assertThatThrownBy(() -> numbers.protect(valid)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 암호문은_매번_다르지만_복호화하면_같고_블라인드_인덱스는_같다() {
        String rrn = ResidentNumbers.generateVirtual(LocalDate.of(1988, 5, 5), Gender.M);
        ResidentNumbers.Protected a = numbers.protect(rrn);
        ResidentNumbers.Protected b = numbers.protect(rrn);
        assertThat(a.encrypted()).isNotEqualTo(b.encrypted()).doesNotContain(rrn.substring(7));
        assertThat(numbers.decrypt(a.encrypted())).isEqualTo(rrn);
        assertThat(a.hash()).isEqualTo(b.hash()).hasSize(64);
        assertThat(a.masked()).isEqualTo(rrn.substring(0, 8) + "******");
    }

    @Test
    void 키가_다르면_복호화할_수_없다() {
        String stored = numbers.encrypt(ResidentNumbers.generateVirtual(LocalDate.of(1970, 1, 1), Gender.F));
        byte[] other = new byte[32];
        other[0] = 1;
        ResidentNumbers wrongKey = new ResidentNumbers(Base64.getEncoder().encodeToString(other));
        assertThatThrownBy(() -> wrongKey.decrypt(stored)).isInstanceOf(IllegalStateException.class);
    }
}
