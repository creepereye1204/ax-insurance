package com.ax.core.corporate;

import com.ax.core.common.BaseTimeEntity;
import com.ax.core.customer.Customer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 법인 소속 종업원 (개인고객과 연결) */
@Getter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"corporate_id", "customer_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CorporateMember extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "corporate_id")
    private Corporate corporate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(nullable = false, length = 20)
    private String employeeNo;

    @Column(nullable = false, length = 50)
    private String department;

    @Column(nullable = false)
    private LocalDate joinedOn;

    public CorporateMember(Corporate corporate, Customer customer, String employeeNo, String department, LocalDate joinedOn) {
        this.corporate = corporate;
        this.customer = customer;
        this.employeeNo = employeeNo;
        this.department = department;
        this.joinedOn = joinedOn;
    }
}
