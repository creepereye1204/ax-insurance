package com.ax.core.proposal;

import com.ax.core.common.BusinessException;
import com.ax.core.customer.ConsumerType;
import com.ax.core.customer.Customer;
import com.ax.core.customer.InvestmentProfile;
import com.ax.core.product.Coverage;
import com.ax.core.product.Fund;
import com.ax.core.product.FundRepository;
import com.ax.core.product.PremiumRateRepository;
import com.ax.core.product.Product;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 가입설계 보험료 산출.
 * <ul>
 *   <li>보장성 담보: 요율표(성별·나이·납입기간) × 가입금액</li>
 *   <li>저축성: 고객이 정한 월 납입액 + 위험 특약 보험료, 적립금 예시표</li>
 *   <li>변액: 펀드 편입비율 검증 + 적합성 원칙(일반금융소비자)</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class PremiumCalculator {

    private final PremiumRateRepository rateRepository;
    private final FundRepository fundRepository;

    public record CoverageAmount(Long coverageId, long amount) {
    }

    public record Input(int paymentTerm, List<CoverageAmount> coverages, Long monthlyContribution,
                        Integer annuityStartAge, List<FundAllocation> funds, boolean unsuitableAcknowledged) {
    }

    public record CoverageQuote(Coverage coverage, long amount, long monthlyPremium) {
    }

    public record Quote(Product product, Customer customer, int age, int paymentTerm, List<CoverageQuote> coverages,
                        Long monthlyContribution, Integer annuityStartAge, List<FundAllocation> funds,
                        long monthlyPremium, Illustration illustration, Suitability suitability,
                        List<String> suitabilityIssues) {
    }

    public Quote quote(Product product, Customer customer, Input in) {
        LocalDate today = LocalDate.now();
        int age = customer.ageAt(today);
        if (!product.isOnSale()) {
            throw new BusinessException("판매 중지된 상품입니다: " + product.getName());
        }
        if (!product.acceptsAge(age)) {
            throw new BusinessException("가입 가능 나이(만 %d~%d세)가 아닙니다. 고객 나이: 만 %d세"
                    .formatted(product.getMinEntryAge(), product.getMaxEntryAge(), age));
        }
        if (!product.getPaymentTerms().contains(in.paymentTerm())) {
            throw new BusinessException("선택할 수 없는 납입기간입니다: " + in.paymentTerm() + "년");
        }

        List<CoverageQuote> quotes = quoteCoverages(product, customer, age, in);
        long premium = quotes.stream().mapToLong(CoverageQuote::monthlyPremium).sum();

        Illustration illustration = null;
        Suitability suitability = Suitability.NOT_APPLICABLE;
        List<String> issues = List.of();
        Long contribution = null;
        Integer annuityAge = null;
        List<FundAllocation> funds = null;

        if (product.getType().isSavings()) {
            contribution = validContribution(product, in.monthlyContribution());
            annuityAge = validAnnuityAge(product, age, in);
            premium += contribution;
            if (product.getType().isVariable()) {
                funds = in.funds();
                Map<String, Fund> fundMap = validFunds(product, funds);
                double feePct = funds.stream()
                        .mapToDouble(f -> fundMap.get(f.fundCode()).getAnnualFeeBp() / 100.0 * f.pct() / 100).sum();
                illustration = Illustration.variable(age, contribution, in.paymentTerm(), annuityAge, feePct);
                issues = suitabilityIssues(customer, funds, fundMap);
                suitability = decideSuitability(customer, issues, in.unsuitableAcknowledged());
            } else {
                illustration = Illustration.fixed(age, contribution, in.paymentTerm(), annuityAge);
            }
        }
        return new Quote(product, customer, age, in.paymentTerm(), quotes, contribution, annuityAge, funds,
                premium, illustration, suitability, issues);
    }

    private List<CoverageQuote> quoteCoverages(Product product, Customer customer, int age, Input in) {
        Map<Long, Coverage> byId = product.getCoverages().stream()
                .collect(Collectors.toMap(Coverage::getId, Function.identity()));
        Map<Long, Long> amounts = new HashMap<>();
        for (CoverageAmount ca : in.coverages() == null ? List.<CoverageAmount>of() : in.coverages()) {
            Coverage c = byId.get(ca.coverageId());
            if (c == null) {
                throw new BusinessException("상품에 없는 담보입니다: " + ca.coverageId());
            }
            if (amounts.put(c.getId(), ca.amount()) != null) {
                throw new BusinessException("같은 담보를 중복 선택했습니다: " + c.getName());
            }
        }
        List<CoverageQuote> result = new ArrayList<>();
        for (Coverage c : product.getCoverages()) {
            if (c.isAccumulation()) {
                // 적립 담보는 가입금액 없이 항상 포함 (보험료는 월 납입액)
                result.add(new CoverageQuote(c, 0, 0));
                continue;
            }
            Long amount = amounts.get(c.getId());
            if (amount == null) {
                if (c.isRequired()) {
                    throw new BusinessException("주계약 '" + c.getName() + "'은(는) 반드시 가입해야 합니다.");
                }
                continue;
            }
            if (!c.isValidAmount(amount)) {
                throw new BusinessException("%s 가입금액은 %,d~%,d원(%,d원 단위)이어야 합니다."
                        .formatted(c.getName(), c.getMinAmount(), c.getMaxAmount(), c.getAmountStep()));
            }
            long rate = rateRepository.findByCoverageIdAndGenderAndAgeAndPaymentTerm(
                            c.getId(), customer.getGender(), age, in.paymentTerm())
                    .orElseThrow(() -> new BusinessException("요율이 없습니다: " + c.getName() + " (만 " + age + "세)"))
                    .getRatePer10M();
            long premium = Math.round(amount * (double) rate / 10_000_000 / 10) * 10;
            result.add(new CoverageQuote(c, amount, Math.max(premium, 10)));
        }
        return result;
    }

    private static long validContribution(Product product, Long contribution) {
        if (contribution == null || contribution < product.getMinMonthlyContribution()
                || contribution > product.getMaxMonthlyContribution() || contribution % 10_000 != 0) {
            throw new BusinessException("월 납입액은 %,d~%,d원(1만원 단위)이어야 합니다."
                    .formatted(product.getMinMonthlyContribution(), product.getMaxMonthlyContribution()));
        }
        return contribution;
    }

    private static Integer validAnnuityAge(Product product, int age, Input in) {
        if (product.getAnnuityStartAges() == null) {
            return null;
        }
        Integer start = in.annuityStartAge();
        if (start == null || !product.getAnnuityStartAges().contains(start)) {
            throw new BusinessException("연금개시나이를 선택하세요: " + product.getAnnuityStartAges());
        }
        if (start < age + in.paymentTerm()) {
            throw new BusinessException("연금개시나이는 납입 완료 나이(만 %d세) 이후여야 합니다.".formatted(age + in.paymentTerm()));
        }
        return start;
    }

    private Map<String, Fund> validFunds(Product product, List<FundAllocation> funds) {
        if (funds == null || funds.isEmpty()) {
            throw new BusinessException("변액보험은 펀드를 1개 이상 선택해야 합니다.");
        }
        Set<String> seen = new HashSet<>();
        int total = 0;
        for (FundAllocation f : funds) {
            if (!product.getFundCodes().contains(f.fundCode())) {
                throw new BusinessException("이 상품에서 선택할 수 없는 펀드입니다: " + f.fundCode());
            }
            if (!seen.add(f.fundCode()) || f.pct() <= 0 || f.pct() % 10 != 0) {
                throw new BusinessException("펀드 편입비율은 10% 단위로, 펀드별 한 번씩 입력하세요.");
            }
            total += f.pct();
        }
        if (total != 100) {
            throw new BusinessException("펀드 편입비율 합계가 100%가 아닙니다 (현재 " + total + "%).");
        }
        return fundRepository.findAllById(seen).stream().collect(Collectors.toMap(Fund::getCode, Function.identity()));
    }

    /** 적합성 원칙: 일반금융소비자의 투자성향보다 위험한 펀드가 있는지 */
    private static List<String> suitabilityIssues(Customer customer, List<FundAllocation> funds, Map<String, Fund> fundMap) {
        if (customer.getConsumerType() == ConsumerType.PROFESSIONAL) {
            return List.of();
        }
        InvestmentProfile profile = customer.getInvestmentProfile();
        if (profile == null) {
            throw new BusinessException("변액보험은 투자성향 진단이 필요합니다. 고객 상세에서 투자성향을 진단하세요.");
        }
        return funds.stream()
                .map(f -> fundMap.get(f.fundCode()))
                .filter(fund -> !profile.allows(fund.getRiskGrade()))
                .map(fund -> "%s(위험 %d등급)은 고객 투자성향(%s, %d등급까지)보다 위험합니다."
                        .formatted(fund.getName(), fund.getRiskGrade(), profile.getLabel(), profile.getMaxRiskGrade()))
                .toList();
    }

    private static Suitability decideSuitability(Customer customer, List<String> issues, boolean acknowledged) {
        if (customer.getConsumerType() == ConsumerType.PROFESSIONAL) {
            return Suitability.EXEMPT_PROFESSIONAL;
        }
        if (issues.isEmpty()) {
            return Suitability.SUITABLE;
        }
        // 견적 단계에서는 막지 않고 결과를 보여준다. 설계서 발행 시 고객 확인 여부를 검사한다.
        return acknowledged ? Suitability.UNSUITABLE_ACKNOWLEDGED : Suitability.UNSUITABLE;
    }
}
