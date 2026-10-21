package com.ax.core.customer;

import com.ax.core.common.BaseTimeEntity;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 가족관계: customer 기준 relative 가 relation 이다 (양방향으로 두 행을 저장) */
@Getter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"customer_id", "relative_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerRelation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "relative_id")
    private Customer relative;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Relation relation;

    public CustomerRelation(Customer customer, Customer relative, Relation relation) {
        this.customer = customer;
        this.relative = relative;
        this.relation = relation;
    }
}
