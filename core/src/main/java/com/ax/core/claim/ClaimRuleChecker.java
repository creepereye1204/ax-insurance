package com.ax.core.claim;

import com.ax.core.application.Application;
import com.ax.core.contract.Contract;
import com.ax.core.contract.ContractCoverage;
import com.ax.core.external.ExternalMedicalRecordRepository;
import com.ax.core.product.CoverageCategory;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 청구 접수 시 규칙 기반 점검. 결과는 "확인 필요 사항"으로 심사자에게 보여줄 뿐 자동으로 부지급하지 않는다.
 * <ul>
 *   <li>실손 담보 가입 여부</li>
 *   <li>부담보 해당 여부 (계약 성립 시 심사 결과로 붙은 조건)</li>
 *   <li>고지의무 위반 의심: 청약 전 같은 질병(KCD 3자리) 진료 이력이 있는데 고지하지 않음</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ClaimRuleChecker {

    private final ExternalMedicalRecordRepository externalRecords;
    private final ClaimRepository claimRepository;

    record PriorRecord(String source, String kcdCode, LocalDate date) {
    }

    public List<String> check(Contract contract, String kcdCode, LocalDate treatmentDate) {
        List<String> notes = new ArrayList<>();
        if (contract.getCoverages().isEmpty()) {
            return notes;
        }
        Optional<ContractCoverage> medical = contract.coverageOf(CoverageCategory.MEDICAL);
        if (medical.isEmpty()) {
            notes.add("실손의료비 담보가 없는 계약입니다 (심사에서 거절되었거나 미가입).");
            return notes;
        }
        if (kcdCode == null) {
            return notes;
        }
        medical.get().exclusionFor(kcdCode, treatmentDate).ifPresent(e ->
                notes.add("부담보 해당: " + e.label() + " (" + e.kcd() + ", 부담보 기간 ~" + e.until() + ")"));

        Application application = contract.getApplication();
        if (application != null) {
            nonDisclosureSuspicion(contract, application, kcdCode).ifPresent(notes::add);
        }
        return notes;
    }

    private Optional<String> nonDisclosureSuspicion(Contract contract, Application application, String kcdCode) {
        String group = kcdCode.length() >= 3 ? kcdCode.substring(0, 3) : kcdCode;
        LocalDate applied = application.getSubmittedAt().toLocalDate();

        boolean disclosed = application.allDetails().stream().anyMatch(d -> d.getKcdCode().startsWith(group));
        if (disclosed) {
            return Optional.empty();
        }
        Long customerId = contract.getCustomer().getId();
        Stream<PriorRecord> external = externalRecords.findByCustomerIdOrderByDiagnosedOnDesc(customerId).stream()
                .map(r -> new PriorRecord("외부기관(" + r.getInstitution() + ")", r.getKcdCode(), r.getDiagnosedOn()));
        Stream<PriorRecord> internal = claimRepository.findByContractCustomerIdOrderByIdDesc(customerId).stream()
                .filter(c -> c.getKcdCode() != null)
                .map(c -> new PriorRecord("당사 청구(" + c.getClaimNo() + ")", c.getKcdCode(), c.getTreatmentDate()));

        return Stream.concat(external, internal)
                .filter(r -> r.kcdCode().startsWith(group) && r.date().isBefore(applied))
                .findFirst()
                .map(r -> "고지의무 위반 의심: 청약일(" + applied + ") 이전 " + r.date() + " " + r.kcdCode()
                        + " 진료 이력이 있으나 고지되지 않았습니다. 출처: " + r.source());
    }
}
