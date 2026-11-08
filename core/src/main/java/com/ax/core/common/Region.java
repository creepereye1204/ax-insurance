package com.ax.core.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 시·도 (정보계 지역 차원). weight: 가상 데이터 배분용 인구 비중(‰, 합 1000) */
@Getter
@RequiredArgsConstructor
public enum Region {
    SEOUL("서울", 183), BUSAN("부산", 64), DAEGU("대구", 46), INCHEON("인천", 58), GWANGJU("광주", 28),
    DAEJEON("대전", 28), ULSAN("울산", 21), SEJONG("세종", 8), GYEONGGI("경기", 265), GANGWON("강원", 30),
    CHUNGBUK("충북", 31), CHUNGNAM("충남", 42), JEONBUK("전북", 34), JEONNAM("전남", 35), GYEONGBUK("경북", 50),
    GYEONGNAM("경남", 63), JEJU("제주", 14);

    private final String label;
    private final int weight;

    /** 0 ≤ u < 1 → 인구 비중대로 고른 지역 */
    public static Region weighted(double u) {
        int point = (int) (u * 1000), acc = 0;
        for (Region r : values()) {
            acc += r.weight;
            if (point < acc) {
                return r;
            }
        }
        return SEOUL;
    }

    /** SQL CASE 식: 0~999 값 식(expr)을 인구 비중대로 지역 코드로 바꾼다 (대량 백필용, weighted 와 같은 규칙) */
    public static String sqlCase(String expr) {
        StringBuilder sb = new StringBuilder("case");
        int acc = 0;
        for (Region r : values()) {
            acc += r.weight;
            sb.append(" when ").append(expr).append(" < ").append(acc).append(" then '").append(r.name()).append("'");
        }
        return sb.append(" else 'SEOUL' end").toString();
    }
}
