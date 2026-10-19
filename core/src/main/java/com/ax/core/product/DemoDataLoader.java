package com.ax.core.product;

import com.ax.core.contract.Contract;
import com.ax.core.contract.ContractRepository;
import com.ax.core.customer.Customer;
import com.ax.core.customer.CustomerRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 데모용 샘플 데이터. 상품은 AI 서비스의 data/policies/샘플_실손의료비보험.txt 와 짝을 이룬다.
 * 공개 데모에서는 고객/계약 등록 API 를 막아 두므로, 청구 데모는 이 계약을 사용한다.
 */
@Component
@RequiredArgsConstructor
public class DemoDataLoader implements ApplicationRunner {

    private static final String DEMO_PRODUCT_CODE = "SAMPLE-MED-01";

    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final ContractRepository contractRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (productRepository.existsByCode(DEMO_PRODUCT_CODE)) {
            return;
        }
        Product product = productRepository.save(
                new Product(DEMO_PRODUCT_CODE, "샘플 실손의료비보험", "샘플_실손의료비보험"));
        Customer customer = customerRepository.save(new Customer("데모고객", LocalDate.of(1998, 1, 1), null));
        contractRepository.save(new Contract("CT-DEMO-0001", customer, product,
                LocalDate.of(2026, 1, 1), LocalDate.of(2036, 12, 31), 25_000));
    }
}
