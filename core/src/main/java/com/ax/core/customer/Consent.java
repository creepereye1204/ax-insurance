package com.ax.core.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 정보 조회·이용 동의. 선심사는 유효한 동의가 있을 때만 가능하다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Consent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ConsentType type;

    @Column(nullable = false)
    private LocalDateTime agreedAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    public Consent(Customer customer, ConsentType type, LocalDateTime agreedAt) {
        this.customer = customer;
        this.type = type;
        this.agreedAt = agreedAt;
        this.expiresAt = agreedAt.plusDays(type.getValidDays());
    }

    public boolean isValidAt(LocalDateTime time) {
        return !time.isBefore(agreedAt) && time.isBefore(expiresAt);
    }
}
