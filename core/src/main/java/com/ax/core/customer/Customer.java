package com.ax.core.customer;

import com.ax.core.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 고객. 주민등록번호는 저장하지 않는다(생년월일·성별만). */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer extends BaseTimeEntity {

    public static final int SENIOR_AGE = 65;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 1)
    private Gender gender;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConsumerType consumerType;

    @Column(nullable = false, length = 50)
    private String occupation;

    /** 직업 위험등급 (1 사무직 ~ 3 고위험) */
    @Column(nullable = false)
    private int jobRiskGrade;

    /** 투자성향 (진단 전이면 null) */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private InvestmentProfile investmentProfile;

    private LocalDateTime profiledAt;

    public Customer(String name, LocalDate birthDate, Gender gender, String phone,
                    ConsumerType consumerType, String occupation, int jobRiskGrade) {
        this.name = name;
        this.birthDate = birthDate;
        this.gender = gender;
        this.phone = phone;
        this.consumerType = consumerType;
        this.occupation = occupation;
        this.jobRiskGrade = jobRiskGrade;
    }

    public void update(String name, String phone, String occupation, int jobRiskGrade, ConsumerType consumerType) {
        this.name = name;
        this.phone = phone;
        this.occupation = occupation;
        this.jobRiskGrade = jobRiskGrade;
        this.consumerType = consumerType;
    }

    public void assessInvestmentProfile(InvestmentProfile profile) {
        this.investmentProfile = profile;
        this.profiledAt = LocalDateTime.now();
    }

    /** 만 나이 */
    public int ageAt(LocalDate date) {
        return Period.between(birthDate, date).getYears();
    }

    /** 고령 금융소비자 보호 대상 (일반금융소비자 + 만 65세 이상) */
    public boolean isProtectedSenior(LocalDate date) {
        return consumerType == ConsumerType.GENERAL && ageAt(date) >= SENIOR_AGE;
    }
}
