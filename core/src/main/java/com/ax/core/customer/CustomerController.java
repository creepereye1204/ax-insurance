package com.ax.core.customer;

import com.ax.core.application.ApplicationController;
import com.ax.core.application.ApplicationRepository;
import com.ax.core.claim.ClaimRepository;
import com.ax.core.common.NotFoundException;
import com.ax.core.contract.ContractController;
import com.ax.core.contract.ContractRepository;
import com.ax.core.external.ExternalMedicalRecordRepository;
import com.ax.core.proposal.ProposalController;
import com.ax.core.proposal.ProposalRepository;
import com.ax.core.uw.UwCaseRepository;
import com.ax.core.uw.UwStage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerRepository customerRepository;
    private final ConsentRepository consentRepository;
    private final ContractRepository contractRepository;
    private final ApplicationRepository applicationRepository;
    private final ClaimRepository claimRepository;
    private final UwCaseRepository uwCaseRepository;
    private final ExternalMedicalRecordRepository externalRecords;
    private final ProposalRepository proposalRepository;

    private static final String PHONE = "^01[016789]-\\d{3,4}-\\d{4}$";

    public record CreateRequest(@NotBlank @Size(max = 50) String name, @NotNull @Past LocalDate birthDate,
                                @NotNull Gender gender, @Pattern(regexp = PHONE, message = "010-0000-0000 형식") String phone,
                                @NotNull ConsumerType consumerType, @NotBlank @Size(max = 50) String occupation,
                                @Min(1) @Max(3) int jobRiskGrade) {
    }

    public record UpdateRequest(@NotBlank @Size(max = 50) String name,
                                @Pattern(regexp = PHONE, message = "010-0000-0000 형식") String phone,
                                @NotNull ConsumerType consumerType, @NotBlank @Size(max = 50) String occupation,
                                @Min(1) @Max(3) int jobRiskGrade) {
    }

    /** 투자성향 진단: 5문항 각 1~5점 */
    public record ProfileRequest(@NotNull @Size(min = 5, max = 5) List<@Min(1) @Max(5) Integer> scores) {
    }

    public record ConsentRequest(@NotNull ConsentType type) {
    }

    public record Response(Long id, String name, LocalDate birthDate, int age, int insuranceAge, LocalDate nextAgeUpDate,
                           Gender gender, String phone,
                           ConsumerType consumerType, String consumerTypeLabel, String occupation, int jobRiskGrade,
                           InvestmentProfile investmentProfile, String investmentProfileLabel, LocalDateTime profiledAt,
                           boolean protectedSenior, LocalDateTime createdAt) {
        static Response from(Customer c) {
            LocalDate today = LocalDate.now();
            return new Response(c.getId(), c.getName(), c.getBirthDate(), c.ageAt(today), c.insuranceAgeAt(today),
                    c.nextAgeUpDate(today), c.getGender(),
                    c.getPhone(), c.getConsumerType(), c.getConsumerType().getLabel(), c.getOccupation(),
                    c.getJobRiskGrade(), c.getInvestmentProfile(),
                    c.getInvestmentProfile() == null ? null : c.getInvestmentProfile().getLabel(), c.getProfiledAt(),
                    c.isProtectedSenior(today), c.getCreatedAt());
        }
    }

    public record PageResponse(List<Response> content, int page, int totalPages, long totalElements) {
    }

    public record ConsentResponse(Long id, ConsentType type, String label, LocalDateTime agreedAt,
                                  LocalDateTime expiresAt, boolean valid) {
        static ConsentResponse from(Consent c) {
            return new ConsentResponse(c.getId(), c.getType(), c.getType().getLabel(), c.getAgreedAt(), c.getExpiresAt(),
                    c.isValidAt(LocalDateTime.now()));
        }
    }

    public record ClaimSummary(Long id, String claimNo, String contractNo, LocalDate treatmentDate, String diagnosis,
                               String kcdCode, long claimedAmount, String status) {
    }

    public record PreUwSummary(Long caseId, LocalDateTime createdAt, String productName, List<String> outcomes) {
    }

    public record ExternalRecord(String institution, String kcdCode, LocalDate diagnosedOn, String treatment, boolean cured) {
    }

    public record Detail(Response customer, List<ConsentResponse> consents,
                         List<ProposalController.Summary> proposals,
                         List<ContractController.Response> contracts,
                         List<ApplicationController.Summary> applications,
                         List<ClaimSummary> claims, List<PreUwSummary> preUwCases,
                         List<ExternalRecord> externalRecords) {
    }

    @GetMapping
    public PageResponse list(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page) {
        Page<Customer> found = customerRepository.search(q.trim(),
                PageRequest.of(Math.max(page, 0), 20, Sort.by(Sort.Direction.DESC, "id")));
        return new PageResponse(found.map(Response::from).getContent(), found.getNumber(), found.getTotalPages(),
                found.getTotalElements());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@Valid @RequestBody CreateRequest req) {
        return Response.from(customerRepository.save(new Customer(req.name().trim(), req.birthDate(), req.gender(),
                req.phone(), req.consumerType(), req.occupation().trim(), req.jobRiskGrade())));
    }

    @PutMapping("/{id}")
    @Transactional
    public Response update(@PathVariable Long id, @Valid @RequestBody UpdateRequest req) {
        Customer c = find(id);
        c.update(req.name().trim(), req.phone(), req.occupation().trim(), req.jobRiskGrade(), req.consumerType());
        return Response.from(c);
    }

    @PostMapping("/{id}/investment-profile")
    @Transactional
    public Response assessProfile(@PathVariable Long id, @Valid @RequestBody ProfileRequest req) {
        Customer c = find(id);
        c.assessInvestmentProfile(InvestmentProfile.fromScore(req.scores().stream().mapToInt(Integer::intValue).sum()));
        return Response.from(c);
    }

    /** 고객 상세: 기본정보 + 동의 + 계약 + 청약 + 청구 + 선심사 이력 (성능 개선 대상 — 설계 문서 12절) */
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public Detail get(@PathVariable Long id) {
        Customer c = find(id);
        return new Detail(
                Response.from(c),
                consentRepository.findByCustomerIdOrderByIdDesc(id).stream().map(ConsentResponse::from).toList(),
                proposalRepository.findByCustomerIdOrderByIdDesc(id).stream().map(ProposalController.Summary::from).toList(),
                contractRepository.findByCustomerIdOrderByIdDesc(id).stream().map(ContractController.Response::from).toList(),
                applicationRepository.findByCustomerIdOrderByIdDesc(id).stream().map(ApplicationController.Summary::from).toList(),
                claimRepository.findByContractCustomerIdOrderByIdDesc(id).stream()
                        .map(x -> new ClaimSummary(x.getId(), x.getClaimNo(), x.getContract().getContractNo(),
                                x.getTreatmentDate(), x.getDiagnosis(), x.getKcdCode(), x.getClaimedAmount(), x.getStatus().name()))
                        .toList(),
                uwCaseRepository.findByCustomerIdOrderByIdDesc(id).stream()
                        .filter(u -> u.getStage() == UwStage.PRE_UW)
                        .map(u -> new PreUwSummary(u.getId(), u.getCreatedAt(), u.getProduct().getName(),
                                u.getResults().stream().map(r -> r.getCoverage().getName() + ": " + r.getOutcome().getLabel()).toList()))
                        .toList(),
                externalRecords.findByCustomerIdOrderByDiagnosedOnDesc(id).stream()
                        .map(r -> new ExternalRecord(r.getInstitution(), r.getKcdCode(), r.getDiagnosedOn(),
                                r.getTreatment().getLabel(), r.isCured()))
                        .toList());
    }

    @PostMapping("/{id}/consents")
    @ResponseStatus(HttpStatus.CREATED)
    public ConsentResponse consent(@PathVariable Long id, @Valid @RequestBody ConsentRequest req) {
        return ConsentResponse.from(consentRepository.save(new Consent(find(id), req.type(), LocalDateTime.now())));
    }

    private Customer find(Long id) {
        return customerRepository.findById(id).orElseThrow(() -> new NotFoundException("고객", id));
    }
}
