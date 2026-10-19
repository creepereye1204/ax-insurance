package com.ax.core.disclosure;

import com.ax.core.common.NotFoundException;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/questionnaires")
@RequiredArgsConstructor
public class QuestionnaireController {

    public static final String STANDARD_CODE = "STD-DISCLOSURE";

    private final QuestionnaireRepository questionnaireRepository;

    public record QuestionResponse(Long id, int seq, String code, String text, int lookbackMonths, boolean detailRequired) {
    }

    public record Response(Long id, String code, int version, String title, List<QuestionResponse> questions) {
        static Response from(Questionnaire q) {
            return new Response(q.getId(), q.getCode(), q.getVersion(), q.getTitle(), q.getQuestions().stream()
                    .map(x -> new QuestionResponse(x.getId(), x.getSeq(), x.getCode(), x.getText(),
                            x.getLookbackMonths(), x.isDetailRequired()))
                    .toList());
        }
    }

    @GetMapping("/current")
    @Transactional(readOnly = true)
    public Response current() {
        return questionnaireRepository
                .findFirstByCodeAndEffectiveFromLessThanEqualOrderByVersionDesc(STANDARD_CODE, LocalDate.now())
                .map(Response::from)
                .orElseThrow(() -> new NotFoundException("유효한 질문지", STANDARD_CODE));
    }
}
