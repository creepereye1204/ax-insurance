#!/usr/bin/env python3
"""업무 시나리오 자동 테스트 (docs/scenarios.md 의 시나리오를 API 로 실행해 기대 결과를 검증한다).

사용법:
    python3 scripts/scenarios.py                       # 기본: http://localhost:8031 (기간계 직접)
    python3 scripts/scenarios.py https://www.insurance.r-e.kr/core

시나리오는 데이터를 새로 만든다(설계서·청약·계약·청구). 같은 고객으로 여러 번 실행해도 동작한다.
"""
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8031").rstrip("/")
ALL_AGREEMENTS = ["EXPLAINED", "TERMS_RECEIVED", "DISCLOSURE_TRUE", "PRIVACY", "VARIABLE_RISK", "SENIOR_PROTECTION"]
results = []


class ApiError(Exception):
    def __init__(self, status, detail):
        super().__init__(f"{status}: {detail}")
        self.status, self.detail = status, detail


def call(method, path, body=None, retries=5):
    req = urllib.request.Request(BASE + path, method=method, headers={"content-type": "application/json"},
                                 data=None if body is None else json.dumps(body).encode())
    try:
        with urllib.request.urlopen(req, timeout=60) as res:
            return json.loads(res.read() or "null")
    except urllib.error.HTTPError as e:
        raw = e.read()
        if e.code == 429 and retries > 0:  # 게이트웨이 요청 제한: 잠시 쉬고 재시도
            time.sleep(1.5)
            return call(method, path, body, retries - 1)
        try:
            payload = json.loads(raw or b"{}")
        except json.JSONDecodeError:
            payload = {"detail": raw[:200].decode(errors="replace")}
        raise ApiError(e.code, payload.get("detail", payload) if isinstance(payload, dict) else payload) from None


def customer(name):
    page = call("GET", "/customers?q=" + urllib.parse.quote(name))
    # 시나리오 고객은 가장 먼저 등록됐으므로 id 가 가장 작은 동명이인을 사용
    return min((c for c in page["content"] if c["name"] == name), key=lambda c: c["id"])


def product(type_, predicate=lambda p: True):
    page = call("GET", f"/products?type={type_}&size=100")
    return call("GET", f"/products/{next(p for p in page['content'] if predicate(p))['id']}")


def diagnose(cust, scores):
    """투자성향 진단 (변액은 설계할 때마다 당일 재진단)"""
    return call("POST", f"/customers/{cust['id']}/investment-profile", {"scores": scores})


def design(cust, prod, term, amounts=None, contribution=None, annuity_age=None, funds=None, ack=False):
    if amounts is None:
        amounts = [{"coverageId": c["id"], "amount": c["defaultAmount"]}
                   for c in prod["coverages"] if not c["accumulation"]]
    return call("POST", "/proposals", {
        "customerId": cust["id"], "productId": prod["summary"]["id"], "paymentTerm": term, "coverages": amounts,
        "monthlyContribution": contribution, "annuityStartAge": annuity_age, "funds": funds,
        "unsuitableAcknowledged": ack})


def questionnaire(product_id):
    """상품 심사 유형에 맞는 질문지 (일반심사 → 표준, 간편심사형 → 간편고지 3-2-5)"""
    return call("GET", f"/questionnaires/current?productId={product_id}")


def answers_of(qn, yes=None):
    """yes: {문항코드: [상세...]} — 나머지 문항은 '아니오'"""
    yes = yes or {}
    return [{"questionId": q["id"], "answer": q["code"] in yes, "details": yes.get(q["code"], [])} for q in qn["questions"]]


def days_ago(n):
    return time.strftime("%Y-%m-%d", time.localtime(time.time() - n * 86400))


def apply(proposal, cust, yes=None, agreements=None):
    qn = questionnaire(proposal["productId"])
    answers = answers_of(qn, yes)
    doc = call("POST", "/applications/preview", {"proposalId": proposal["id"], "questionnaireId": qn["id"], "answers": answers})
    required = [a["code"] for a in doc["requiredAgreements"]]
    return doc, call("POST", "/applications", {
        "proposalId": proposal["id"], "questionnaireId": qn["id"], "answers": answers,
        "signature": {"signerName": cust["name"], "agreements": agreements if agreements is not None else required}})


def issued(detail, allowed_refer_rules=("R-CLM-01",)):
    """계약 성립까지 진행. 같은 서버에서 시나리오를 반복 실행하면 청구가 누적돼 잦은 청구 룰(R-CLM-01)로
    본사 이관될 수 있다 — 그 경우 심사자 승인을 거쳐 계약을 성립시킨다(반복 실행 안정성)."""
    if detail["summary"]["status"] == "UNDER_REVIEW":
        case = detail["uwCase"]
        referred = [x for x in case["results"] if x["outcome"] == "REFER"]
        assert all(set(x["matchedRules"]) & set(allowed_refer_rules) for x in referred), \
            f"예상 밖 본사 이관: {[x['matchedRules'] for x in referred]}"
        choices = {str(r["coverageId"]): "ACCEPT_PROPOSED" for r in case["results"]}
        call("POST", f"/uw/cases/{case['id']}/decision", {"choices": choices, "underwriter": "시나리오", "comment": "반복 실행"})
        detail = call("GET", f"/applications/{detail['summary']['id']}")
    assert detail["summary"]["status"] == "ISSUED" and detail["contractId"], detail["summary"]
    return detail


