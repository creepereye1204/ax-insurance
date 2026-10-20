package com.ax.core.application;

import com.ax.core.common.BusinessException;
import com.ax.core.common.NotFoundException;
import com.ax.core.common.TreatmentType;
import com.ax.core.customer.Customer;
import com.ax.core.disclosure.Question;
import com.ax.core.disclosure.Questionnaire;
import com.ax.core.disclosure.QuestionnaireRepository;
import com.ax.core.kcd.KcdRepository;
import com.ax.core.proposal.Proposal;
import com.ax.core.proposal.ProposalCoverage;
import com.ax.core.proposal.ProposalRepository;
import com.ax.core.uw.UwCase;
import com.ax.core.uw.UwService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 청약: 가입설계서 → 알릴의무 고지 → 청약서 확인 → 전자서명 → 제출 → 전산심사.
 * 미리보기(preview)와 제출(submit)은 같은 검증·같은 청약서 내용을 사용한다.
 */
@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ProposalRepository proposalRepository;
    private final QuestionnaireRepository questionnaireRepository;
    private final KcdRepository kcdRepository;
    private final UwService uwService;

    public record DetailInput(String kcdCode, LocalDate diagnosedOn, TreatmentType treatment, int treatmentDays, boolean cured) {
    }

    public record AnswerInput(Long questionId, boolean answer, List<DetailInput> details) {
    }

    public record Signature(String signerName, List<Agreement> agreements) {
    }

    public record Draft(Application application, List<Agreement> requiredAgreements, String documentHash) {
    }

    public record Submitted(Application application, UwCase uwCase) {
    }

    /** 청약서 미리보기: 저장하지 않고 검증된 청약서 내용과 필수 확인 항목을 돌려준다. */
    @Transactional(readOnly = true)
    public Draft preview(Long proposalId, Long questionnaireId, List<AnswerInput> answers) {
        return draft(proposalId, questionnaireId, answers, LocalDateTime.now());
    }

    /** 서명 후 제출: 서명 검증 → 저장 → 설계서 전환 → 전산심사 */
    @Transactional
    public Submitted submit(Long proposalId, Long questionnaireId, List<AnswerInput> answers, Signature signature) {
        LocalDateTime now = LocalDateTime.now();
        Draft draft = draft(proposalId, questionnaireId, answers, now);
        Application app = draft.application();
        Customer customer = app.getCustomer();

        if (signature == null || signature.signerName() == null || !signature.signerName().trim().equals(customer.getName())) {
            throw new BusinessException("서명자 이름이 계약자 이름(" + customer.getName() + ")과 일치하지 않습니다.");
        }
        List<Agreement> missing = draft.requiredAgreements().stream()
                .filter(a -> signature.agreements() == null || !signature.agreements().contains(a)).toList();
        if (!missing.isEmpty()) {
            throw new BusinessException("확인하지 않은 필수 항목이 있습니다: "
                    + missing.stream().map(Agreement::getText).collect(Collectors.joining(" / ")));
        }
        app.sign(customer.getName(), draft.requiredAgreements(), draft.documentHash(), now);
        app.getProposal().convert(now.toLocalDate());
        applicationRepository.save(app);
        return new Submitted(app, uwService.autoUnderwrite(app));
    }

    private Draft draft(Long proposalId, Long questionnaireId, List<AnswerInput> answers, LocalDateTime now) {
        Proposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new NotFoundException("설계서", proposalId));
        if (proposal.getStatus() != com.ax.core.proposal.ProposalStatus.ISSUED) {
            throw new BusinessException("이미 청약으로 전환된 설계서입니다.");
        }
        if (proposal.isExpired(now.toLocalDate())) {
            throw new BusinessException("유효기간(" + proposal.getValidUntil() + ")이 지난 설계서입니다. 다시 설계하세요.");
        }
        Questionnaire questionnaire = questionnaireRepository.findById(questionnaireId)
                .orElseThrow(() -> new NotFoundException("질문지", questionnaireId));
        Customer customer = proposal.getCustomer();
        if (!proposal.getProduct().acceptsAge(customer.ageAt(now.toLocalDate()))) {
            throw new BusinessException("설계 이후 나이가 바뀌어 가입 가능 나이를 벗어났습니다. 다시 설계하세요.");
        }

        Application app = new Application(newApplicationNo(), proposal, questionnaire, now);
        for (ProposalCoverage pc : proposal.getCoverages()) {
            long base = pc.getCoverage().isAccumulation() && proposal.getMonthlyContribution() != null
                    ? proposal.getMonthlyContribution() : pc.getMonthlyPremium();
            app.addCoverage(new ApplicationCoverage(pc.getCoverage(), pc.getInsuredAmount(), base));
        }
        addAnswers(app, questionnaire, answers, now.toLocalDate());
        List<Agreement> required = Agreement.requiredFor(customer, proposal.getProduct(), now.toLocalDate());
        return new Draft(app, required, hash(app));
    }

    /** 모든 문항에 답해야 하고, "예"인 상세 필요 문항은 상세가 1건 이상, 진단일은 문항의 조회기간 안이어야 한다. */
    private void addAnswers(Application app, Questionnaire questionnaire, List<AnswerInput> inputs, LocalDate today) {
        Map<Long, AnswerInput> byQuestion = inputs == null ? Map.of()
                : inputs.stream().collect(Collectors.toMap(AnswerInput::questionId, Function.identity(), (a, b) -> a));

        for (Question q : questionnaire.getQuestions()) {
            AnswerInput in = byQuestion.get(q.getId());
            if (in == null) {
                throw new BusinessException("고지 문항 " + q.getCode() + "에 답하지 않았습니다.");
            }
            DisclosureAnswer answer = new DisclosureAnswer(q, in.answer());
            List<DetailInput> details = in.details() == null ? List.of() : in.details();
            if (in.answer() && q.isDetailRequired() && details.isEmpty()) {
                throw new BusinessException(q.getCode() + " '예' 응답에는 질병·치료 상세를 1건 이상 입력해야 합니다.");
            }
            if (in.answer()) {
                for (DetailInput d : details) {
                    validateDetail(q, d, today);
                    answer.addDetail(new DisclosureDetail(d.kcdCode().trim().toUpperCase(), d.diagnosedOn(),
                            d.treatment(), d.treatmentDays(), d.cured()));
                }
            }
            app.addAnswer(answer);
        }
    }

    private void validateDetail(Question q, DetailInput d, LocalDate today) {
        if (d.kcdCode() == null || d.kcdCode().isBlank()) {
            throw new BusinessException(q.getCode() + ": 질병코드(KCD)를 입력하세요.");
        }
        if (!kcdRepository.existsById(d.kcdCode().trim().toUpperCase())) {
            throw new BusinessException(q.getCode() + ": 등록되지 않은 질병코드입니다 (" + d.kcdCode() + ")");
        }
        if (d.diagnosedOn() == null || d.diagnosedOn().isAfter(today)) {
            throw new BusinessException(q.getCode() + ": 진단일이 올바르지 않습니다.");
        }
        if (q.getLookbackMonths() > 0 && d.diagnosedOn().isBefore(today.minusMonths(q.getLookbackMonths()))) {
            throw new BusinessException(q.getCode() + ": 진단일이 고지 대상 기간(최근 " + q.getLookbackMonths() + "개월)을 벗어납니다.");
        }
        if (d.treatment() == null || d.treatmentDays() < 0) {
            throw new BusinessException(q.getCode() + ": 치료 유형·일수를 확인하세요.");
        }
    }

    /** 청약서 핵심 내용(설계·보험료·고지)의 해시. 서명 시점 내용을 증빙한다. */
    private static String hash(Application app) {
        StringBuilder sb = new StringBuilder()
                .append(app.getProposal().getProposalNo()).append('|')
                .append(app.getCustomer().getId()).append('|')
                .append(app.getProduct().getCode()).append('|')
                .append(app.getPaymentTerm()).append('|')
                .append(app.getMonthlyPremium()).append('|')
                .append(app.getQuestionnaire().getCode()).append('v').append(app.getQuestionnaire().getVersion());
        app.getCoverages().forEach(c -> sb.append('|').append(c.getCoverage().getCode()).append(':').append(c.getInsuredAmount()));
        app.getAnswers().forEach(a -> {
            sb.append('|').append(a.getQuestion().getCode()).append('=').append(a.isAnswer());
            a.getDetails().forEach(d -> sb.append(',').append(d.getKcdCode()).append('@').append(d.getDiagnosedOn())
                    .append(':').append(d.getTreatment()).append(':').append(d.getTreatmentDays()).append(':').append(d.isCured()));
        });
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional(readOnly = true)
    public Application get(Long id) {
        return applicationRepository.findById(id).orElseThrow(() -> new NotFoundException("청약", id));
    }

    @Transactional(readOnly = true)
    public List<Application> list(Long customerId) {
        return customerId == null
                ? applicationRepository.findTop100ByOrderByIdDesc()
                : applicationRepository.findByCustomerIdOrderByIdDesc(customerId);
    }

    private static String newApplicationNo() {
        return "AP" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }
}
