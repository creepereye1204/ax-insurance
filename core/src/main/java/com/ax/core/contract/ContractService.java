package com.ax.core.contract;

import com.ax.core.application.Application;
import com.ax.core.common.NotFoundException;
import com.ax.core.uw.UwCoverageResult;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContractService {

    static final int TERM_YEARS = 10;

    private final ContractRepository contractRepository;

    /** 심사에서 인수된 담보만으로 계약을 성립시킨다. 부담보 기간은 계약일부터 계산한다. */
    @Transactional
    public Contract issue(Application application, List<UwCoverageResult> acceptedResults) {
        LocalDate start = LocalDate.now();
        Contract contract = new Contract(newContractNo(), application.getCustomer(), application.getProduct(),
                application, start, start.plusYears(TERM_YEARS).minusDays(1));
        for (UwCoverageResult r : acceptedResults) {
            List<ContractExclusion> exclusions = r.getExclusions().stream()
                    .map(e -> new ContractExclusion(e.label(), e.kcd(), start.plusMonths(e.months()).minusDays(1)))
                    .toList();
            contract.addCoverage(new ContractCoverage(r.getCoverage(), r.getInsuredAmount(), r.getRatingPct(), exclusions));
        }
        return contractRepository.save(contract);
    }

    @Transactional(readOnly = true)
    public Contract get(Long id) {
        return contractRepository.findById(id).orElseThrow(() -> new NotFoundException("계약", id));
    }

    @Transactional
    public Contract terminate(Long id) {
        Contract contract = get(id);
        contract.terminate();
        return contract;
    }

    public static String newContractNo() {
        return "CT" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }
}
