package com.ax.core.contract;

import com.ax.core.common.BusinessException;
import com.ax.core.common.NotFoundException;
import com.ax.core.customer.Customer;
import com.ax.core.customer.CustomerRepository;
import com.ax.core.product.Product;
import com.ax.core.product.ProductRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContractService {

    private final ContractRepository contractRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    @Transactional
    public Contract create(Long customerId, Long productId, LocalDate startDate, LocalDate endDate, long monthlyPremium) {
        if (!endDate.isAfter(startDate)) {
            throw new BusinessException("보험 종료일은 시작일 이후여야 합니다.");
        }
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new NotFoundException("고객", customerId));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("상품", productId));
        return contractRepository.save(
                new Contract(newContractNo(), customer, product, startDate, endDate, monthlyPremium));
    }

    @Transactional(readOnly = true)
    public Contract get(Long id) {
        return contractRepository.findById(id).orElseThrow(() -> new NotFoundException("계약", id));
    }

    @Transactional
    public Contract terminate(Long id) {
        Contract contract = get(id);
        contract.terminate();
        return contract;
    }

    private String newContractNo() {
        return "CT" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }
}
