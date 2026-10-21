package com.ax.core.customer;

import com.ax.core.common.BusinessException;
import com.ax.core.common.NotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private static final String[] SURNAMES = {"김", "이", "박", "최", "정", "강", "조", "윤", "장", "임", "한", "오", "서", "신", "권"};
    private static final String[] GIVEN = {"민", "서", "지", "현", "우", "수", "예", "도", "하", "윤", "준", "은", "채", "시", "유", "연", "진", "아"};
    /** 직업, 위험등급 */
    private static final Object[][] JOBS = {
            {"회사원(사무직)", 1}, {"공무원", 1}, {"교사", 1}, {"간호사", 1}, {"자영업", 2}, {"영업직", 2},
            {"택배기사", 3}, {"건설현장 근로자", 3}, {"주부", 1}, {"대학생", 1}, {"프리랜서 개발자", 1}};

    private final CustomerRepository customerRepository;
    private final CustomerRelationRepository relationRepository;
    private final InvestmentAssessmentRepository assessmentRepository;
    private final RrnAccessLogRepository rrnAccessLogRepository;
    private final ResidentNumbers residentNumbers;

    public record VirtualSpec(Integer minAge, Integer maxAge, Gender gender, ConsumerType consumerType, DrivingType driving) {
    }

    /** 신규 고객: 실제 주민번호는 받지 않고 생년월일·성별로 가상 주민번호를 만들어 암호화 저장한다. */
    @Transactional
    public Customer create(String name, LocalDate birthDate, Gender gender, String phone, ConsumerType consumerType,
                           String occupation, int jobRiskGrade, DrivingType driving) {
        Customer c = new Customer(name, birthDate, gender, phone, consumerType, occupation, jobRiskGrade);
        c.changeDriving(driving);
        assignVirtualRrn(c);
        return customerRepository.save(c);
    }

    /** 가상고객 즉시 생성 (나이 범위·성별·소비자 유형·운전을 지정하거나 무작위) */
    @Transactional
    public Customer createVirtual(VirtualSpec spec) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int min = spec.minAge() == null ? 20 : spec.minAge();
        int max = spec.maxAge() == null ? 60 : spec.maxAge();
        if (min < 0 || max > 100 || min > max) {
            throw new BusinessException("나이 범위를 확인하세요 (0~100세, 최소 ≤ 최대).");
        }
        LocalDate today = LocalDate.now();
        // [today - (max+1)년 + 1일, today - min년] 사이의 생일 → 만 나이가 min~max
        LocalDate from = today.minusYears(max + 1L).plusDays(1);
        LocalDate to = today.minusYears(min);
        LocalDate birth = from.plusDays(rnd.nextLong(to.toEpochDay() - from.toEpochDay() + 1));
        Gender gender = spec.gender() != null ? spec.gender() : rnd.nextBoolean() ? Gender.M : Gender.F;
        Object[] job = birth.isAfter(today.minusYears(19)) ? new Object[] {"학생", 1} : JOBS[rnd.nextInt(JOBS.length)];
        DrivingType driving = spec.driving() != null ? spec.driving()
                : birth.isAfter(today.minusYears(19)) ? DrivingType.NONE : DrivingType.values()[rnd.nextInt(3)];
        String name = SURNAMES[rnd.nextInt(SURNAMES.length)] + GIVEN[rnd.nextInt(GIVEN.length)] + GIVEN[rnd.nextInt(GIVEN.length)];
        String phone = "010-0000-%04d".formatted(rnd.nextInt(10000));
        Customer c = create(name, birth, gender, phone, spec.consumerType() == null ? ConsumerType.GENERAL : spec.consumerType(),
                (String) job[0], (int) job[1], driving);
        c.markVirtual();
        return c;
    }

    private void assignVirtualRrn(Customer c) {
        for (int i = 0; i < 5; i++) {
            ResidentNumbers.Protected rrn = residentNumbers.protect(ResidentNumbers.generateVirtual(c.getBirthDate(), c.getGender()));
            if (!customerRepository.existsByRrnHash(rrn.hash())) {
                c.assignRrn(rrn);
                return;
            }
        }
        throw new IllegalStateException("가상 주민번호 생성 실패 (중복)");
    }

    /** 주민번호 원문 열람: 사유 필수, 감사 로그 기록 */
    @Transactional
    public String revealRrn(Long customerId, String accessor, String reason) {
        Customer c = find(customerId);
        if (c.getRrnEncrypted() == null) {
            throw new BusinessException("주민번호가 등록되지 않은 고객입니다.");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("열람 사유를 입력하세요.");
        }
        rrnAccessLogRepository.save(new RrnAccessLog(customerId, accessor == null || accessor.isBlank() ? "설계사" : accessor.trim(),
                reason.trim()));
        return residentNumbers.decrypt(c.getRrnEncrypted());
    }

    /** 투자성향 진단: 매번 이력을 남기고 고객의 최신 성향을 갱신한다 */
    @Transactional
    public Customer assess(Long customerId, List<Integer> scores) {
        Customer c = find(customerId);
        InvestmentAssessment a = assessmentRepository.save(new InvestmentAssessment(c, scores));
        c.assessInvestmentProfile(a.getProfile());
        return c;
    }

    // ---------------------------------------------------------------- 가족관계

    @Transactional
    public void relate(Long customerId, Long relativeId, Relation relation) {
        if (customerId.equals(relativeId)) {
            throw new BusinessException("본인과는 가족관계를 등록할 수 없습니다.");
        }
        Customer c = find(customerId);
        Customer r = find(relativeId);
        if (relationRepository.existsByCustomerIdAndRelativeId(customerId, relativeId)) {
            throw new BusinessException("이미 등록된 가족입니다.");
        }
        checkLimit(customerId, relation);
        checkLimit(relativeId, relation.inverse());
        if (relation == Relation.PARENT && !r.getBirthDate().isBefore(c.getBirthDate())
                || relation == Relation.CHILD && !r.getBirthDate().isAfter(c.getBirthDate())) {
            throw new BusinessException("부모는 본인보다 나이가 많아야 합니다.");
        }
        relationRepository.save(new CustomerRelation(c, r, relation));
        relationRepository.save(new CustomerRelation(r, c, relation.inverse()));
    }

    @Transactional
    public void unrelate(Long customerId, Long relativeId) {
        relationRepository.deleteByCustomerIdAndRelativeId(customerId, relativeId);
        relationRepository.deleteByCustomerIdAndRelativeId(relativeId, customerId);
    }

    private void checkLimit(Long customerId, Relation relation) {
        long count = relationRepository.countByCustomerIdAndRelation(customerId, relation);
        if (relation == Relation.SPOUSE && count >= 1 || relation == Relation.PARENT && count >= 2) {
            throw new BusinessException(relation == Relation.SPOUSE ? "배우자는 1명만 등록할 수 있습니다." : "부모는 2명까지 등록할 수 있습니다.");
        }
    }

    public Customer find(Long id) {
        return customerRepository.findById(id).orElseThrow(() -> new NotFoundException("고객", id));
    }
}
