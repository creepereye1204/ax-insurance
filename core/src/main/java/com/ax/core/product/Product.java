package com.ax.core.product;

import com.ax.core.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    /** AI 서비스에 적재된 약관 문서명. 청구 검토 시 이 약관을 기준으로 검색한다. */
    @Column(nullable = false, length = 100)
    private String policyDocument;

    public Product(String code, String name, String policyDocument) {
        this.code = code;
        this.name = name;
        this.policyDocument = policyDocument;
    }
}
