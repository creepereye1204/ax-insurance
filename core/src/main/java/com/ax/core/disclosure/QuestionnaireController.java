package com.ax.core.disclosure;

import com.ax.core.common.NotFoundException;
import com.ax.core.product.ProductRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/questionnaires")
@RequiredArgsConstructor
public class QuestionnaireController {

    private final QuestionnaireRepository questionnaireRepository;
    private final ProductRepository productRepository;

    public record QuestionResponse(Long id, int seq, String code, String text, int lookbackMonths, boolean detailRequired) {
    }

    public record Response(Long id, String code, int version, String title, boolean simplified,
                           List<QuestionResponse> questions) {
        static Response from(Questionnaire q) {
            return new Response(q.getId(), q.getCode(), q.getVersion(), q.getTitle(), q.isSimplified(), q.getQuestions().stream()
                    .map(x -> new QuestionResponse(x.getId(), x.getSeq(), x.getCode(), x.getText(),
                            x.getLookbackMonths(), x.isDetailRequired()))
                    .toList());
        }
    }

    /** 현재 유효한 질문지. 상품을 주면 상품 심사 유형(표준 / 간편 3-2-5)에 맞는 질문지를 준다. */
    @GetMapping("/current")
    @Transactional(readOnly = true)
    public Response current(@RequestParam(required = false) Long productId) {
        boolean simplified = productId != null && productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("상품", productId)).isSimplifiedUw();
        String code = Questionnaire.codeFor(simplified);
        return questionnaireRepository.findFirstByCodeAndEffectiveFromLessThanEqualOrderByVersionDesc(code, LocalDate.now())
                .map(Response::from)
                .orElseThrow(() -> new NotFoundException("유효한 질문지", code));
    }
}
