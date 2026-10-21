package com.ax.core.corporate;

import com.ax.core.common.BusinessException;
import com.ax.core.common.NotFoundException;
import com.ax.core.customer.Customer;
import com.ax.core.customer.CustomerRepository;
import com.ax.core.customer.CustomerService;
import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CorporateService {

    public static final int MAX_VIRTUAL_EMPLOYEES = 300;

    /** 업종, 업종 위험등급, 대표 직무, 직무 위험등급, 부서 목록 (가상) */
    public record Industry(String name, int risk, String job, int jobRisk, String[] departments) {
    }

    public static final Industry[] INDUSTRIES = {
            new Industry("IT 서비스", 1, "소프트웨어 개발자", 1, new String[] {"개발", "기획", "디자인", "경영지원"}),
            new Industry("제조", 2, "생산직", 2, new String[] {"생산", "품질", "구매", "경영지원"}),
            new Industry("물류·운송", 3, "배송기사", 3, new String[] {"배송", "물류센터", "배차", "경영지원"}),
            new Industry("건설", 3, "건설현장 근로자", 3, new String[] {"현장", "공무", "안전", "경영지원"}),
            new Industry("요식업", 2, "조리사", 2, new String[] {"주방", "홀", "매장관리"}),
            new Industry("금융", 1, "회사원(사무직)", 1, new String[] {"영업", "리스크", "IT", "경영지원"}),
    };

    private final CorporateRepository corporateRepository;
    private final CorporateMemberRepository memberRepository;
    private final CustomerRepository customerRepository;
    private final CustomerService customerService;

    @Transactional
    public Corporate create(String name, String representative, String industry, int industryRiskGrade, String phone) {
        return corporateRepository.save(new Corporate(name, newBizRegNo(), representative, industry, industryRiskGrade, phone, false));
    }

    /**
     * 가상 법인 즉시 생성: 종업원(가상 개인고객)까지 함께 만든다. 사무직 외 업종은 종업원의 70%가 현장 직무(업종 위험등급).
     */
    @Transactional
    public Corporate createVirtual(String name, String industryName, int employees) {
        if (employees < 1 || employees > MAX_VIRTUAL_EMPLOYEES) {
            throw new BusinessException("종업원 수는 1~" + MAX_VIRTUAL_EMPLOYEES + "명입니다.");
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Industry ind = INDUSTRIES[rnd.nextInt(INDUSTRIES.length)];
        for (Industry i : INDUSTRIES) {
            if (i.name().equals(industryName)) {
                ind = i;
            }
        }
        String corpName = name == null || name.isBlank() ? "가상" + ind.name().replace("·", "") + "%03d(주)".formatted(rnd.nextInt(1000)) : name.trim();
        Corporate corp = corporateRepository.save(new Corporate(corpName, newBizRegNo(), "대표" + rnd.nextInt(100), ind.name(),
                ind.risk(), "02-0000-%04d".formatted(rnd.nextInt(10000)), true));
        for (int n = 1; n <= employees; n++) {
            boolean field = ind.risk() > 1 && rnd.nextDouble() < 0.7;
            Customer c = customerService.createVirtual(new CustomerService.VirtualSpec(20, 62, null, null, null,
                    field ? ind.job() : "회사원(사무직)", field ? ind.jobRisk() : 1));
            String dept = field ? ind.departments()[0] : ind.departments()[1 + rnd.nextInt(ind.departments().length - 1)];
            memberRepository.save(new CorporateMember(corp, c, "E%04d".formatted(n), dept,
                    LocalDate.now().minusDays(rnd.nextInt(3650))));
        }
        return corp;
    }

    @Transactional
    public void addMember(Long corporateId, Long customerId, String employeeNo, String department) {
        Corporate corp = find(corporateId);
        Customer customer = customerRepository.findById(customerId).orElseThrow(() -> new NotFoundException("고객", customerId));
        if (memberRepository.existsByCorporateIdAndCustomerId(corporateId, customerId)) {
            throw new BusinessException("이미 등록된 종업원입니다.");
        }
        long next = memberRepository.countByCorporateId(corporateId) + 1;
        memberRepository.save(new CorporateMember(corp, customer,
                employeeNo == null || employeeNo.isBlank() ? "E%04d".formatted(next) : employeeNo.trim(),
                department == null || department.isBlank() ? "미지정" : department.trim(), LocalDate.now()));
    }

    @Transactional
    public void removeMember(Long corporateId, Long customerId) {
        memberRepository.deleteByCorporateIdAndCustomerId(corporateId, customerId);
    }

    public Corporate find(Long id) {
        return corporateRepository.findById(id).orElseThrow(() -> new NotFoundException("법인", id));
    }

    /** 가상 사업자등록번호: 999-xx-xxxxx (실존 번호와 구분되는 접두) */
    private String newBizRegNo() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < 10; i++) {
            String no = "999-%02d-%05d".formatted(rnd.nextInt(100), rnd.nextInt(100_000));
            if (!corporateRepository.existsByBizRegNo(no)) {
                return no;
            }
        }
        throw new IllegalStateException("사업자번호 생성 실패");
    }
}
