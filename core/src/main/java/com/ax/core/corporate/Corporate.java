package com.ax.core.corporate;

import com.ax.core.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 법인고객. 단체보험의 계약자(보험료 납부)이고, 피보험자는 소속 종업원(개인고객)이다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Corporate extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    /** 사업자등록번호 (가상: 999-xx-xxxxx) */
    @Column(nullable = false, unique = true, length = 12)
    private String bizRegNo;

    @Column(nullable = false, length = 50)
    private String representative;

    @Column(nullable = false, length = 50)
    private String industry;

    /** 업종 위험등급 (1 사무 ~ 3 고위험) */
    @Column(nullable = false)
    private int industryRiskGrade;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false)
    private boolean virtualCorporate;

    public Corporate(String name, String bizRegNo, String representative, String industry, int industryRiskGrade,
                     String phone, boolean virtualCorporate) {
        this.name = name;
        this.bizRegNo = bizRegNo;
        this.representative = representative;
        this.industry = industry;
        this.industryRiskGrade = industryRiskGrade;
        this.phone = phone;
        this.virtualCorporate = virtualCorporate;
    }
}