def scenario(name):
    def wrap(fn):
        started = time.time()
        try:
            note = fn()
            results.append(("PASS", name, note or "", time.time() - started))
        except AssertionError as e:
            results.append(("FAIL", name, str(e), time.time() - started))
        except Exception as e:  # noqa: BLE001
            results.append(("ERROR", name, f"{type(e).__name__}: {e}", time.time() - started))
        return fn
    return wrap


def outcomes(detail):
    return {r["coverageName"]: r["outcome"] for r in detail["uwCase"]["results"]}


# ---------------------------------------------------------------------------------------------------- 시나리오

@scenario("S01 김정상: 건강보험 고지 없음 → 자동 승인 → 계약 성립 → 청약철회")
def _():
    c = customer("김정상")
    p = design(c, product("HEALTH", lambda x: not x["simplifiedUw"]), 20)
    _, d = apply(p, c)
    assert d["summary"]["status"] == "ISSUED", d["summary"]
    k = call("POST", f"/contracts/{d['contractId']}/withdraw")
    assert k["status"] == "WITHDRAWN", k["status"]
    return f"월보험료 {p['monthlyPremium']:,}원, 청약철회 완료"


@scenario("S02 이위염: 선심사 → 위·십이지장 부담보 예상")
def _():
    c = customer("이위염")
    call("POST", f"/customers/{c['id']}/consents", {"type": "PRE_UW_INQUIRY"})
    prod = product("HEALTH", lambda x: not x["simplifiedUw"])
    r = call("POST", "/uw/pre", {"customerId": c["id"], "productId": prod["summary"]["id"]})
    rules = {rule for x in r["uwCase"]["results"] for rule in x["matchedRules"]}
    assert "R-GI-01" in rules, rules
    return "R-GI-01 부담보 예상"


@scenario("S03 이위염: 위염 미고지로 가입 → 위염 청구 → 고지의무 위반 의심")
def _():
    c = customer("이위염")
    sample = call("GET", "/products?q=" + urllib.parse.quote("샘플") + "&size=5")["content"][0]
    p = design(c, call("GET", f"/products/{sample['id']}"), 20)
    _, d = apply(p, c)
    d = issued(d)
    claim = call("POST", "/claims", {"contractId": d["contractId"], "type": "OUTPATIENT",
                                     "treatmentDate": time.strftime("%Y-%m-%d"), "diagnosis": "위염 외래",
                                     "kcdCode": "K29.7", "amount": 50000})
    assert any("고지의무 위반 의심" in n for n in claim["checkNotes"]), claim["checkNotes"]
    return "고지의무 위반 의심 표시"


@scenario("S04 박당뇨: 당뇨 고지 → 본사 이관 → 심사자 실손 거절 → 일부 담보 계약")
def _():
    c = customer("박당뇨")
    sample = call("GET", "/products?q=" + urllib.parse.quote("샘플") + "&size=5")["content"][0]
    p = design(c, call("GET", f"/products/{sample['id']}"), 20)
    _, d = apply(p, c, {"Q4": [{"kcdCode": "E11.9", "diagnosedOn": "2024-03-01", "treatment": "MEDICATION",
                                "treatmentDays": 600, "cured": False}]})
    assert d["summary"]["status"] == "UNDER_REVIEW", d["summary"]
    case = d["uwCase"]
    choices = {str(r["coverageId"]): ("DECLINE" if r["coverageName"] == "실손의료비" else "ACCEPT_STANDARD")
               for r in case["results"]}
    call("POST", f"/uw/cases/{case['id']}/decision", {"choices": choices, "underwriter": "시나리오", "comment": "실손 거절"})
    after = call("GET", f"/applications/{d['summary']['id']}")
    assert after["summary"]["status"] == "ISSUED" and after["contractId"], after["summary"]
    return "본사 이관 → 일부 담보 승인"


@scenario("S05 최고령(만 71세): 일반 종신 나이 초과 / 간편심사형 가입 시 고령자 보호 확인 필수")
def _():
    c = customer("최고령")
    normal = product("WHOLE_LIFE", lambda x: not x["simplifiedUw"])
    try:
        design(c, normal, 10)
        raise AssertionError("나이 초과인데 설계됨")
    except ApiError as e:
        assert "가입 가능 나이" in str(e.detail), e.detail
    simple = product("WHOLE_LIFE", lambda x: x["simplifiedUw"] and x["maxEntryAge"] >= c["insuranceAge"])
    p = design(c, simple, 10)
    try:
        apply(p, c, agreements=["EXPLAINED", "TERMS_RECEIVED", "DISCLOSURE_TRUE", "PRIVACY"])
        raise AssertionError("고령자 보호 확인 없이 청약됨")
    except ApiError as e:
        assert "확인하지 않은 필수 항목" in str(e.detail), e.detail
    doc, d = apply(p, c)
    assert "SENIOR_PROTECTION" in [a["code"] for a in doc["requiredAgreements"]]
    return f"간편심사형 청약 {d['summary']['statusLabel']}"


