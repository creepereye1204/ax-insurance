package com.ax.core.common;

/** 업무 규칙 위반 (예: 해지된 계약으로 청구, 이미 심사 완료된 청구 재심사). */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
