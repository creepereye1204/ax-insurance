package com.ax.core.uw.rule;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 심사 룰 묶음. 룰을 바꿀 때는 새 버전을 만들고 활성 버전을 교체한다(과거 판단 재현용). */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UwRuleSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private int version;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false, length = 200)
    private String description;

    @OneToMany(mappedBy = "ruleSet", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("priority")
    private List<UwRule> rules = new ArrayList<>();

    public UwRuleSet(int version, String description) {
        this.version = version;
        this.description = description;
        this.active = true;
    }

    /** 새 버전을 활성화하면 이전 버전은 비활성으로 남긴다 (과거 심사 재현용으로 삭제하지 않음) */
    public void deactivate() {
        this.active = false;
    }

    public UwRuleSet add(UwRule rule) {
        rule.attachTo(this);
        rules.add(rule);
        return this;
    }
}