@scenario("S06 한골절: 골절 고지 → 골절 부위 부담보 계약 → 골절 청구 시 부담보 해당")
def _():
    c = customer("한골절")
    sample = call("GET", "/products?q=" + urllib.parse.quote("샘플") + "&size=5")["content"][0]
    p = design(c, call("GET", f"/products/{sample['id']}"), 20)
    _, d = apply(p, c, {"Q3": [{"kcdCode": "S82.2", "diagnosedOn": time.strftime("%Y-%m-%d", time.localtime(time.time() - 180 * 86400)),
                                "treatment": "SURGERY", "treatmentDays": 10, "cured": False}]})
    d = issued(d)
    claim = call("POST", "/claims", {"contractId": d["contractId"], "type": "OUTPATIENT",
                                     "treatmentDate": time.strftime("%Y-%m-%d"), "diagnosis": "골절 재활",
                                     "kcdCode": "S82.2", "amount": 80000})
    assert any("부담보 해당" in n for n in claim["checkNotes"]), claim["checkNotes"]
    return "부담보 해당 표시"


def _equity_funds(prod):
    eq = [f["code"] for f in prod["funds"] if f["riskGrade"] <= 2][0]
    return [{"fundCode": eq, "pct": 70}, {"fundCode": "F-BOND", "pct": 30}]


@scenario("S07 오안정(안정형): 변액연금 주식형 → 부적합, 확인 없이 발행 불가 / 확인 후 발행")
def _():
    c = customer("오안정")
    assert diagnose(c, [1, 1, 2, 2, 2])["investmentProfile"] == "STABLE"
    prod = product("VARIABLE_ANNUITY", lambda x: x["maxEntryAge"] >= c["insuranceAge"])
    funds = _equity_funds(prod)
    try:
        design(c, prod, 20, contribution=300000, annuity_age=65, funds=funds)
        raise AssertionError("부적합인데 발행됨")
    except ApiError as e:
        assert "부적합" in str(e.detail), e.detail
    p = design(c, prod, 20, contribution=300000, annuity_age=65, funds=funds, ack=True)
    assert p["suitabilityLabel"].startswith("부적합"), p["suitabilityLabel"]
    return p["suitabilityLabel"]


@scenario("S08 정전문(전문금융소비자): 변액 적합성 원칙 적용 제외")
def _():
    c = customer("정전문")
    prod = product("VARIABLE_ANNUITY", lambda x: x["maxEntryAge"] >= c["insuranceAge"])
    p = design(c, prod, 10, contribution=500000, annuity_age=65, funds=_equity_funds(prod))
    assert p["suitabilityLabel"].startswith("전문금융소비자"), p["suitabilityLabel"]
    return p["suitabilityLabel"]


@scenario("S09 강공격(공격투자형): 변액유니버설 주식형 적합 → 청약 시 원금손실 확인 필수")
def _():
    c = customer("강공격")
    assert diagnose(c, [5, 5, 5, 5, 5])["investmentProfile"] == "AGGRESSIVE"
    prod = product("VARIABLE_UNIVERSAL")
    p = design(c, prod, 20, contribution=300000, funds=_equity_funds(prod))
    assert p["suitabilityLabel"] == "적합", p["suitabilityLabel"]
    doc, d = apply(p, c)
    assert "VARIABLE_RISK" in [a["code"] for a in doc["requiredAgreements"]]
    return f"청약 {d['summary']['statusLabel']}"


@scenario("S10 윤위험(고위험 직업): 운전자보험 상해 담보 50% 할증")
def _():
    c = customer("윤위험")
    p = design(c, product("DRIVER", lambda x: not x["simplifiedUw"]), 20)
    _, d = apply(p, c)
    rating = {r["coverageName"]: r["ratingPct"] for r in d["uwCase"]["results"]}
    assert all(v == 50 for v in rating.values()), rating
    k = call("GET", f"/contracts/{d['contractId']}")
    assert k["monthlyPremium"] > p["monthlyPremium"], (k["monthlyPremium"], p["monthlyPremium"])
    return f"설계 {p['monthlyPremium']:,}원 → 계약 {k['monthlyPremium']:,}원"


@scenario("S11 서임신: 임신 고지 → 수술·입원 담보 임신·출산 부담보")
def _():
    c = customer("서임신")
    p = design(c, product("HEALTH", lambda x: not x["simplifiedUw"]), 20)
    _, d = apply(p, c, {"Q5": []})
    rules = {rule for r in d["uwCase"]["results"] for rule in r["matchedRules"]}
    assert "R-PRG-01" in rules, rules
    return "임신·출산 부담보"


@scenario("S12 문다청구: 최근 1년 청구 4회 → 실손 청약 본사 이관")
def _():
    c = customer("문다청구")
    sample = call("GET", "/products?q=" + urllib.parse.quote("샘플") + "&size=5")["content"][0]
    p = design(c, call("GET", f"/products/{sample['id']}"), 20)
    _, d = apply(p, c)
    rules = {rule for r in d["uwCase"]["results"] for rule in r["matchedRules"]}
    assert "R-CLM-01" in rules and d["summary"]["status"] == "UNDER_REVIEW", (rules, d["summary"]["status"])
    return "R-CLM-01 본사 이관"


