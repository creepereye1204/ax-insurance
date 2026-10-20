package com.ax.core.proposal;

import com.ax.core.common.BusinessException;
import com.ax.core.common.NotFoundException;
import com.ax.core.customer.Customer;
import com.ax.core.customer.CustomerRepository;
import com.ax.core.product.Product;
import com.ax.core.product.ProductRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProposalService {

    private final PremiumCalculator calculator;
    private final ProposalRepository proposalRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    /** 보험료 계산만 (저장하지 않음) */
    @Transactional(readOnly = true)
    public PremiumCalculator.Quote quote(Long customerId, Long productId, PremiumCalculator.Input input) {
        return calculator.quote(product(productId), customer(customerId), input);
    }

    /** 설계서 발행: 부적합 상품은 고객 확인이 있어야 발행된다 */
    @Transactional
    public Proposal issue(Long customerId, Long productId, PremiumCalculator.Input input) {
        PremiumCalculator.Quote q = calculator.quote(product(productId), customer(customerId), input);
        if (q.suitability() == Suitability.UNSUITABLE) {
            throw new BusinessException("부적합: " + String.join(" ", q.suitabilityIssues())
                    + " 고객이 부적합 사실을 확인한 경우에만 설계서를 발행할 수 있습니다.");
        }
        return proposalRepository.save(new Proposal(newProposalNo(), q));
    }

    @Transactional(readOnly = true)
    public Proposal get(Long id) {
        return proposalRepository.findById(id).orElseThrow(() -> new NotFoundException("설계서", id));
    }

    @Transactional(readOnly = true)
    public List<Proposal> list(Long customerId) {
        return customerId == null
                ? proposalRepository.findTop100ByOrderByIdDesc()
                : proposalRepository.findByCustomerIdOrderByIdDesc(customerId);
    }

    private Customer customer(Long id) {
        return customerRepository.findById(id).orElseThrow(() -> new NotFoundException("고객", id));
    }

    private Product product(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new NotFoundException("상품", id));
    }

    private static String newProposalNo() {
        return "PR" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }
}
