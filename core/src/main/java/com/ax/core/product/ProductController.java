package com.ax.core.product;

import com.ax.core.common.NotFoundException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductRepository productRepository;
    private final FundRepository fundRepository;

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

    public record Detail(Summary summary, boolean savings, boolean variable, boolean renewable, String policyDocument,
                         Long minMonthlyContribution, Long maxMonthlyContribution, List<Integer> annuityStartAges,
                         List<FundResponse> funds, List<CoverageResponse> coverages) {
    }

    public record PageResponse(List<Summary> content, int page, int totalPages, long totalElements) {
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
                found.getTotalElements());
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
                p.getAnnuityStartAges(), funds, p.getCoverages().stream().map(CoverageResponse::from).toList());
    }
}