@scenario("S13 유어린(만 10세): 종신 가입 불가 / 어린이보험 가입")
def _():
    c = customer("유어린")
    try:
        design(c, product("WHOLE_LIFE"), 20)
        raise AssertionError("나이 미달인데 설계됨")
    except ApiError as e:
        assert "가입 가능 나이" in str(e.detail), e.detail
    p = design(c, product("CHILD"), 20)
    _, d = apply(p, c)
    return f"어린이보험 {d['summary']['statusLabel']}"


@scenario("S14 검증: 서명자 불일치 / 설계서 재사용 / 고지 상세 누락")
def _():
    c = customer("김정상")
    p = design(c, product("CANCER", lambda x: not x["simplifiedUw"]), 20)
    qn = questionnaire(p["productId"])
    no = [{"questionId": q["id"], "answer": False, "details": []} for q in qn["questions"]]
    bad = [dict(a, answer=(a["questionId"] == qn["questions"][3]["id"])) for a in no]
    for body, expect in [
        ({"proposalId": p["id"], "questionnaireId": qn["id"], "answers": no,
          "signature": {"signerName": "다른사람", "agreements": ALL_AGREEMENTS}}, "서명자 이름"),
        ({"proposalId": p["id"], "questionnaireId": qn["id"], "answers": bad,
          "signature": {"signerName": c["name"], "agreements": ALL_AGREEMENTS}}, "상세를 1건 이상"),
    ]:
        try:
            call("POST", "/applications", body)
            raise AssertionError(f"'{expect}' 오류가 나야 함")
        except ApiError as e:
            assert expect in str(e.detail), e.detail
    apply(p, c)
    try:
        apply(p, c)
        raise AssertionError("설계서 재사용됨")
    except ApiError as e:
        assert "이미 청약으로 전환" in str(e.detail), e.detail
    return "검증 3건 정상"


@scenario("S16 보험나이·상령일: 설계서 유효기간은 상령일 전날을 넘지 않고, 상령 후 보험료가 표시된다")
def _():
    c = customer("김정상")
    p = design(c, product("WHOLE_LIFE", lambda x: not x["simplifiedUw"]), 20)
    assert p["insuredAge"] == c["insuranceAge"], (p["insuredAge"], c["insuranceAge"])
    assert p["validUntil"] < p["nextAgeUpDate"], (p["validUntil"], p["nextAgeUpDate"])
    assert p["premiumAfterAgeUp"] and p["premiumAfterAgeUp"] > p["monthlyPremium"], p["premiumAfterAgeUp"]
    assert p["firstPremium"] == p["monthlyPremium"]
    assert abs(p["convertedPremium"] - p["monthlyPremium"] * p["conversionRatePct"] / 100) <= 10, p["convertedPremium"]
    return f"보험나이 {p['insuredAge']}세, 상령일 {p['nextAgeUpDate']}, {p['monthlyPremium']:,} → {p['premiumAfterAgeUp']:,}원, 환산 {p['convertedPremium']:,}원"


@scenario("S15 전산심사 사전 실행: 김정상 자동 승인 예상 / 박당뇨 당뇨 고지 시 본사 이관 예상 (저장 안 됨)")
def _():
    def simulate(cust, yes=None):
        prod = product("HEALTH", lambda x: not x["simplifiedUw"])
        p = design(cust, prod, 20)
        qn = questionnaire(prod["summary"]["id"])
        answers = answers_of(qn, yes)
        return p, call("POST", "/applications/underwrite-preview",
                       {"proposalId": p["id"], "questionnaireId": qn["id"], "answers": answers})
    _, ok = simulate(customer("김정상"))
    assert ok["verdict"] == "자동 승인 예상" and ok["signal"] == "GREEN", (ok["verdict"], ok["signal"])
    p, ref = simulate(customer("박당뇨"), {"Q4": [{"kcdCode": "E11.9", "diagnosedOn": "2024-01-01", "treatment": "MEDICATION",
                                                 "treatmentDays": 300, "cured": False}]})
    assert ref["verdict"] == "본사 이관 예상" and ref["signal"] == "YELLOW", (ref["verdict"], ref["signal"])
    assert call("GET", f"/proposals/{p['id']}")["status"] == "ISSUED", "사전 실행이 설계서를 전환함"
    return "자동 승인 예상 / 본사 이관 예상"


