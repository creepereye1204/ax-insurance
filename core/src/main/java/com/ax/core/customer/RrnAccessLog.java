package com.ax.core.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 주민등록번호 원문 열람 기록 (누가·언제·왜). 개인정보 접근 감사용으로 추가만 한다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RrnAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long customerId;

    @Column(nullable = false, length = 50)
    private String accessor;

    @Column(nullable = false, length = 200)
    private String reason;

    @Column(nullable = false)
    private LocalDateTime accessedAt;

    public RrnAccessLog(Long customerId, String accessor, String reason) {
        this.customerId = customerId;
        this.accessor = accessor;
        this.reason = reason;
        this.accessedAt = LocalDateTime.now();
    }
}
