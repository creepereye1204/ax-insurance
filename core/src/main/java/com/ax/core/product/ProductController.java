package com.ax.core.product;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductRepository productRepository;

    public record Response(Long id, String code, String name, String policyDocument) {
        static Response from(Product p) {
            return new Response(p.getId(), p.getCode(), p.getName(), p.getPolicyDocument());
        }
    }

    @GetMapping
    public List<Response> list() {
        return productRepository.findAll().stream().map(Response::from).toList();
    }
}
