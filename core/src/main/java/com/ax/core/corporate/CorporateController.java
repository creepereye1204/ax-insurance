package com.ax.core.corporate;

import com.ax.core.common.NotFoundException;
import com.ax.core.product.ProductPlan;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 법인고객 · 종업원 · 단체 가입설계 */
@RestController
@RequiredArgsConstructor
public class CorporateController {

    private final CorporateService corporateService;
    private final CorporateRepository corporateRepository;
    private final CorporateMemberRepository memberRepository;
    private final GroupQuoteService groupQuoteService;
    private final GroupProposalRepository groupProposalRepository;

    public record CreateRequest(@NotBlank @Size(max = 100) String name, @NotBlank @Size(max = 50) String representative,
                                @NotBlank String industry, @Min(1) @Max(3) int industryRiskGrade, String phone) {
    }

    public record VirtualRequest(String name, String industry, @Min(1) @Max(CorporateService.MAX_VIRTUAL_EMPLOYEES) int employees) {
    }

    public record MemberRequest(@NotNull Long customerId, String employeeNo, String department) {
    }

    public record GroupDesignRequest(@NotNull Long corporateId, @NotNull Long productId, @NotNull Integer paymentTerm,
                                     @NotEmpty List<ProductPlan.Item> items) {
    }

    public record Summary(Long id, String name, String bizRegNo, String representative, String industry,
                          int industryRiskGrade, boolean virtual, long employees, LocalDateTime createdAt) {
    }

    public record Member(Long customerId, String name, String employeeNo, String department, LocalDate joinedOn,
                         int insuranceAge, String gender, String occupation, int jobRiskGrade, String drivingLabel) {
    }

    public record IndustryResponse(String name, int risk) {
    }

    public record Line(Long customerId, String name, String employeeNo, String department, int insuranceAge,
                       boolean included, long monthlyPremium, String excludedReason) {
    }

    public record GroupQuoteResponse(Long id, String proposalNo, Long corporateId, String corporateName, Long productId,
                                     String productName, int paymentTerm, List<ProductPlan.Item> items, int insuredCount,
                                     int excludedCount, long grossPremium, int discountPct, long monthlyPremium,
                                     LocalDateTime issuedAt, LocalDate validUntil, List<Line> lines) {
        static GroupQuoteResponse from(GroupQuoteService.GroupQuote q) {
            return new GroupQuoteResponse(null, null, q.corporate().getId(), q.corporate().getName(), q.product().getId(),
                    q.product().getName(), q.paymentTerm(), q.items(), q.insuredCount(), q.lines().size() - q.insuredCount(),
                    q.grossPremium(), q.discountPct(), q.monthlyPremium(), null, null,
                    q.lines().stream().map(l -> new Line(l.member().getCustomer().getId(), l.member().getCustomer().getName(),
                            l.member().getEmployeeNo(), l.member().getDepartment(), l.insuranceAge(), l.included(),
                            l.monthlyPremium(), l.excludedReason())).toList());
        }

        static GroupQuoteResponse from(GroupProposal p) {
            return new GroupQuoteResponse(p.getId(), p.getProposalNo(), p.getCorporate().getId(), p.getCorporate().getName(),
                    p.getProduct().getId(), p.getProduct().getName(), p.getPaymentTerm(), p.getItems(), p.getInsuredCount(),
                    p.getExcludedCount(), p.getGrossPremium(), p.getDiscountPct(), p.getMonthlyPremium(), p.getIssuedAt(),
                    p.getValidUntil(), p.getMembers().stream().map(m -> new Line(m.getCustomer().getId(), m.getCustomer().getName(),
                            null, null, m.getInsuranceAge(), m.isIncluded(), m.getMonthlyPremium(), m.getExcludedReason())).toList());
        }
    }

    public record GroupProposalSummary(Long id, String proposalNo, String productName, int insuredCount, long monthlyPremium,
                                       LocalDateTime issuedAt, LocalDate validUntil) {
    }

    public record Detail(Summary corporate, List<Member> members, List<GroupProposalSummary> groupProposals) {
    }

