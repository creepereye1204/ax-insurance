package com.ax.core.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 변액보험 펀드 (가상) */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Fund {

    @Id
    @Column(length = 20)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 20)
    private String assetClass;

    /** 위험등급 (1 매우높은위험 ~ 6 매우낮은위험) */
    @Column(nullable = false)
    private int riskGrade;

    /** 연간 펀드 운용보수 (bp, 100 = 1%) */
    @Column(nullable = false)
    private int annualFeeBp;

    public Fund(String code, String name, String assetClass, int riskGrade, int annualFeeBp) {
        this.code = code;
        this.name = name;
        this.assetClass = assetClass;
        this.riskGrade = riskGrade;
        this.annualFeeBp = annualFeeBp;
    }
}