@scenario("S17 박당뇨: 고지 기반 선심사 — 표준 종신 노랑(본사 심사) / 간편심사형 종신 초록, 질문지 혼용 불가")
def _():
    c = customer("박당뇨")
    dm = [{"kcdCode": "E11.9", "diagnosedOn": days_ago(3 * 365), "treatment": "MEDICATION", "treatmentDays": 300, "cured": False}]
    std = product("WHOLE_LIFE", lambda x: not x["simplifiedUw"] and x["minEntryAge"] <= c["insuranceAge"] <= x["maxEntryAge"])
    std_qn = questionnaire(std["summary"]["id"])
    assert not std_qn["simplified"] and len(std_qn["questions"]) == 5, std_qn["code"]
    r = call("POST", "/uw/pre", {"customerId": c["id"], "productId": std["summary"]["id"], "questionnaireId": std_qn["id"],
                                 "answers": answers_of(std_qn, {"Q4": dm})})
    assert r["uwCase"]["signal"] == "YELLOW", r["uwCase"]["signal"]
    assert "간편심사형" in r["hint"], r["hint"]
    assert r["uwCase"]["preDisclosure"]["answers"]["Q4"] is True

    smp = product("WHOLE_LIFE", lambda x: x["simplifiedUw"] and x["minEntryAge"] <= c["insuranceAge"] <= x["maxEntryAge"])
    smp_qn = questionnaire(smp["summary"]["id"])
    assert smp_qn["simplified"] and [q["code"] for q in smp_qn["questions"]] == ["S1", "S2", "S3"], smp_qn["code"]
    r = call("POST", "/uw/pre", {"customerId": c["id"], "productId": smp["summary"]["id"], "questionnaireId": smp_qn["id"],
                                 "answers": answers_of(smp_qn)})
    assert r["uwCase"]["signal"] == "GREEN", [(x["coverageName"], x["matchedRules"]) for x in r["uwCase"]["results"]]
    try:
        call("POST", "/uw/pre", {"customerId": c["id"], "productId": smp["summary"]["id"], "questionnaireId": std_qn["id"],
                                 "answers": answers_of(std_qn)})
        raise AssertionError("간편심사형 상품에 표준 질문지가 허용됨")
    except ApiError as e:
        assert "간편고지" in str(e.detail), e.detail
    p = design(c, smp, 20)
    _, d = apply(p, c)
    d = issued(d)
    return "표준 노랑 → 간편 초록 → 간편심사형 계약 성립"


@scenario("S18 한골절: 간편심사형 — 동의 조회만으로 빨강(2년 내 수술) / S2 수술 고지도 빨강")
def _():
    c = customer("한골절")
    smp = product("HEALTH", lambda x: x["simplifiedUw"] and x["minEntryAge"] <= c["insuranceAge"] <= x["maxEntryAge"]) \
        if any(x["simplifiedUw"] for x in call("GET", "/products?type=HEALTH&size=100")["content"]) \
        else product("WHOLE_LIFE", lambda x: x["simplifiedUw"] and x["minEntryAge"] <= c["insuranceAge"] <= x["maxEntryAge"])
    call("POST", f"/customers/{c['id']}/consents", {"type": "PRE_UW_INQUIRY"})
    r = call("POST", "/uw/pre", {"customerId": c["id"], "productId": smp["summary"]["id"]})
    rules = {rule for x in r["uwCase"]["results"] for rule in x["matchedRules"]}
    assert r["uwCase"]["signal"] == "RED" and "R-SMP-12" in rules, (r["uwCase"]["signal"], rules)
    qn = questionnaire(smp["summary"]["id"])
    r = call("POST", "/uw/pre", {"customerId": c["id"], "productId": smp["summary"]["id"], "questionnaireId": qn["id"],
                                 "answers": answers_of(qn, {"S2": [{"kcdCode": "S82.2", "diagnosedOn": days_ago(180),
                                                                   "treatment": "SURGERY", "treatmentDays": 10, "cured": False}]})})
    assert r["uwCase"]["signal"] == "RED" and "R-SMP-02" in {x for y in r["uwCase"]["results"] for x in y["matchedRules"]}
    return "조회 이력 R-SMP-12 빨강, 고지 S2 빨강"


@scenario("S19 특약 연관 규칙: 암입원일당은 암수술과 함께 / 암수술 ≤ 암진단 50% / 재해사망 ≤ 일반사망")
def _():
    c = customer("김정상")
    can = product("CANCER", lambda x: not x["simplifiedUw"])
    cov = {x["code"].rsplit("-", 1)[1]: x for x in can["coverages"]}
    assert {r["type"] for r in can["rules"]} == {"REQUIRES", "MAX_RATIO"}, can["rules"]
    def expect_error(amounts, text):
        try:
            design(c, can, 20, amounts)
            raise AssertionError(f"규칙 위반인데 설계됨: {text}")
        except ApiError as e:
            assert text in str(e.detail), e.detail
    expect_error([{"coverageId": cov["CAN"]["id"], "amount": 30_000_000}, {"coverageId": cov["CHO"]["id"], "amount": 30_000}], "함께 가입")
    expect_error([{"coverageId": cov["CAN"]["id"], "amount": 10_000_000}, {"coverageId": cov["CSU"]["id"], "amount": 10_000_000}], "50% 이하")
    ok = design(c, can, 20, [{"coverageId": cov["CAN"]["id"], "amount": 20_000_000}, {"coverageId": cov["CSU"]["id"], "amount": 10_000_000},
                            {"coverageId": cov["CHO"]["id"], "amount": 30_000}])
    wl = product("WHOLE_LIFE", lambda x: not x["simplifiedUw"])
    w = {x["code"].rsplit("-", 1)[1]: x for x in wl["coverages"]}
    try:
        design(c, wl, 20, [{"coverageId": w["DTH"]["id"], "amount": 50_000_000}, {"coverageId": w["ADT"]["id"], "amount": 100_000_000}])
        raise AssertionError("재해사망 > 일반사망인데 설계됨")
    except ApiError as e:
        assert "100% 이하" in str(e.detail), e.detail
    return f"위반 3건 차단, 경계값(암수술 = 암진단 50%) 설계 {ok['monthlyPremium']:,}원"