    public record PageResponse(List<Summary> content, int page, int totalPages, long totalElements) {
    }

    @GetMapping("/corporates/industries")
    public List<IndustryResponse> industries() {
        return Arrays.stream(CorporateService.INDUSTRIES).map(i -> new IndustryResponse(i.name(), i.risk())).toList();
    }

    @GetMapping("/corporates")
    @Transactional(readOnly = true)
    public PageResponse list(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page) {
        Page<Corporate> found = corporateRepository.search(q.trim(), PageRequest.of(Math.max(page, 0), 20, Sort.by(Sort.Direction.DESC, "id")));
        return new PageResponse(found.map(this::summary).getContent(), found.getNumber(), found.getTotalPages(), found.getTotalElements());
    }

    @PostMapping("/corporates")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Summary create(@Valid @RequestBody CreateRequest req) {
        return summary(corporateService.create(req.name().trim(), req.representative().trim(), req.industry().trim(),
                req.industryRiskGrade(), req.phone()));
    }

    /** 가상 법인 + 종업원 즉시 생성 */
    @PostMapping("/corporates/virtual")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Summary createVirtual(@Valid @RequestBody VirtualRequest req) {
        return summary(corporateService.createVirtual(req.name(), req.industry(), req.employees()));
    }

    @GetMapping("/corporates/{id}")
    @Transactional(readOnly = true)
    public Detail get(@PathVariable Long id) {
        Corporate c = corporateService.find(id);
        LocalDate today = LocalDate.now();
        return new Detail(summary(c),
                memberRepository.findByCorporateIdOrderById(id).stream().map(m -> {
                    var p = m.getCustomer();
                    return new Member(p.getId(), p.getName(), m.getEmployeeNo(), m.getDepartment(), m.getJoinedOn(),
                            p.insuranceAgeAt(today), p.getGender().name(), p.getOccupation(), p.getJobRiskGrade(),
                            p.getDriving() == null ? "미확인" : p.getDriving().getLabel());
                }).toList(),
                groupProposalRepository.findByCorporateIdOrderByIdDesc(id).stream()
                        .map(g -> new GroupProposalSummary(g.getId(), g.getProposalNo(), g.getProduct().getName(), g.getInsuredCount(),
                                g.getMonthlyPremium(), g.getIssuedAt(), g.getValidUntil())).toList());
    }

    @PostMapping("/corporates/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public void addMember(@PathVariable Long id, @Valid @RequestBody MemberRequest req) {
        corporateService.addMember(id, req.customerId(), req.employeeNo(), req.department());
    }

    @DeleteMapping("/corporates/{id}/members/{customerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable Long id, @PathVariable Long customerId) {
        corporateService.removeMember(id, customerId);
    }

    @PostMapping("/group-proposals/quote")
    @Transactional(readOnly = true)
    public GroupQuoteResponse quote(@Valid @RequestBody GroupDesignRequest req) {
        return GroupQuoteResponse.from(groupQuoteService.quote(req.corporateId(), req.productId(), req.paymentTerm(), req.items()));
    }

    @PostMapping("/group-proposals")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public GroupQuoteResponse issue(@Valid @RequestBody GroupDesignRequest req) {
        return GroupQuoteResponse.from(groupQuoteService.issue(req.corporateId(), req.productId(), req.paymentTerm(), req.items()));
    }

    @GetMapping("/group-proposals/{id}")
    @Transactional(readOnly = true)
    public GroupQuoteResponse getProposal(@PathVariable Long id) {
        return GroupQuoteResponse.from(groupProposalRepository.findById(id).orElseThrow(() -> new NotFoundException("단체 설계서", id)));
    }

    private Summary summary(Corporate c) {
        return new Summary(c.getId(), c.getName(), c.getBizRegNo(), c.getRepresentative(), c.getIndustry(), c.getIndustryRiskGrade(),
                c.isVirtualCorporate(), memberRepository.countByCorporateId(c.getId()), c.getCreatedAt());
    }
}
