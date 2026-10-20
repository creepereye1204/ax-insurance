package com.ax.core.uw;

import com.ax.core.application.Application;
import com.ax.core.application.DisclosureAnswer;
import com.ax.core.claim.Claim;
import com.ax.core.claim.ClaimRepository;
import com.ax.core.claim.ClaimType;
import com.ax.core.common.TreatmentType;
import com.ax.core.customer.Customer;
import com.ax.core.external.ExternalMedicalRecordRepository;
import com.ax.core.product.CoverageCategory;
import com.ax.core.uw.engine.MedicalRecord;
import com.ax.core.uw.engine.UwInput;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 심사 단계별로 룰 엔진 입력을 만든다. 같은 엔진·같은 룰을 쓰고 입력만 다르다. */
@Component
@RequiredArgsConstructor
public class UwInputFactory {

    public static final String SOURCE_DISCLOSURE = "고지";
    public static final String SOURCE_CLAIM = "당사청구";

    private final ClaimRepository claimRepository;
    private final ExternalMedicalRecordRepository externalRecords;

    /** 전산심사: 고지 응답·상세 + 당사 청구 횟수 */
    public UwInput forApplication(Application app, List<CoverageCategory> categories) {
        LocalDate asOf = app.getSubmittedAt().toLocalDate();
        Map<String, Boolean> answers = new LinkedHashMap<>();
        for (DisclosureAnswer a : app.getAnswers()) {
            answers.put(a.getQuestion().getCode(), a.isAnswer());
        }
        List<MedicalRecord> records = app.allDetails().stream()
                .map(d -> new MedicalRecord(SOURCE_DISCLOSURE, d.getKcdCode(), d.getDiagnosedOn(), d.getTreatment(), d.isCured()))
                .toList();
        Customer c = app.getCustomer();
        return new UwInput(asOf, c.ageAt(asOf), c.getJobRiskGrade(), answers, records, claimDates(c.getId()), categories);
    }

    /** 선심사: 고지 없이 당사 청구이력 + 외부기관 조회 결과만으로 예상 */
    public UwInput forPreUw(Customer customer, List<MedicalRecord> history, List<CoverageCategory> categories) {
        LocalDate asOf = LocalDate.now();
        return new UwInput(asOf, customer.ageAt(asOf), customer.getJobRiskGrade(), Map.of(), history,
                claimDates(customer.getId()), categories);
    }

    /** 선심사 조회 대상 이력 (스냅샷으로 저장된다) */
    public List<MedicalRecord> historyOf(Customer customer) {
        Stream<MedicalRecord> internal = claimRepository.findByContractCustomerIdOrderByIdDesc(customer.getId()).stream()
                .filter(cl -> cl.getKcdCode() != null)
                .map(cl -> new MedicalRecord(SOURCE_CLAIM, cl.getKcdCode(), cl.getTreatmentDate(), treatmentOf(cl), false));
        Stream<MedicalRecord> external = externalRecords.findByCustomerIdOrderByDiagnosedOnDesc(customer.getId()).stream()
                .map(r -> new MedicalRecord("외부:" + r.getInstitution(), r.getKcdCode(), r.getDiagnosedOn(),
                        r.getTreatment(), r.isCured()));
        return Stream.concat(internal, external).toList();
    }

    private List<LocalDate> claimDates(Long customerId) {
        return claimRepository.findByContractCustomerIdOrderByIdDesc(customerId).stream()
                .map(Claim::getTreatmentDate).toList();
    }

    private static TreatmentType treatmentOf(Claim claim) {
        return claim.getType() == ClaimType.INPATIENT ? TreatmentType.INPATIENT
                : claim.getType() == ClaimType.PRESCRIPTION ? TreatmentType.MEDICATION : TreatmentType.OUTPATIENT;
    }
}