@scenario("S20 플랜: 기본 플랜(실속·표준·고급)으로 설계 / 설계사 플랜 저장·검증·삭제")
def _():
    c = customer("김정상")
    can = product("CANCER", lambda x: not x["simplifiedUw"])
    plans = {p["name"]: p for p in can["plans"]}
    assert {"실속형", "표준형", "고급형"} <= set(plans), list(plans)
    premiums = {n: design(c, can, 20, [{"coverageId": i["coverageId"], "amount": i["amount"]} for i in plans[n]["items"]])["monthlyPremium"]
                for n in ("실속형", "표준형", "고급형")}
    assert premiums["실속형"] < premiums["표준형"] < premiums["고급형"], premiums
    cov = {x["code"].rsplit("-", 1)[1]: x for x in can["coverages"]}
    try:
        call("POST", f"/products/{can['summary']['id']}/plans", {"name": "규칙위반", "items": [
            {"coverageId": cov["CAN"]["id"], "amount": 30_000_000}, {"coverageId": cov["CHO"]["id"], "amount": 30_000}]})
        raise AssertionError("규칙 위반 플랜이 저장됨")
    except ApiError as e:
        assert "함께 가입" in str(e.detail), e.detail
    name = "시나리오 " + time.strftime("%H%M%S")
    saved = call("POST", f"/products/{can['summary']['id']}/plans", {"name": name, "items": [
        {"coverageId": cov["CAN"]["id"], "amount": 50_000_000}, {"coverageId": cov["CSU"]["id"], "amount": 10_000_000}]})
    assert saved["custom"]
    assert any(p["id"] == saved["id"] for p in call("GET", f"/products/{can['summary']['id']}")["plans"])
    call("DELETE", f"/products/{can['summary']['id']}/plans/{saved['id']}")
    try:
        call("DELETE", f"/products/{can['summary']['id']}/plans/{plans['표준형']['id']}")
        raise AssertionError("기본 플랜이 삭제됨")
    except ApiError as e:
        assert "삭제할 수 없" in str(e.detail), e.detail
    return " < ".join(f"{n} {v:,}원" for n, v in premiums.items())


@scenario("S21 임시저장: 검증 없이 저장 → 덮어쓰기 → 설계서 발행 시 자동 삭제")
def _():
    c = customer("김정상")
    hp = product("HEALTH", lambda x: not x["simplifiedUw"])
    pid = hp["summary"]["id"]
    for d in call("GET", f"/proposal-drafts?customerId={c['id']}&productId={pid}"):
        call("DELETE", f"/proposal-drafts/{d['id']}")
    half = {"paymentTerm": 20, "coverages": [{"coverageId": hp["coverages"][0]["id"], "amount": 1}], "planName": None}
    first = call("PUT", "/proposal-drafts", {"customerId": c["id"], "productId": pid, "payload": half})
    second = call("PUT", "/proposal-drafts", {"customerId": c["id"], "productId": pid, "payload": dict(half, planName="표준형")})
    assert first["id"] == second["id"] and second["payload"]["planName"] == "표준형", (first["id"], second["id"])
    assert any(d["id"] == first["id"] for d in call("GET", f"/proposal-drafts?customerId={c['id']}"))
    design(c, hp, 20)
    assert call("GET", f"/proposal-drafts?customerId={c['id']}&productId={pid}") == [], "발행 후에도 임시저장이 남음"
    return "고객·상품당 1건 덮어쓰기, 발행 시 삭제"


@scenario("S22 가상고객 즉시 생성: 가상 주민번호(마스킹·암호화) → 열람 시 사유·감사 로그 / 가망 → 계약 → 철회 시 가망")
def _():
    c = call("POST", "/customers/virtual", {"minAge": 30, "maxAge": 40, "gender": "F", "driving": "PRIVATE"})
    assert c["virtual"] and c["gender"] == "F" and 30 <= c["age"] <= 40 and c["driving"] == "PRIVATE", c
    assert c["status"] == "PROSPECT", c["status"]
    masked = c["rrnMasked"]
    assert masked.endswith("******") and masked[:6] == c["birthDate"][2:].replace("-", "") and masked[7] in "24", masked
    try:
        call("POST", f"/customers/{c['id']}/rrn", {"reason": ""})
        raise AssertionError("사유 없이 열람됨")
    except ApiError as e:
        assert e.status in (400, 422), e
    rrn = call("POST", f"/customers/{c['id']}/rrn", {"reason": "시나리오 본인확인"})["rrn"]
    assert rrn.startswith(masked[:8]) and rrn[8:10] == "99", rrn
    detail = call("GET", f"/customers/{c['id']}")
    assert detail["rrnAccessLogs"][0]["reason"] == "시나리오 본인확인"
    p = design(c, product("HEALTH", lambda x: not x["simplifiedUw"] and x["minEntryAge"] <= c["insuranceAge"] <= x["maxEntryAge"]), 20)
    _, d = apply(p, c)
    d = issued(d)
    assert call("GET", f"/customers/{c['id']}")["customer"]["status"] == "CONTRACTED"
    call("POST", f"/contracts/{d['contractId']}/withdraw")
    assert call("GET", f"/customers/{c['id']}")["customer"]["status"] == "PROSPECT"
    return f"{c['name']} {masked} → 계약고객 → 철회 후 가망고객"


