package com.ax.core.claim;

import com.ax.core.common.BusinessException;
import com.ax.core.common.NotFoundException;
import com.ax.core.contract.Contract;
import com.ax.core.contract.ContractRepository;
import com.ax.core.kcd.KcdRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClaimService {

    private final ClaimRepository claimRepository;
    private final ContractRepository contractRepository;
    private final KcdRepository kcdRepository;
    private final ClaimRuleChecker ruleChecker;
    private final ApplicationEventPublisher events;

    /**
     * 청구 접수. 규칙 점검(부담보·고지위반)은 동기로 즉시 수행하고,
     * AI 검토는 커밋 이후 비동기로 진행되므로 접수 응답은 즉시 반환된다.
     */
    @Transactional
    public Claim submit(Long contractId, ClaimType type, LocalDate treatmentDate, String diagnosis,
                        String kcdCode, long amount) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new NotFoundException("계약", contractId));
        if (kcdCode != null && !kcdRepository.existsById(kcdCode)) {
            throw new BusinessException("등록되지 않은 질병코드입니다: " + kcdCode);
        }
        Claim claim = new Claim(newClaimNo(), contract, type, treatmentDate, diagnosis, kcdCode, amount);
        claim.attachCheckNotes(ruleChecker.check(contract, kcdCode, treatmentDate));
        claimRepository.save(claim);
        events.publishEvent(new ClaimSubmittedEvent(claim.getId()));
        return claim;
    }

    @Transactional(readOnly = true)
    public Claim get(Long id) {
        return claimRepository.findById(id).orElseThrow(() -> new NotFoundException("청구", id));
    }

    @Transactional
    public Claim approve(Long id, long paidAmount, String comment) {
        Claim claim = get(id);
        claim.approve(paidAmount, comment);
        return claim;
    }

    @Transactional
    public Claim reject(Long id, String comment) {
        Claim claim = get(id);
        claim.reject(comment);
        return claim;
    }

    private String newClaimNo() {
        return "CL" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }
}
