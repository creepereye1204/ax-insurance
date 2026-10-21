package com.ax.core.product;

import com.ax.core.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 플랜(담보 묶음). 상품이 제공하는 기본 플랜(실속·표준·고급)과 설계사가 저장한 플랜이 있다.
 * 가입설계에서 플랜을 고르면 담보 선택·가입금액이 채워지고, 이후 자유롭게 바꿀 수 있다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductPlan extends BaseTimeEntity {

    public record Item(Long coverageId, long amount) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 200)
    private String description;

    /** 설계사가 저장한 플랜 (기본 플랜은 삭제 불가) */
    @Column(nullable = false)
    private boolean custom;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<Item> items;

    public ProductPlan(Product product, String name, String description, boolean custom, List<Item> items) {
        this.product = product;
        this.name = name;
        this.description = description;
        this.custom = custom;
        this.items = items;
    }
}
