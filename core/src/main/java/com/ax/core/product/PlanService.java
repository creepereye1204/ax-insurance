package com.ax.core.product;

import com.ax.core.common.BusinessException;
import com.ax.core.common.NotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 플랜 조회·저장·기본 플랜 생성 */
@Service
@RequiredArgsConstructor
public class PlanService {

    public static final int MAX_CUSTOM_PLANS = 20;

    private final ProductRepository productRepository;
    private final ProductPlanRepository planRepository;
    private final CoverageRuleRepository ruleRepository;

    @Transactional(readOnly = true)
    public List<ProductPlan> plansOf(Long productId) {
        return planRepository.findByProductIdOrderByCustomAscIdAsc(productId);
    }

    /** 설계사 플랜 저장: 담보·가입금액·주계약·특약 연관 규칙을 보험료 계산과 같은 기준으로 검사한다. */
    @Transactional
    public ProductPlan saveCustom(Long productId, String name, List<ProductPlan.Item> items) {
        Product product = productRepository.findById(productId).orElseThrow(() -> new NotFoundException("상품", productId));
        if (name == null || name.isBlank() || name.length() > 50) {
            throw new BusinessException("플랜 이름을 1~50자로 입력하세요.");
        }
        List<ProductPlan> existing = plansOf(productId);
        if (existing.stream().filter(ProductPlan::isCustom).count() >= MAX_CUSTOM_PLANS) {
            throw new BusinessException("저장한 플랜은 상품당 " + MAX_CUSTOM_PLANS + "개까지입니다.");
        }
        if (existing.stream().anyMatch(p -> p.getName().equals(name.trim()))) {
            throw new BusinessException("같은 이름의 플랜이 있습니다: " + name.trim());
        }
        List<String> problems = check(product, items, ruleRepository.specsOf(productId));
        if (!problems.isEmpty()) {
            throw new BusinessException(String.join(" / ", problems));
        }
        return planRepository.save(new ProductPlan(product, name.trim(), "설계사 저장 플랜", true, items));
    }

    @Transactional
    public void deleteCustom(Long productId, Long planId) {
        ProductPlan plan = planRepository.findById(planId)
                .filter(p -> p.getProduct().getId().equals(productId))
                .orElseThrow(() -> new NotFoundException("플랜", planId));
        if (!plan.isCustom()) {
            throw new BusinessException("상품 기본 플랜은 삭제할 수 없습니다.");
        }
        planRepository.delete(plan);
    }

    /** 플랜 검사 (문제 목록, 비어 있으면 정상) */
    public static List<String> check(Product product, List<ProductPlan.Item> items, List<RiderRules.Rule> rules) {
        Map<Long, Coverage> byId = product.getCoverages().stream().collect(Collectors.toMap(Coverage::getId, Function.identity()));
        Map<Long, Long> amounts = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        for (ProductPlan.Item item : items == null ? List.<ProductPlan.Item>of() : items) {
            Coverage c = byId.get(item.coverageId());
            if (c == null || c.isAccumulation()) {
                problems.add("플랜에 넣을 수 없는 담보입니다: " + item.coverageId());
            } else if (!c.isValidAmount(item.amount())) {
                problems.add("%s 가입금액은 %,d~%,d원(%,d원 단위)이어야 합니다."
                        .formatted(c.getName(), c.getMinAmount(), c.getMaxAmount(), c.getAmountStep()));
            } else {
                amounts.put(c.getId(), item.amount());
            }
        }
        product.getCoverages().stream().filter(c -> c.isRequired() && !c.isAccumulation() && !amounts.containsKey(c.getId()))
                .forEach(c -> problems.add("주계약 '" + c.getName() + "'이(가) 빠졌습니다."));
        problems.addAll(RiderRules.violations(rules, amounts));
        return problems;
    }

    /**
     * 기본 플랜: 실속형(주계약만, 기본금액의 절반) / 표준형(전 담보 기본금액) / 고급형(전 담보 기본금액 2배).
     * 가입금액은 담보의 한도·단위에 맞추고, 규칙을 어기거나 앞 플랜과 같으면 만들지 않는다.
     */
    public static List<ProductPlan> defaultPlans(Product product, List<RiderRules.Rule> rules) {
        List<Coverage> covs = product.getCoverages().stream().filter(c -> !c.isAccumulation()).toList();
        if (covs.isEmpty()) {
            return List.of();
        }
        record Draft(String name, String description, List<ProductPlan.Item> items) {
        }
        List<Draft> drafts = List.of(
                new Draft("실속형", "주계약 중심, 보험료 부담 최소", covs.stream().filter(Coverage::isRequired)
                        .map(c -> new ProductPlan.Item(c.getId(), fit(c, c.getDefaultAmount() / 2))).toList()),
                new Draft("표준형", "전 담보 기본 가입금액", covs.stream()
                        .map(c -> new ProductPlan.Item(c.getId(), c.getDefaultAmount())).toList()),
                new Draft("고급형", "전 담보 기본 가입금액의 2배", covs.stream()
                        .map(c -> new ProductPlan.Item(c.getId(), fit(c, c.getDefaultAmount() * 2))).toList()));
        List<ProductPlan> plans = new ArrayList<>();
        for (Draft d : drafts) {
            boolean duplicate = plans.stream().anyMatch(p -> p.getItems().equals(d.items()));
            if (!duplicate && check(product, d.items(), rules).isEmpty()) {
                plans.add(new ProductPlan(product, d.name(), d.description(), false, d.items()));
            }
        }
        return plans;
    }

    /** 한도 안으로 자르고 가입 단위에 맞춘다 */
    static long fit(Coverage c, long amount) {
        long clamped = Math.max(c.getMinAmount(), Math.min(c.getMaxAmount(), amount));
        return c.getMinAmount() + (clamped - c.getMinAmount()) / c.getAmountStep() * c.getAmountStep();
    }
}
