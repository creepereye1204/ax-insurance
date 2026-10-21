package com.ax.core.proposal;

import com.ax.core.common.NotFoundException;
import com.ax.core.customer.CustomerRepository;
import com.ax.core.product.ProductRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 가입설계 임시저장 (고객·상품당 1건, 덮어쓰기) */
@RestController
@RequestMapping("/proposal-drafts")
@RequiredArgsConstructor
public class ProposalDraftController {

    private final ProposalDraftRepository draftRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    public record SaveRequest(@NotNull Long customerId, @NotNull Long productId, @NotNull ProposalDraft.Payload payload) {
    }

    public record Response(Long id, Long customerId, String customerName, Long productId, String productName,
                           ProposalDraft.Payload payload, LocalDateTime savedAt) {
        static Response from(ProposalDraft d) {
            return new Response(d.getId(), d.getCustomer().getId(), d.getCustomer().getName(), d.getProduct().getId(),
                    d.getProduct().getName(), d.getPayload(), d.getSavedAt());
        }
    }

    @PutMapping
    @Transactional
    public Response save(@Valid @RequestBody SaveRequest req) {
        ProposalDraft draft = draftRepository.findByCustomerIdAndProductId(req.customerId(), req.productId())
                .map(d -> {
                    d.overwrite(req.payload());
                    return d;
                })
                .orElseGet(() -> draftRepository.save(new ProposalDraft(
                        customerRepository.findById(req.customerId()).orElseThrow(() -> new NotFoundException("고객", req.customerId())),
                        productRepository.findById(req.productId()).orElseThrow(() -> new NotFoundException("상품", req.productId())),
                        req.payload())));
        return Response.from(draft);
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Response> list(@RequestParam(required = false) Long customerId,
                               @RequestParam(required = false) Long productId) {
        if (customerId != null && productId != null) {
            return draftRepository.findByCustomerIdAndProductId(customerId, productId).map(Response::from).stream().toList();
        }
        return (customerId == null ? draftRepository.findTop50ByOrderBySavedAtDesc()
                : draftRepository.findByCustomerIdOrderBySavedAtDesc(customerId)).stream().map(Response::from).toList();
    }

    @DeleteMapping("/{id}")
    @Transactional
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        draftRepository.deleteById(id);
    }
}
