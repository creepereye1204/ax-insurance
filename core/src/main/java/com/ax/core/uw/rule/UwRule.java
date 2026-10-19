package com.ax.core.uw.rule;

import com.ax.core.product.CoverageCategory;
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
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 심사 룰. 조건·결과를 코드가 아닌 데이터(JSON)로 관리해서
 * 룰이 늘어나도 엔진 코드는 바뀌지 않는다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UwRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_set_id")
    private UwRuleSet ruleSet;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private int priority;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private RuleCondition condition;

    /** 적용 담보 분류. 비어 있으면 전체 담보 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<CoverageCategory> categories;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UwAction action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private RuleParams params;

    public UwRule(String code, String name, int priority, RuleCondition condition,
                  List<CoverageCategory> categories, UwAction action, RuleParams params) {
        this.code = code;
        this.name = name;
        this.priority = priority;
        this.condition = condition;
        this.categories = categories;
        this.action = action;
        this.params = params;
    }

    void attachTo(UwRuleSet ruleSet) {
        this.ruleSet = ruleSet;
    }

    public boolean appliesTo(CoverageCategory category) {
        return categories.isEmpty() || categories.contains(category);
    }
}