@scenario("S23 운전: 이륜자동차 운전자 운전자보험 상해 할증(R-DRV-01) / 비운전자는 운전자보험 불가")
def _():
    moto = call("POST", "/customers/virtual", {"minAge": 30, "maxAge": 45, "driving": "MOTORCYCLE"})
    drv = product("DRIVER", lambda x: not x["simplifiedUw"] and x["minEntryAge"] <= moto["insuranceAge"] <= x["maxEntryAge"])
    p = design(moto, drv, 20)
    _, d = apply(p, moto)
    d = issued(d)
    rules = {r for x in d["uwCase"]["results"] for r in x["matchedRules"]}
    assert "R-DRV-01" in rules and all(x["ratingPct"] >= 30 for x in d["uwCase"]["results"]), d["uwCase"]["results"]
    none = call("POST", "/customers/virtual", {"minAge": 30, "maxAge": 45, "driving": "NONE"})
    try:
        design(none, drv, 20)
        raise AssertionError("비운전자가 운전자보험에 설계됨")
    except ApiError as e:
        assert "비운전자" in str(e.detail), e.detail
    return f"이륜 할증 {d['uwCase']['results'][0]['ratingPct']}%, 비운전 차단"


@scenario("S24 가족관계: 양방향 등록 / 배우자 1명 / 부모는 나이가 많아야 함 / 해제")
def _():
    me = call("POST", "/customers/virtual", {"minAge": 35, "maxAge": 40})
    spouse = call("POST", "/customers/virtual", {"minAge": 35, "maxAge": 40})
    other = call("POST", "/customers/virtual", {"minAge": 35, "maxAge": 40})
    kid = call("POST", "/customers/virtual", {"minAge": 5, "maxAge": 10})
    call("POST", f"/customers/{me['id']}/relations", {"relativeId": spouse["id"], "relation": "SPOUSE"})
    call("POST", f"/customers/{me['id']}/relations", {"relativeId": kid["id"], "relation": "CHILD"})
    fam = {f["name"]: f["relation"] for f in call("GET", f"/customers/{kid['id']}")["family"]}
    assert fam == {me["name"]: "PARENT"} or fam.get(me["name"]) == "PARENT", fam
    for body, text in (({"relativeId": other["id"], "relation": "SPOUSE"}, "배우자는 1명"),
                       ({"relativeId": kid["id"], "relation": "PARENT"}, "이미 등록")):
        try:
            call("POST", f"/customers/{me['id']}/relations", body)
            raise AssertionError(f"허용되면 안 됨: {text}")
        except ApiError as e:
            assert text in str(e.detail), e.detail
    try:
        call("POST", f"/customers/{other['id']}/relations", {"relativeId": kid["id"], "relation": "PARENT"})
        raise AssertionError("어린 고객이 부모로 등록됨")
    except ApiError as e:
        assert "나이" in str(e.detail), e.detail
    call("DELETE", f"/customers/{me['id']}/relations/{spouse['id']}")
    assert call("GET", f"/customers/{spouse['id']}")["family"] == []
    return "양방향·제약 3건·해제 확인"


@scenario("S25 변액 적합성: 미진단이면 설계 불가 → 당일 진단 후 설계, 설계서에 진단 결과·시각 스냅샷")
def _():
    c = call("POST", "/customers/virtual", {"minAge": 30, "maxAge": 45})
    prod = product("VARIABLE_UNIVERSAL", lambda x: x["minEntryAge"] <= c["insuranceAge"] <= x["maxEntryAge"])
    try:
        design(c, prod, 20, contribution=300000, funds=_equity_funds(prod))
        raise AssertionError("미진단인데 변액 설계됨")
    except ApiError as e:
        assert "투자성향" in str(e.detail), e.detail
    c = diagnose(c, [4, 4, 4, 4, 4])
    assert c["profiledToday"], c
    p = design(c, prod, 20, contribution=300000, funds=_equity_funds(prod), ack=True)
    assert p["investmentProfileLabel"] == c["investmentProfileLabel"] and p["profiledAt"], (p["investmentProfileLabel"], p["profiledAt"])
    assert len(call("GET", f"/customers/{c['id']}")["assessments"]) == 1
    return f"{p['investmentProfileLabel']} 진단 → {p['suitabilityLabel']}"


def corporate(name):
    return next(c for c in call("GET", "/corporates?q=" + urllib.parse.quote(name))["content"] if c["name"] == name)


