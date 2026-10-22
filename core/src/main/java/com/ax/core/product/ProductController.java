package com.ax.core.product;

import com.ax.core.common.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductRepository productRepository;
    private final FundRepository fundRepository;
    private final CoverageRuleRepository ruleRepository;
    private final PlanService planService;
    private final ProductPlanRepository planRepository;

    public record TypeResponse(String code, String label, boolean savings, boolean variable) {
    }

    public record CoverageResponse(Long id, String code, String name, CoverageCategory category, String categoryLabel,
                                   boolean required, boolean accumulation, long minAmount, long maxAmount,
                                   long defaultAmount, long amountStep) {
        static CoverageResponse from(Coverage c) {
            return new CoverageResponse(c.getId(), c.getCode(), c.getName(), c.getCategory(), c.getCategory().getLabel(),
                    c.isRequired(), c.isAccumulation(), c.getMinAmount(), c.getMaxAmount(), c.getDefaultAmount(),
                    c.getAmountStep());
        }
    }

    public record FundResponse(String code, String name, String assetClass, int riskGrade, int annualFeeBp) {
    }

    public record Summary(Long id, String code, String name, ProductType type, String typeLabel, int minEntryAge,
                          int maxEntryAge, List<Integer> paymentTerms, String insurancePeriod, boolean simplifiedUw,
                          SaleStatus saleStatus, LocalDate launchedOn, int coverageCount) {
        static Summary from(Product p) {
            return new Summary(p.getId(), p.getCode(), p.getName(), p.getType(), p.getType().getLabel(),
                    p.getMinEntryAge(), p.getMaxEntryAge(), p.getPaymentTerms(), p.getInsurancePeriod(),
                    p.isSimplifiedUw(), p.getSaleStatus(), p.getLaunchedOn(), p.getCoverages().size());
        }
    }

    public record RuleResponse(CoverageRuleType type, Long coverageId, Long targetId, Integer ratioPct, String text) {
        static RuleResponse from(RiderRules.Rule r) {
            return new RuleResponse(r.type(), r.coverageId(), r.targetId(), r.ratioPct(), r.text());
        }
    }

    public record PlanResponse(Long id, String name, String description, boolean custom, List<ProductPlan.Item> items) {
        static PlanResponse from(ProductPlan p) {
            return new PlanResponse(p.getId(), p.getName(), p.getDescription(), p.isCustom(), p.getItems());
        }
    }

    public record PlanRequest(@NotBlank String name, @NotEmpty List<ProductPlan.Item> items) {
    }

    public record Detail(Summary summary, boolean savings, boolean variable, boolean renewable, String policyDocument,
                         Long minMonthlyContribution, Long maxMonthlyContribution, List<Integer> annuityStartAges,
                         List<FundResponse> funds, List<CoverageResponse> coverages, List<RuleResponse> rules,
                         List<PlanResponse> plans) {
    }

    /** 목록 카드용 플랜 요약 (담보 구성은 상세에서) */
    public record PlanTag(Long id, String name, boolean custom, int coverageCount) {
    }

    public record PageResponse(List<Summary> content, int page, int totalPages, long totalElements,
                               Map<Long, List<PlanTag>> plans) {
    }

    @GetMapping("/types")
    public List<TypeResponse> types() {
        return Arrays.stream(ProductType.values())
                .map(t -> new TypeResponse(t.name(), t.getLabel(), t.isSavings(), t.isVariable())).toList();
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse list(@RequestParam(required = false) ProductType type,
                             @RequestParam(defaultValue = "") String q,
                             @RequestParam(defaultValue = "true") boolean onSaleOnly,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "20") int size) {
        Page<Product> found = productRepository.search(type, q.trim(), onSaleOnly,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("type", "name")));
        return new PageResponse(found.map(Summary::from).getContent(), found.getNumber(), found.getTotalPages(),
                found.getTotalElements(), planTags(found.map(Product::getId).getContent()));
    }

    /** 페이지에 담긴 상품들의 플랜을 한 번에 조회 (상품마다 조회하지 않는다) */
    private Map<Long, List<PlanTag>> planTags(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return planRepository.findByProductIdInOrderByCustomAscIdAsc(productIds).stream()
                .collect(Collectors.groupingBy(pl -> pl.getProduct().getId(),
                        Collectors.mapping(pl -> new PlanTag(pl.getId(), pl.getName(), pl.isCustom(), pl.getItems().size()),
                                Collectors.toList())));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public Detail get(@PathVariable Long id) {
        Product p = productRepository.findById(id).orElseThrow(() -> new NotFoundException("상품", id));
        List<FundResponse> funds = List.of();
        if (p.getFundCodes() != null) {
            Map<String, Fund> byCode = fundRepository.findAllById(p.getFundCodes()).stream()
                    .collect(Collectors.toMap(Fund::getCode, Function.identity()));
            funds = p.getFundCodes().stream().map(byCode::get)
                    .map(f -> new FundResponse(f.getCode(), f.getName(), f.getAssetClass(), f.getRiskGrade(), f.getAnnualFeeBp()))
                    .toList();
        }
        return new Detail(Summary.from(p), p.getType().isSavings(), p.getType().isVariable(), p.isRenewable(),
                p.getPolicyDocument(), p.getMinMonthlyContribution(), p.getMaxMonthlyContribution(),
                p.getAnnuityStartAges(), funds, p.getCoverages().stream().map(CoverageResponse::from).toList(),
                ruleRepository.specsOf(id).stream().map(RuleResponse::from).toList(),
                planService.plansOf(id).stream().map(PlanResponse::from).toList());
    }

    /** 설계사 플랜 저장 */
    @PostMapping("/{id}/plans")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanResponse savePlan(@PathVariable Long id, @Valid @RequestBody PlanRequest req) {
        return PlanResponse.from(planService.saveCustom(id, req.name(), req.items()));
    }

    @DeleteMapping("/{id}/plans/{planId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePlan(@PathVariable Long id, @PathVariable Long planId) {
        planService.deleteCustom(id, planId);
    }
}
