package com.ax.core.customer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기동 시 고객 유형(가망/계약)을 계약 데이터에 맞춘다.
 * 평소에는 계약 성립·철회·해지 시점에 갱신하지만, 컬럼 추가 전 데이터와 SQL 로 적재한 대량 계약은 이 동기화로 맞춘다.
 * 대량 적재(@Order 2) 다음에 실행한다.
 */
@Slf4j
@Order(3)
@Component
@RequiredArgsConstructor
public class CustomerStatusSync implements ApplicationRunner {

    private final CustomerRepository customerRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        long started = System.currentTimeMillis();
        int changed = customerRepository.syncStatus();
        if (changed > 0) {
            log.info("고객 유형 동기화: {}명 갱신 ({}ms)", changed, System.currentTimeMillis() - started);
        }
    }
}
