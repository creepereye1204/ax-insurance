package com.ax.core.kcd;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 한국표준질병사인분류(KCD) 코드. 데모용으로 일부만 적재한다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Kcd {

    @Id
    @Column(length = 10)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    public Kcd(String code, String name) {
        this.code = code;
        this.name = name;
    }

    /** 룰 조건의 코드 패턴("E11*", "C*", "K29")과 일치하는지. */
    public static boolean matches(String code, String pattern) {
        if (code == null) {
            return false;
        }
        return pattern.endsWith("*")
                ? code.startsWith(pattern.substring(0, pattern.length() - 1))
                : code.equals(pattern);
    }
}
