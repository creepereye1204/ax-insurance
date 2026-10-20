package com.ax.core.product;

import com.ax.core.customer.Gender;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 요율표: 담보 × 성별 × 가입나이 × 납입기간별 가입금액 1,000만원당 월 보험료 */
@Getter
@Entity
@Table(indexes = @Index(name = "ux_premium_rate_key", columnList = "coverageId, gender, age, paymentTerm", unique = true))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PremiumRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long coverageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 1)
    private Gender gender;

    @Column(nullable = false)
    private int age;

    @Column(nullable = false)
    private int paymentTerm;

    @Column(nullable = false)
    private long ratePer10M;
}
