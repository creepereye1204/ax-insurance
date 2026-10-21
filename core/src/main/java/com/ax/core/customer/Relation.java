package com.ax.core.customer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 가족관계 (A 기준 B 가 무엇인가). 등록하면 반대 방향도 함께 등록된다. */
@Getter
@RequiredArgsConstructor
public enum Relation {
    SPOUSE("배우자"),
    PARENT("부모"),
    CHILD("자녀"),
    SIBLING("형제자매");

    private final String label;

    public Relation inverse() {
        return switch (this) {
            case PARENT -> CHILD;
            case CHILD -> PARENT;
            default -> this;
        };
    }
}
