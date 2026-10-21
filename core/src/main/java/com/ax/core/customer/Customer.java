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

/**
 * 고객. 주민등록번호는 시스템이 만든 가상 번호만 암호화해 저장한다(원문 컬럼 없음, ResidentNumbers 참고).
 * 기능 추가로 생긴 컬럼(유형·운전·주민번호)은 기존 3만 건이 있는 테이블이라 null 허용으로 추가했다.
 */
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

    /** 가망/계약 (null 은 기동 시 동기화 전) */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CustomerStatus status;

    /** 운전 여부 (null 은 미확인) */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DrivingType driving;

    /** 화면에서 즉시 생성한 가상고객 */
    private Boolean virtualCustomer;

    @Column(length = 120)
    private String rrnEncrypted;

    @Column(length = 14)
    private String rrnMasked;

    /** 블라인드 인덱스 (HMAC) — 중복 확인·검색용 */
    @Column(length = 64)
    private String rrnHash;

    public Customer(String name, LocalDate birthDate, Gender gender, String phone,
                    ConsumerType consumerType, String occupation, int jobRiskGrade) {
        this.name = name;
        this.birthDate = birthDate;
        this.gender = gender;
        this.phone = phone;
        this.consumerType = consumerType;
        this.occupation = occupation;
        this.jobRiskGrade = jobRiskGrade;
        this.status = CustomerStatus.PROSPECT;
    }

    public void update(String name, String phone, String occupation, int jobRiskGrade, ConsumerType consumerType,
                       DrivingType driving) {
        this.name = name;
        this.phone = phone;
        this.occupation = occupation;
        this.jobRiskGrade = jobRiskGrade;
        this.consumerType = consumerType;
        this.driving = driving;
    }

    public void changeDriving(DrivingType driving) {
        this.driving = driving;
    }

    public void markVirtual() {
        this.virtualCustomer = true;
    }

    public boolean isVirtual() {
        return Boolean.TRUE.equals(virtualCustomer);
    }

    public void assignRrn(ResidentNumbers.Protected rrn) {
        this.rrnEncrypted = rrn.encrypted();
        this.rrnMasked = rrn.masked();
        this.rrnHash = rrn.hash();
    }

    public void changeStatus(CustomerStatus status) {
        this.status = status;
    }

    public CustomerStatus statusOrProspect() {
        return status == null ? CustomerStatus.PROSPECT : status;
    }

    public void assessInvestmentProfile(InvestmentProfile profile) {
        this.investmentProfile = profile;
        this.profiledAt = LocalDateTime.now();
    }

    /** 변액 적합성: 오늘 진단한 투자성향인가 (금소법 — 설계할 때마다 다시 진단) */
    public boolean isProfiledOn(LocalDate date) {
        return investmentProfile != null && profiledAt != null && profiledAt.toLocalDate().equals(date);
    }

    /** 만 나이 */
    public int ageAt(LocalDate date) {
        return Period.between(birthDate, date).getYears();
    }

    /** 보험나이: 만 나이에서 6개월 이상 끝수는 1세로 올린다. 보험료·가입나이 판단 기준 */
    public int insuranceAgeAt(LocalDate date) {
        Period p = Period.between(birthDate, date);
        return p.getYears() + (p.getMonths() >= 6 ? 1 : 0);
    }

    /**
     * 다음 상령일: 보험나이가 실제로 1세 오르는 첫날.
     * 대략 "생일 + 6개월"이지만 월말 생일(예: 8/31)은 2월에 6개월이 차지 않아 다음 달 1일이 되므로,
     * 근처 날짜부터 보험나이가 바뀌는 날을 직접 찾는다 (insuranceAgeAt 과 항상 일치).
     */
    public LocalDate nextAgeUpDate(LocalDate date) {
        int current = insuranceAgeAt(date);
        LocalDate d = birthDate.plusYears(current).plusMonths(6).minusDays(3);
        if (!d.isAfter(date)) {
            d = date.plusDays(1);
        }
        while (insuranceAgeAt(d) <= current) {
            d = d.plusDays(1);
        }
        return d;
    }

    /** 고령 금융소비자 보호 대상 (일반금융소비자 + 만 65세 이상) */
    public boolean isProtectedSenior(LocalDate date) {
        return consumerType == ConsumerType.GENERAL && ageAt(date) >= SENIOR_AGE;
    }
}
