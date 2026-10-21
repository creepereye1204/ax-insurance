package com.ax.core.corporate;

import com.ax.core.common.BusinessException;
import com.ax.core.common.NotFoundException;
import com.ax.core.product.Product;
import com.ax.core.product.ProductPlan;
import com.ax.core.product.ProductRepository;
import com.ax.core.proposal.PremiumCalculator;
import com.ax.core.proposal.PremiumCalculator.CoverageAmount;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 단체 가입설계: 법인이 고른 하나의 플랜(담보·가입금액)을 종업원 전원에게 적용한다.
 * <ul>
 *   <li>보험료는 종업원 개인의 보험나이·성별 요율로 각각 계산 (개인 설계와 같은 PremiumCalculator — 가입나이·운전 등 제한도 동일)</li>
 *   <li>가입할 수 없는 종업원은 제외하고 사유를 남긴다</li>
 *   <li>가입 인원 5명 이상이어야 단체로 설계할 수 있고, 인원이 많을수록 단체 할인</li>
 * </ul>
 * 보장성 상품만 대상이다 (저축·변액은 개인별 적합성·납입액이 달라 단체 일괄 설계 불가).
 */
@Service
@RequiredArgsConstructor
public class GroupQuoteService {

    public static final int MIN_GROUP_SIZE = 5;

    private final CorporateRepository corporateRepository;
    private final CorporateMemberRepository memberRepository;
    private final ProductRepository productRepository;
    private final GroupProposalRepository proposalRepository;
    private final PremiumCalculator calculator;

    public record Line(CorporateMember member, int insuranceAge, boolean included, long monthlyPremium, String excludedReason) {
    }

    public record GroupQuote(Corporate corporate, Product product, int paymentTerm, List<ProductPlan.Item> items,
                             List<Line> lines, int insuredCount, long grossPremium, int discountPct, long monthlyPremium) {
    }

    /** 단체 할인율: 10명 이상 5%, 50명 이상 10%, 100명 이상 15% (가상 기준) */
    public static int discountPct(int insured) {
        return insured >= 100 ? 15 : insured >= 50 ? 10 : insured >= 10 ? 5 : 0;
    }

    @Transactional(readOnly = true)
    public GroupQuote quote(Long corporateId, Long productId, int paymentTerm, List<ProductPlan.Item> items) {
        Corporate corporate = corporateRepository.findById(corporateId).orElseThrow(() -> new NotFoundException("법인", corporateId));
        Product product = productRepository.findById(productId).orElseThrow(() -> new NotFoundException("상품", productId));
        if (product.getType().isSavings()) {
            throw new BusinessException("단체 설계는 보장성 상품만 가능합니다 (저축·변액은 개인 설계).");
        }
        List<CorporateMember> members = memberRepository.findByCorporateIdOrderById(corporateId);
        List<CoverageAmount> coverages = items.stream().map(i -> new CoverageAmount(i.coverageId(), i.amount())).toList();
        PremiumCalculator.Input input = new PremiumCalculator.Input(paymentTerm, coverages, null, null, null, false);
        LocalDate today = LocalDate.now();

        List<Line> lines = members.stream().map(m -> {
            int age = m.getCustomer().insuranceAgeAt(today);
            try {
                return new Line(m, age, true, calculator.quote(product, m.getCustomer(), input).monthlyPremium(), null);
            } catch (BusinessException e) {
                return new Line(m, age, false, 0, e.getMessage());
            }
        }).toList();

        int insured = (int) lines.stream().filter(Line::included).count();
        if (insured < MIN_GROUP_SIZE) {
            // 설계 자체가 잘못되면(담보·금액·규칙 위반) 전원이 같은 사유로 제외되므로 그 사유를 그대로 알려 준다
            boolean sameReason = insured == 0 && !lines.isEmpty()
                    && lines.stream().map(Line::excludedReason).distinct().count() == 1;
            String common = sameReason ? " — " + lines.get(0).excludedReason() : "";
            throw new BusinessException("단체 설계는 가입 가능한 종업원이 %d명 이상이어야 합니다 (현재 %d명 / 등록 %d명)%s"
                    .formatted(MIN_GROUP_SIZE, insured, lines.size(), common));
        }
        long gross = lines.stream().mapToLong(Line::monthlyPremium).sum();
        int discount = discountPct(insured);
        long net = Math.round(gross * (100 - discount) / 100.0 / 10) * 10;
        return new GroupQuote(corporate, product, paymentTerm, items, lines, insured, gross, discount, net);
    }

    @Transactional
    public GroupProposal issue(Long corporateId, Long productId, int paymentTerm, List<ProductPlan.Item> items) {
        GroupQuote q = quote(corporateId, productId, paymentTerm, items);
        return proposalRepository.save(new GroupProposal("GP" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "%06d".formatted(ThreadLocalRandom.current().nextInt(1_000_000)), q));
    }
}
