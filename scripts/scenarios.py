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


if __name__ == "__main__":
    width = max(len(r[1]) for r in results)
    for status, name, note, sec in results:
        print(f"[{status:5}] {name:<{width}}  {sec:5.2f}s  {note}")
    failed = [r for r in results if r[0] != "PASS"]
    print(f"\n{len(results) - len(failed)}/{len(results)} passed")
    sys.exit(1 if failed else 0)
