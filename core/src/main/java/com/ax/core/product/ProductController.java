package com.ax.core.product;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductRepository productRepository;

    public record CoverageResponse(Long id, String code, String name, CoverageCategory category,
                                   String categoryLabel, long monthlyRatePer10M) {
        static CoverageResponse from(Coverage c) {
            return new CoverageResponse(c.getId(), c.getCode(), c.getName(), c.getCategory(),
                    c.getCategory().getLabel(), c.getMonthlyRatePer10M());
        }
    }

    public record Response(Long id, String code, String name, int maxEntryAge, List<CoverageResponse> coverages) {
        static Response from(Product p) {
            return new Response(p.getId(), p.getCode(), p.getName(), p.getMaxEntryAge(),
                    p.getCoverages().stream().map(CoverageResponse::from).toList());
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Response> list() {
        return productRepository.findAll().stream().map(Response::from).toList();
    }
}
