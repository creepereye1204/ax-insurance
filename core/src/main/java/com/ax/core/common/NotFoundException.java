package com.ax.core.common;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String target, Object id) {
        super(target + "을(를) 찾을 수 없습니다: " + id);
    }
}
