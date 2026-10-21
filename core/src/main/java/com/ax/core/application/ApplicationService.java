package com.ax.core.application;

import com.ax.core.application.DisclosureForm.AnswerInput;
import com.ax.core.common.BusinessException;
import com.ax.core.common.NotFoundException;
import com.ax.core.customer.Customer;
import com.ax.core.disclosure.Questionnaire;
import com.ax.core.disclosure.QuestionnaireRepository;
import com.ax.core.proposal.Proposal;
import com.ax.core.proposal.ProposalCoverage;
import com.ax.core.proposal.ProposalRepository;
import com.ax.core.uw.UwCase;
import com.ax.core.uw.UwService;
import com.ax.core.uw.engine.UwResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
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
    private final DisclosureForm disclosureForm;
    private final UwService uwService;

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

    /** 전산심사 사전 실행: 고지까지 입력된 청약 초안으로 심사 결과를 미리 본다 (저장 안 함) */
    @Transactional(readOnly = true)
    public UwResult simulate(Long proposalId, Long questionnaireId, List<AnswerInput> answers) {
        return uwService.simulate(draft(proposalId, questionnaireId, answers, LocalDateTime.now()).application());
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
        disclosureForm.checkFor(questionnaire, proposal.getProduct());
        Customer customer = proposal.getCustomer();
        if (!proposal.getProduct().acceptsAge(customer.insuranceAgeAt(now.toLocalDate()))) {
            throw new BusinessException("설계 이후 나이가 바뀌어 가입 가능 나이를 벗어났습니다. 다시 설계하세요.");
        }

        Application app = new Application(newApplicationNo(), proposal, questionnaire, now);
        for (ProposalCoverage pc : proposal.getCoverages()) {
            long base = pc.getCoverage().isAccumulation() && proposal.getMonthlyContribution() != null
                    ? proposal.getMonthlyContribution() : pc.getMonthlyPremium();
            app.addCoverage(new ApplicationCoverage(pc.getCoverage(), pc.getInsuredAmount(), base));
        }
        disclosureForm.build(questionnaire, answers, now.toLocalDate()).forEach(app::addAnswer);
        List<Agreement> required = Agreement.requiredFor(customer, proposal.getProduct(), now.toLocalDate());
        return new Draft(app, required, hash(app));
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
    public Proposal proposalOf(Long proposalId) {
        return proposalRepository.findById(proposalId).orElseThrow(() -> new NotFoundException("설계서", proposalId));
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
