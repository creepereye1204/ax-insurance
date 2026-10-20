package com.ax.core.application;

import com.ax.core.customer.Customer;
import com.ax.core.product.Product;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 청약서 서명 시 고객이 확인하는 항목. 고객·상품에 따라 필수 항목이 달라진다. */
@Getter
@RequiredArgsConstructor
public enum Agreement {
    EXPLAINED("상품설명서와 중요사항을 설명 듣고 이해했습니다."),
    TERMS_RECEIVED("약관과 청약서 부본을 받았습니다."),
    DISCLOSURE_TRUE("계약 전 알릴의무 사항을 사실대로 작성했습니다."),
    PRIVACY("보험계약 체결을 위한 개인(신용)정보 수집·이용에 동의합니다."),
    VARIABLE_RISK("변액보험은 투자 결과에 따라 원금 손실이 발생할 수 있고 예금자보호 대상이 아님을 설명 들었습니다."),
    SENIOR_PROTECTION("만 65세 이상 고령 소비자 보호를 위해 가족 등 조력자 동석 또는 녹취 확인 절차를 거쳤습니다.");

    private final String text;

    public static List<Agreement> requiredFor(Customer customer, Product product, LocalDate date) {
        List<Agreement> required = new ArrayList<>(List.of(EXPLAINED, TERMS_RECEIVED, DISCLOSURE_TRUE, PRIVACY));
        if (product.getType().isVariable()) {
            required.add(VARIABLE_RISK);
        }
        if (customer.isProtectedSenior(date)) {
            required.add(SENIOR_PROTECTION);
        }
        return required;
    }
}