@scenario("S26 법인 단체설계: Ax테크(30명) 건강보험 표준형 → 개인별 요율 합계 − 단체 할인 5% → 단체 설계서 발행")
def _():
    corp = corporate("Ax테크(가상)")
    hp = product("HEALTH", lambda x: not x["simplifiedUw"] and x["minEntryAge"] <= 20 and x["maxEntryAge"] >= 63)
    plan = next(p for p in hp["plans"] if p["name"] == "표준형")
    body = {"corporateId": corp["id"], "productId": hp["summary"]["id"], "paymentTerm": 20, "items": plan["items"]}
    q = call("POST", "/group-proposals/quote", body)
    assert q["insuredCount"] >= 10 and q["discountPct"] == 5, (q["insuredCount"], q["discountPct"])
    assert q["grossPremium"] == sum(l["monthlyPremium"] for l in q["lines"] if l["included"])
    assert abs(q["monthlyPremium"] - q["grossPremium"] * 0.95) <= 10, (q["monthlyPremium"], q["grossPremium"])
    g = call("POST", "/group-proposals", body)
    assert g["proposalNo"].startswith("GP") and len(g["lines"]) == len(q["lines"])
    assert any(x["id"] == g["id"] for x in call("GET", f"/corporates/{corp['id']}")["groupProposals"])
    return f"{q['insuredCount']}명 {q['grossPremium']:,}원 → 할인 {q['discountPct']}% → {q['monthlyPremium']:,}원"


@scenario("S27 단체설계 제외·최소 인원: 운전자보험은 비운전 종업원 제외 / 소담카페(4명)는 5명 미달로 불가")
def _():
    corp = call("POST", "/corporates/virtual", {"industry": "물류·운송", "employees": 20})
    members = call("GET", f"/corporates/{corp['id']}")["members"]
    assert len(members) == 20
    # 비운전 종업원 1명을 확실히 만든다
    m = members[0]
    cust = call("GET", f"/customers/{m['customerId']}")["customer"]
    call("PUT", f"/customers/{cust['id']}", {"name": cust["name"], "phone": cust["phone"], "consumerType": cust["consumerType"],
                                             "occupation": cust["occupation"], "jobRiskGrade": cust["jobRiskGrade"], "driving": "NONE"})
    drv = product("DRIVER", lambda x: not x["simplifiedUw"])
    items = [{"coverageId": c["id"], "amount": c["defaultAmount"]} for c in drv["coverages"] if c["required"]]
    q = call("POST", "/group-proposals/quote", {"corporateId": corp["id"], "productId": drv["summary"]["id"], "paymentTerm": 20, "items": items})
    excluded = [l for l in q["lines"] if not l["included"]]
    assert any(l["customerId"] == cust["id"] and "비운전자" in l["excludedReason"] for l in excluded), excluded
    cafe = corporate("소담카페(가상)")
    try:
        call("POST", "/group-proposals/quote", {"corporateId": cafe["id"], "productId": drv["summary"]["id"], "paymentTerm": 20, "items": items})
        raise AssertionError("4명 법인이 단체 설계됨")
    except ApiError as e:
        assert "5명 이상" in str(e.detail), e.detail
    return f"제외 {len(excluded)}명(비운전·나이 등), 4명 법인 차단"



@scenario("S28 박당뇨: 설계 중 비교설계 — 표준 종신 ↔ 간편심사형 종신 예상 결과 나란히 / 동의 없으면 불가")
def _():
    c = customer("박당뇨")
    call("POST", f"/customers/{c['id']}/consents", {"type": "PRE_UW_INQUIRY"})
    std = product("WHOLE_LIFE", lambda x: not x["simplifiedUw"] and x["minEntryAge"] <= c["insuranceAge"] <= x["maxEntryAge"])
    r = call("POST", "/uw/pre/compare", {"customerId": c["id"], "productId": std["summary"]["id"]})
    sel, alt = r["selected"]["uwCase"], r["alternative"]["uwCase"]
    assert sel["productId"] == std["summary"]["id"] and not sel["simplifiedUw"], sel["productName"]
    assert alt["simplifiedUw"] and alt["productId"] != sel["productId"], alt["productName"]
    assert r["selected"]["inquired"], "조회 이력이 비어 있음 (외부기관 당뇨 투약)"
    assert sel["signal"] != "GREEN" and r["recommendation"], (sel["signal"], r["recommendation"])
    # 반대 방향(간편 → 표준)도 비교 상대를 찾는다
    back = call("POST", "/uw/pre/compare", {"customerId": c["id"], "productId": alt["productId"]})
    assert not back["alternative"]["uwCase"]["simplifiedUw"]

    fresh = call("POST", "/customers/virtual", {"minAge": 30, "maxAge": 40})
    try:
        call("POST", "/uw/pre/compare", {"customerId": fresh["id"], "productId": std["summary"]["id"]})
        raise AssertionError("동의 없이 비교설계됨")
    except ApiError as e:
        assert "정보조회 동의" in str(e.detail), e.detail
    return f"표준 {sel['signal']} ↔ 간편 {alt['signal']} · {r['recommendation'][:30]}…"

if __name__ == "__main__":
    width = max(len(r[1]) for r in results)
    for status, name, note, sec in results:
        print(f"[{status:5}] {name:<{width}}  {sec:5.2f}s  {note}")
    failed = [r for r in results if r[0] != "PASS"]
    print(f"\n{len(results) - len(failed)}/{len(results)} passed")
    sys.exit(1 if failed else 0)
