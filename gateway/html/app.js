'use strict';

// ============================================================ 공통
const view = document.getElementById('view');
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const won = (n) => (n == null ? '-' : Number(n).toLocaleString('ko-KR') + '원');
const man = (n) => (n == null ? '-' : (Number(n) / 10_000).toLocaleString('ko-KR') + '만원');
const day = (s) => (s ? String(s).slice(0, 10) : '-');
const dt = (s) => (s ? String(s).slice(0, 16).replace('T', ' ') : '-');
const maskPhone = (p) => (p ? p.replace(/^(\d{3})-(\d{3,4})-(\d{4})$/, '$1-****-$3') : '-');
const today = () => new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10);

async function api(path, options = {}) {
  const res = await fetch(path, { headers: { 'content-type': 'application/json' }, ...options });
  if (res.status === 429) throw new Error('요청이 많아요. 잠시 후 다시 시도해 주세요. (공용 자원 보호를 위한 제한)');
  const body = await res.json().catch(() => null);
  if (!res.ok) {
    const d = body?.detail;
    throw new Error(Array.isArray(d) ? d.map((x) => x.msg).join(', ') : d || `요청 실패 (${res.status})`);
  }
  return body;
}
const post = (path, body) => api(path, { method: 'POST', body: JSON.stringify(body) });

const OUTCOME = {
  STANDARD: ['표준체', 't-ok'], CONDITIONAL: ['조건부 인수', 't-warn'], REFER: ['본사 이관', 't-info'],
  POSTPONE: ['연기', 't-warn'], DECLINE: ['거절', 't-bad'],
};
const APP_STATUS = { UNDER_REVIEW: 't-info', ISSUED: 't-ok', POSTPONED: 't-warn', DECLINED: 't-bad' };
const CLAIM_STATUS = {
  RECEIVED: ['접수', 't-muted'], AI_REVIEWING: ['AI 검토 중', 't-info'], AI_REVIEWED: ['AI 검토 완료', 't-info'],
  MANUAL_REVIEW: ['수동심사', 't-warn'], APPROVED: ['지급', 't-ok'], REJECTED: ['부지급', 't-bad'],
};
const TREATMENT = { OUTPATIENT: '외래', INPATIENT: '입원', SURGERY: '수술', MEDICATION: '투약' };
const tag = (label, cls) => `<span class="tag ${cls}">${esc(label)}</span>`;
const outcomeTag = (o) => tag(...(OUTCOME[o] || [o, 't-muted']));
const rules = (list) => (list || []).map((r) => `<span class="rule">${esc(r)}</span>`).join(' ');
const exclusionText = (ex) => (ex || []).map((e) =>
  `부담보: ${esc(e.label)} ${e.months ? e.months + '개월' : '~' + esc(e.until)}`).join('<br>');

function renderMarkdown(md) {
  const lines = esc(md).split('\n');
  let html = '', inList = false;
  for (const raw of lines) {
    const line = raw.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
    const li = line.match(/^\s*(?:[-*]|\d+\.)\s+(.*)$/);
    if (li) { if (!inList) { html += '<ul>'; inList = true; } html += `<li>${li[1]}</li>`; continue; }
    if (inList) { html += '</ul>'; inList = false; }
    const hd = line.match(/^(#{1,4})\s+(.*)$/);
    if (hd) html += `<h4>${hd[2]}</h4>`; else if (line.trim()) html += `<p>${line}</p>`;
  }
  return `<div class="md">${html}${inList ? '</ul>' : ''}</div>`;
}

function showError(el, e) { el.textContent = e.message; el.hidden = false; }
function table(headers, rows, empty = '데이터가 없습니다.') {
  if (!rows.length) return `<div class="empty">${empty}</div>`;
  return `<div class="table-wrap"><table><thead><tr>${headers.map((h) => `<th>${h}</th>`).join('')}</tr></thead>
    <tbody>${rows.join('')}</tbody></table></div>`;
}

let cache = {};
async function cached(key, path) { return (cache[key] ??= await api(path)); }
const products = () => cached('products', '/core/products');
const questionnaire = () => cached('questionnaire', '/core/questionnaires/current');
const kcdList = () => cached('kcd', '/core/kcd');

async function customerOptions(selected) {
  const page = await api('/core/customers?page=0');
  return page.content.map((c) =>
    `<option value="${c.id}" ${String(c.id) === String(selected) ? 'selected' : ''}>${esc(c.name)} (만 ${c.age}세, ${c.gender === 'M' ? '남' : '여'})</option>`).join('');
}

// ============================================================ 라우터
const routes = [
  [/^\/$/, home],
  [/^\/customers$/, customers],
  [/^\/customers\/(\d+)$/, customerDetail],
  [/^\/pre-uw$/, preUw],
  [/^\/apply$/, apply],
  [/^\/applications$/, applications],
  [/^\/applications\/(\d+)$/, applicationDetail],
  [/^\/uw$/, uwInbox],
  [/^\/uw\/(\d+)$/, uwDetail],
  [/^\/claims$/, claims],
  [/^\/qa$/, qa],
];
let timers = [];
function clearTimers() { timers.forEach(clearTimeout); timers = []; }

async function route() {
  clearTimers();
  const [path, query] = (location.hash.slice(1) || '/').split('?');
  const params = new URLSearchParams(query || '');
  document.querySelectorAll('nav.side a').forEach((a) => {
    const href = a.getAttribute('href').slice(1);
    a.classList.toggle('on', href === '/' ? path === '/' : path.startsWith(href));
  });
  for (const [re, fn] of routes) {
    const m = path.match(re);
    if (m) {
      view.innerHTML = '<div class="empty">불러오는 중…</div>';
      try { await fn(params, ...m.slice(1)); } catch (e) { view.innerHTML = `<div class="error">${esc(e.message)}</div>`; }
      window.scrollTo(0, 0);
      return;
    }
  }
  view.innerHTML = '<div class="empty">페이지를 찾을 수 없습니다.</div>';
}
window.addEventListener('hashchange', route);

// ============================================================ 개요
async function home() {
  view.innerHTML = `
  <h1>보험 업무 시스템 데모</h1>
  <p class="sub">고객관리 → 선심사 → 청약·알릴의무 고지 → 전산심사 → 본사심사(AI 의견) → 계약 → 청구 점검</p>
  <div class="card"><div class="flow">[채널계]  고객관리 ─ 선심사(동의 → 청구이력·외부기관 조회 → 예상결과)
          청약 작성 ─ 알릴의무 고지(문항 버전 고정) ─ 제출
                                  │
[심사]    전산심사(룰 엔진, 담보별 결과) ─┬─ 전 담보 표준/조건부 → 자동 승인 → 계약 성립
                                    ├─ 본사 이관 → AI 참고의견 → 심사자 담보별 결정 → 계약 성립
                                    └─ 연기 / 거절
[보상]    청구 접수 ─ 부담보·고지의무 위반 점검 ─ AI 약관 검토 ─ 심사자 지급 결정</div></div>
  <div class="card"><h2>체험 시나리오 (가상 고객)</h2>
  ${table(['고객', '숨은 이력 (외부기관 Mock)', '해볼 것'], [
    ['김정상', '없음', '선심사·청약 모두 표준체 → 자동 승인'],
    ['이위염', '5개월 전 위염 외래, 치료 중', '선심사 → 위·십이지장 부담보 예상. 고지 안 하고 청약 → 승인 → 위염(K29.7)으로 청구하면 <b>고지의무 위반 의심</b>'],
    ['박당뇨', '3년 전부터 당뇨 투약', '청약 시 Q4에 E11.9 고지 → <b>본사 이관</b> → 심사 대기함에서 AI 의견 확인 후 결정'],
    ['한골절', '6개월 전 경골 골절 수술', 'Q3에 S82.2 고지 → 골절 부위 부담보로 계약 → 골절로 청구하면 <b>부담보 해당</b>'],
    ['최고령', '만 71세', '가입나이 초과 → 거절'],
  ].map((r) => `<tr>${r.map((c) => `<td>${c}</td>`).join('')}</tr>`))}
  <p class="small muted" style="margin-top:10px">심사 기준·약관은 포트폴리오용 가상 기준입니다. 설계: <a href="https://github.com/creepereye1204/insurance.r-e.kr/blob/main/docs/underwriting-design.md">underwriting-design.md</a></p></div>`;
}

// ============================================================ 고객관리
async function customers(params) {
  const q = params.get('q') || '';
  const page = Number(params.get('page') || 0);
  const data = await api(`/core/customers?q=${encodeURIComponent(q)}&page=${page}`);
  view.innerHTML = `
  <div class="actions"><h1>고객관리</h1><span class="spacer"></span><button class="btn" id="new-btn">고객 등록</button></div>
  <p class="sub">이름·연락처로 검색. 연락처는 목록에서 마스킹됩니다. 주민등록번호는 저장하지 않습니다.</p>
  <div class="card" id="new-form" hidden>
    <h2>고객 등록</h2>
    <div class="row">
      <div><label>이름</label><input id="n-name" maxlength="50"></div>
      <div><label>생년월일</label><input id="n-birth" type="date"></div>
      <div><label>성별</label><select id="n-gender"><option value="M">남</option><option value="F">여</option></select></div>
      <div><label>연락처</label><input id="n-phone" placeholder="010-0000-0000"></div>
    </div>
    <div class="actions"><button class="btn" id="n-save">저장</button><button class="btn ghost" id="n-cancel">취소</button></div>
    <div class="error" id="n-err" hidden></div>
  </div>
  <div class="card">
    <form class="actions" id="search" style="margin-bottom:12px">
      <input id="q" value="${esc(q)}" placeholder="이름 또는 연락처" style="max-width:280px">
      <button class="btn ghost">검색</button>
      <span class="spacer"></span><span class="muted small">총 ${data.totalElements}명</span>
    </form>
    ${table(['ID', '이름', '생년월일', '나이', '성별', '연락처', '등록일'], data.content.map((c) => `
      <tr class="click" data-id="${c.id}"><td>${c.id}</td><td><b>${esc(c.name)}</b></td><td>${c.birthDate}</td><td>만 ${c.age}세</td>
      <td>${c.gender === 'M' ? '남' : '여'}</td><td>${esc(maskPhone(c.phone))}</td><td>${day(c.createdAt)}</td></tr>`))}
    <div class="actions" style="margin-top:10px">
      ${page > 0 ? `<a class="btn ghost sm" href="#/customers?q=${encodeURIComponent(q)}&page=${page - 1}">이전</a>` : ''}
      <span class="muted small">${data.totalPages ? page + 1 : 0} / ${data.totalPages}</span>
      ${page + 1 < data.totalPages ? `<a class="btn ghost sm" href="#/customers?q=${encodeURIComponent(q)}&page=${page + 1}">다음</a>` : ''}
    </div>
  </div>`;
  document.getElementById('search').onsubmit = (e) => {
    e.preventDefault();
    location.hash = `#/customers?q=${encodeURIComponent(document.getElementById('q').value.trim())}`;
  };
  view.querySelectorAll('tr.click').forEach((tr) => (tr.onclick = () => (location.hash = `#/customers/${tr.dataset.id}`)));
  const form = document.getElementById('new-form');
  document.getElementById('new-btn').onclick = () => (form.hidden = false);
  document.getElementById('n-cancel').onclick = () => (form.hidden = true);
  document.getElementById('n-save').onclick = async () => {
    const err = document.getElementById('n-err'); err.hidden = true;
    try {
      const c = await post('/core/customers', {
        name: document.getElementById('n-name').value, birthDate: document.getElementById('n-birth').value || null,
        gender: document.getElementById('n-gender').value, phone: document.getElementById('n-phone').value.trim() || null,
      });
      location.hash = `#/customers/${c.id}`;
    } catch (e) { showError(err, e); }
  };
}

async function customerDetail(params, id) {
  const d = await api(`/core/customers/${id}`);
  const c = d.customer;
  const validConsent = d.consents.find((x) => x.valid);
  view.innerHTML = `
  <div class="actions"><h1>${esc(c.name)} <span class="muted" style="font-size:15px;font-weight:400">고객번호 ${c.id}</span></h1>
    <span class="spacer"></span>
    <a class="btn ghost" href="#/pre-uw?customer=${c.id}">선심사</a>
    <a class="btn" href="#/apply?customer=${c.id}">청약 작성</a></div>
  <p class="sub"><a href="#/customers">← 고객 목록</a></p>
  <div class="grid2">
    <div class="card"><h2>기본 정보</h2><dl class="kv">
      <dt>생년월일</dt><dd>${c.birthDate} (만 ${c.age}세)</dd><dt>성별</dt><dd>${c.gender === 'M' ? '남' : '여'}</dd>
      <dt>연락처</dt><dd>${esc(maskPhone(c.phone))}</dd><dt>등록일</dt><dd>${dt(c.createdAt)}</dd></dl></div>
    <div class="card"><h2>정보조회 동의</h2>
      ${validConsent ? `<p>${tag('유효', 't-ok')} ${esc(validConsent.label)}<br><span class="small muted">${dt(validConsent.agreedAt)} ~ ${dt(validConsent.expiresAt)}</span></p>`
        : `<p>${tag('없음', 't-muted')} 선심사 전에 동의가 필요합니다.</p>`}
      <button class="btn ghost sm" id="consent">동의 받기 (30일 유효)</button>
      <div class="error" id="c-err" hidden></div></div>
  </div>
  <div class="card"><h2>계약</h2>${table(['계약번호', '상품', '기간', '담보 (조건)', '월보험료', '상태'], d.contracts.map((k) => `
    <tr><td>${esc(k.contractNo)}<br><span class="small muted">${esc(k.applicationNo || '')}</span></td><td>${esc(k.productName)}</td>
    <td class="small">${k.startDate}<br>~ ${k.endDate}</td>
    <td class="small">${k.coverages.map((v) => `${esc(v.name)} ${man(v.insuredAmount)}${v.ratingPct ? ` <span class="t-warn">할증 ${v.ratingPct}%</span>` : ''}${v.exclusions.length ? `<br><span class="t-warn">${exclusionText(v.exclusions)}</span>` : ''}`).join('<br>')}</td>
    <td class="num">${won(k.monthlyPremium)}</td><td>${tag(k.status === 'ACTIVE' ? '유지' : '해지', k.status === 'ACTIVE' ? 't-ok' : 't-muted')}</td></tr>`))}</div>
  <div class="grid2">
    <div class="card"><h2>청약</h2>${table(['청약번호', '제출일', '상태'], d.applications.map((a) => `
      <tr class="click" data-href="#/applications/${a.id}"><td>${esc(a.applicationNo)}</td><td>${dt(a.submittedAt)}</td>
      <td>${tag(a.statusLabel, APP_STATUS[a.status])}</td></tr>`))}</div>
    <div class="card"><h2>선심사 이력</h2>${table(['일시', '예상 결과'], d.preUwCases.map((p) => `
      <tr><td class="small">${dt(p.createdAt)}</td><td class="small">${p.outcomes.map(esc).join('<br>')}</td></tr>`))}</div>
  </div>
  <div class="grid2">
    <div class="card"><h2>보험금 청구</h2>${table(['청구번호', '진료일', '내용', '청구액', '상태'], d.claims.map((x) => `
      <tr><td class="small">${esc(x.claimNo)}</td><td>${x.treatmentDate}</td><td class="small">${esc(x.diagnosis)} ${x.kcdCode ? `<span class="rule">${esc(x.kcdCode)}</span>` : ''}</td>
      <td class="num">${won(x.claimedAmount)}</td><td>${tag(...(CLAIM_STATUS[x.status] || [x.status, 't-muted']))}</td></tr>`))}</div>
    <div class="card"><h2>외부기관 의료이력 <span class="small muted">(Mock · 선심사 동의 시 조회)</span></h2>
      ${table(['기관', '질병코드', '진단일', '치료', '완치'], d.externalRecords.map((r) => `
      <tr><td class="small">${esc(r.institution)}</td><td><span class="rule">${esc(r.kcdCode)}</span></td><td>${r.diagnosedOn}</td>
      <td>${esc(r.treatment)}</td><td>${r.cured ? '예' : '아니오'}</td></tr>`))}</div>
  </div>`;
  view.querySelectorAll('tr.click').forEach((tr) => (tr.onclick = () => (location.hash = tr.dataset.href)));
  document.getElementById('consent').onclick = async () => {
    try { await post(`/core/customers/${id}/consents`, { type: 'PRE_UW_INQUIRY' }); route(); }
    catch (e) { showError(document.getElementById('c-err'), e); }
  };
}

// ============================================================ 선심사
async function preUw(params) {
  const selected = params.get('customer') || '';
  const [options, prods] = await Promise.all([customerOptions(selected), products()]);
  view.innerHTML = `
  <h1>선심사</h1>
  <p class="sub">청약 전에 고객 동의를 받아 <b>당사 청구이력 + 외부기관(Mock)</b>을 조회하고, 같은 심사 룰로 담보별 <b>예상</b> 결과를 냅니다. 고지 전이므로 구속력은 없습니다.</p>
  <div class="card">
    <div class="row">
      <div><label>고객</label><select id="cust"><option value="">선택</option>${options}</select></div>
      <div><label>상품</label><select id="prod">${prods.map((p) => `<option value="${p.id}">${esc(p.name)}</option>`).join('')}</select></div>
    </div>
    <div id="consent-box" class="small muted"></div>
    <div class="actions" style="margin-top:10px"><button class="btn" id="run">선심사 실행</button></div>
    <div class="error" id="err" hidden></div>
  </div>
  <div id="result"></div>`;
  const cust = document.getElementById('cust');
  const consentBox = document.getElementById('consent-box');
  async function refreshConsent() {
    if (!cust.value) { consentBox.innerHTML = ''; return; }
    const d = await api(`/core/customers/${cust.value}`);
    const v = d.consents.find((x) => x.valid);
    consentBox.innerHTML = v
      ? `${tag('동의 유효', 't-ok')} ${esc(v.label)} (~${dt(v.expiresAt)})`
      : `${tag('동의 없음', 't-bad')} 선심사를 하려면 고객 동의가 필요합니다. <label style="display:inline-flex;gap:6px;align-items:center;margin-left:8px;color:var(--ink)">
         <input type="checkbox" id="agree"> 고객이 정보조회에 동의함</label>`;
  }
  cust.onchange = refreshConsent;
  await refreshConsent();
  document.getElementById('run').onclick = async () => {
    const err = document.getElementById('err'); err.hidden = true;
    try {
      if (!cust.value) throw new Error('고객을 선택하세요.');
      const agree = document.getElementById('agree');
      if (agree) {
        if (!agree.checked) throw new Error('고객 동의를 먼저 받아야 합니다.');
        await post(`/core/customers/${cust.value}/consents`, { type: 'PRE_UW_INQUIRY' });
        await refreshConsent();
      }
      const r = await post('/core/uw/pre', { customerId: Number(cust.value), productId: Number(document.getElementById('prod').value) });
      const c = r.uwCase;
      document.getElementById('result').innerHTML = `
      <div class="card"><h2>예상 결과 · ${esc(c.customerName)} <span class="small muted">룰셋 v${c.ruleSetVersion} · ${dt(c.createdAt)}</span></h2>
        ${table(['담보', '예상 결과', '조건', '적용 룰', '사유'], c.results.map((x) => `
          <tr><td>${esc(x.coverageName)}</td><td>${outcomeTag(x.outcome)}</td>
          <td class="small">${exclusionText(x.exclusions)}${x.ratingPct ? `할증 ${x.ratingPct}%` : ''}</td>
          <td>${rules(x.matchedRules)}</td><td class="small">${x.reasons.map(esc).join('<br>')}</td></tr>`))}
        <div class="actions" style="margin-top:12px"><a class="btn" href="#/apply?customer=${c.customerId}">이 고객으로 청약 작성</a></div></div>
      <div class="card"><h2>조회된 이력 <span class="small muted">(조회 스냅샷으로 저장됨)</span></h2>
        ${table(['출처', '질병코드', '진단일', '치료', '완치'], r.inquired.map((m) => `
          <tr><td class="small">${esc(m.source)}</td><td><span class="rule">${esc(m.kcdCode)}</span></td><td>${m.diagnosedOn}</td>
          <td>${TREATMENT[m.treatment] || esc(m.treatment)}</td><td>${m.cured ? '예' : '아니오'}</td></tr>`), '조회된 이력이 없습니다.')}</div>`;
    } catch (e) { showError(err, e); }
  };
}

// ============================================================ 청약 작성
async function apply(params) {
  const selected = params.get('customer') || '';
  const [options, prods, qn, kcd] = await Promise.all([customerOptions(selected), products(), questionnaire(), kcdList()]);
  const product = prods[0];
  const amounts = [1, 2, 3, 5, 10].map((n) => `<option value="${n * 10_000_000}" ${n === 3 ? 'selected' : ''}>${n * 1000}만원</option>`).join('');
  view.innerHTML = `
  <h1>청약 작성</h1>
  <p class="sub">담보 선택 → 계약 전 알릴의무 고지 → 제출하면 즉시 <b>전산심사</b>가 실행됩니다.</p>
  <datalist id="kcd-list">${kcd.map((k) => `<option value="${esc(k.code)}">${esc(k.name)}</option>`).join('')}</datalist>
  <div class="card"><h2>1. 계약 정보</h2>
    <div class="row">
      <div><label>계약자 = 피보험자</label><select id="cust"><option value="">선택</option>${options}</select></div>
      <div><label>상품</label><select id="prod" disabled><option value="${product.id}">${esc(product.name)}</option></select></div>
    </div>
    ${table(['가입', '담보', '분류', '가입금액', '기준 월보험료'], product.coverages.map((v) => `
      <tr><td><input type="checkbox" class="cov" value="${v.id}" checked></td><td>${esc(v.name)}</td><td>${esc(v.categoryLabel)}</td>
      <td><select class="amt" data-id="${v.id}" style="max-width:130px">${amounts}</select></td>
      <td class="num small muted">1천만원당 ${won(v.monthlyRatePer10M)}</td></tr>`))}
  </div>
  <div class="card"><h2>2. 계약 전 알릴의무 <span class="small muted">${esc(qn.title)} · v${qn.version}</span></h2>
    <p class="small muted">사실대로 알리지 않으면 계약이 해지되거나 보장이 제한될 수 있습니다. "예"를 선택하면 질병·치료 내용을 입력합니다.</p>
    ${qn.questions.map((q) => `
    <div class="question" data-qid="${q.id}" data-detail="${q.detailRequired}">
      <div class="q">${esc(q.code)}. ${esc(q.text)} ${q.lookbackMonths ? `<small>(최근 ${q.lookbackMonths}개월)</small>` : ''}</div>
      <div class="yn"><label><input type="radio" name="q${q.id}" value="false" checked> 아니오</label>
        <label><input type="radio" name="q${q.id}" value="true"> 예</label></div>
      <div class="details"></div>
      ${q.detailRequired ? '<button class="btn ghost sm add" hidden style="margin-top:8px">+ 상세 추가</button>' : ''}
    </div>`).join('')}
  </div>
  <div class="card"><div class="actions"><button class="btn" id="submit">청약 제출 (전산심사 실행)</button>
    <span class="small muted">제출 시점의 질문지 버전(v${qn.version})이 청약에 고정됩니다.</span></div>
    <div class="error" id="err" hidden></div></div>
  <div id="result"></div>`;

  const detailRow = () => {
    const div = document.createElement('div');
    div.className = 'detail-row';
    div.innerHTML = `
      <div><label>질병코드 (KCD)</label><input class="d-kcd" list="kcd-list" placeholder="예) E11.9, K29.7"></div>
      <div><label>진단일</label><input class="d-date" type="date" max="${today()}"></div>
      <div><label>치료</label><select class="d-treat">${Object.entries(TREATMENT).map(([k, v]) => `<option value="${k}">${v}</option>`).join('')}</select></div>
      <div><label>치료일수</label><input class="d-days" type="number" min="0" value="1"></div>
      <div><label>완치</label><select class="d-cured"><option value="false">아니오</option><option value="true">예</option></select></div>
      <div><button class="btn ghost sm d-del">삭제</button></div>`;
    div.querySelector('.d-del').onclick = () => div.remove();
    return div;
  };
  view.querySelectorAll('.question').forEach((qEl) => {
    const details = qEl.querySelector('.details');
    const add = qEl.querySelector('.add');
    qEl.querySelectorAll('input[type=radio]').forEach((r) => (r.onchange = () => {
      const yes = qEl.querySelector('input[type=radio]:checked').value === 'true';
      if (add) add.hidden = !yes;
      if (yes && qEl.dataset.detail === 'true' && !details.children.length) details.append(detailRow());
      if (!yes) details.innerHTML = '';
    }));
    if (add) add.onclick = () => details.append(detailRow());
  });

  document.getElementById('submit').onclick = async () => {
    const err = document.getElementById('err'); err.hidden = true;
    const btn = document.getElementById('submit'); btn.disabled = true;
    try {
      const customerId = document.getElementById('cust').value;
      if (!customerId) throw new Error('계약자를 선택하세요.');
      const coverages = [...view.querySelectorAll('.cov:checked')].map((c) => ({
        coverageId: Number(c.value), insuredAmount: Number(view.querySelector(`.amt[data-id="${c.value}"]`).value),
      }));
      const answers = [...view.querySelectorAll('.question')].map((qEl) => ({
        questionId: Number(qEl.dataset.qid),
        answer: qEl.querySelector('input[type=radio]:checked').value === 'true',
        details: [...qEl.querySelectorAll('.detail-row')].map((d) => ({
          kcdCode: d.querySelector('.d-kcd').value.trim(), diagnosedOn: d.querySelector('.d-date').value || null,
          treatment: d.querySelector('.d-treat').value, treatmentDays: Number(d.querySelector('.d-days').value || 0),
          cured: d.querySelector('.d-cured').value === 'true',
        })),
      }));
      const r = await post('/core/applications', {
        customerId: Number(customerId), productId: product.id, questionnaireId: qn.id, coverages, answers,
      });
      location.hash = `#/applications/${r.summary.id}`;
    } catch (e) { showError(err, e); } finally { btn.disabled = false; }
  };
}

// ============================================================ 청약 현황 / 상세
async function applications() {
  const list = await api('/core/applications');
  view.innerHTML = `<h1>청약 현황</h1><p class="sub">최근 청약 100건</p>
  <div class="card">${table(['청약번호', '고객', '상품', '제출일시', '상태'], list.map((a) => `
    <tr class="click" data-id="${a.id}"><td>${esc(a.applicationNo)}</td><td>${esc(a.customerName)}</td><td>${esc(a.productName)}</td>
    <td>${dt(a.submittedAt)}</td><td>${tag(a.statusLabel, APP_STATUS[a.status])}</td></tr>`))}</div>`;
  view.querySelectorAll('tr.click').forEach((tr) => (tr.onclick = () => (location.hash = `#/applications/${tr.dataset.id}`)));
}

function answersCard(d) {
  return `<div class="card"><h2>알릴의무 고지 <span class="small muted">질문지 v${d.questionnaireVersion}</span></h2>
  ${table(['문항', '응답', '상세'], d.answers.map((a) => `
    <tr><td class="small"><b>${esc(a.questionCode)}</b> ${esc(a.questionText)}</td><td class="nowrap">${a.answer ? tag("예", "t-warn") : "아니오"}</td>
    <td class="small">${a.details.map((x) => `<span class="rule">${esc(x.kcdCode)}</span> ${x.diagnosedOn} ${esc(x.treatment)} ${x.treatmentDays}일 ${x.cured ? '완치' : '치료중'}`).join('<br>')}</td></tr>`))}</div>`;
}

function resultsTable(results, proposal) {
  return table(['담보', '가입금액', proposal ? '전산심사 제안' : '결과', '조건', '적용 룰', '사유'], results.map((x) => `
    <tr><td>${esc(x.coverageName)}</td><td class="num">${man(x.insuredAmount)}</td><td>${outcomeTag(x.outcome)}</td>
    <td class="small">${exclusionText(x.exclusions)}${x.ratingPct ? `할증 ${x.ratingPct}%` : ''}</td>
    <td>${rules(x.matchedRules)}</td><td class="small">${(x.reasons || []).map(esc).join('<br>')}</td></tr>`));
}

function logsTable(logs) {
  return table(['일시', '단계', '판단 주체', '결정', '룰', '사유'], logs.map((l) => `
    <tr><td class="small">${dt(l.createdAt)}</td><td>${esc(l.stage)}</td><td>${esc(l.actor)}</td><td>${esc(l.decision)}</td>
    <td>${rules(l.matchedRules)}</td><td class="small">${esc(l.reason || '')}</td></tr>`));
}

async function applicationDetail(params, id) {
  const d = await api(`/core/applications/${id}`);
  const s = d.summary, u = d.uwCase;
  view.innerHTML = `
  <div class="actions"><h1>청약 ${esc(s.applicationNo)}</h1>${tag(s.statusLabel, APP_STATUS[s.status])}</div>
  <p class="sub"><a href="#/customers/${s.customerId}">${esc(s.customerName)}</a> · ${esc(s.productName)} · 제출 ${dt(s.submittedAt)}</p>
  ${s.status === 'UNDER_REVIEW' ? `<div class="notice">본사 심사로 이관되었습니다. 사유: ${esc(u.referReason)} · <a href="#/uw/${u.id}">심사 화면으로</a></div>` : ''}
  ${s.status === 'ISSUED' ? `<div class="notice" style="border-left-color:var(--ok)">계약이 성립되었습니다. <a href="#/customers/${s.customerId}">고객 상세에서 계약 확인</a> · <a href="#/claims">청구 접수</a></div>` : ''}
  <div class="card"><h2>심사 결과 <span class="small muted">${esc(u.stageLabel)} · ${esc(u.statusLabel)} · 룰셋 v${u.ruleSetVersion}</span></h2>${resultsTable(u.results)}</div>
  ${answersCard(d)}
  <div class="card"><h2>심사 이력 <span class="small muted">(추가만 가능한 감사 로그)</span></h2>${logsTable(u.logs)}</div>`;
}

// ============================================================ 본사 심사
async function uwInbox() {
  const list = await api('/core/uw/cases');
  view.innerHTML = `<h1>심사 대기함</h1><p class="sub">전산심사에서 본사로 이관된 청약</p>
  <div class="card">${table(['접수', '청약번호', '고객', '이관 사유', 'AI 의견'], list.map((c) => `
    <tr class="click" data-id="${c.id}"><td class="small">${dt(c.createdAt)}</td><td>${esc(c.applicationNo)}</td><td>${esc(c.customerName)}</td>
    <td class="small">${esc(c.referReason)}</td><td>${c.aiOpinion ? tag('작성됨', 't-ok') : tag('작성 중', 't-muted')}</td></tr>`), '대기 중인 심사 건이 없습니다. 박당뇨 고객으로 당뇨(E11.9)를 고지해 청약해 보세요.')}</div>`;
  view.querySelectorAll('tr.click').forEach((tr) => (tr.onclick = () => (location.hash = `#/uw/${tr.dataset.id}`)));
}

async function uwDetail(params, caseId) {
  const u = await api(`/core/uw/cases/${caseId}`);
  const app = await api(`/core/applications/${u.applicationId}`);
  const pending = u.status === 'PENDING';
  view.innerHTML = `
  <div class="actions"><h1>본사 심사 · ${esc(u.customerName)}</h1>${tag(u.statusLabel, pending ? 't-info' : 't-ok')}</div>
  <p class="sub"><a href="#/uw">← 대기함</a> · 청약 <a href="#/applications/${u.applicationId}">${esc(u.applicationNo)}</a> · 이관 사유: ${esc(u.referReason || '-')}</p>
  <div class="card"><h2>${pending ? '전산심사 제안' : '최종 결과'}</h2>${resultsTable(u.results, pending)}</div>
  ${answersCard(app)}
  <div class="card"><h2>AI 심사 참고의견 <span class="small muted">약관 근거 · 참고용</span></h2>
    <div id="ai">${u.aiOpinion ? renderMarkdown(u.aiOpinion) : '<span class="muted">AI가 의견을 작성 중입니다… (로컬 GPU, 20~60초)</span>'}</div></div>
  ${pending ? `
  <div class="card"><h2>심사자 결정</h2>
    ${table(['담보', '결정'], u.results.map((x) => `
      <tr><td>${esc(x.coverageName)}</td><td><select class="choice" data-cov="${x.coverageId}" style="max-width:280px">
        ${(x.exclusions.length || x.ratingPct) ? `<option value="ACCEPT_PROPOSED">제안 조건으로 승인 (${x.exclusions.length ? '부담보' : ''}${x.ratingPct ? ' 할증' : ''})</option>` : ''}
        <option value="ACCEPT_STANDARD">표준체 승인</option><option value="DECLINE">거절</option></select></td></tr>`))}
    <div class="row" style="margin-top:10px">
      <div><label>심사자</label><input id="uw-name" value="심사자A"></div>
      <div style="grid-column: span 2"><label>심사 의견</label><input id="uw-comment" placeholder="예) 당뇨 투약 중, 실손 인수 불가"></div>
    </div>
    <div class="actions"><button class="btn" id="decide">결정 저장 → 계약 성립/거절</button></div>
    <div class="error" id="err" hidden></div></div>` : ''}
  <div class="card"><h2>심사 이력</h2>${logsTable(u.logs)}</div>`;

  if (pending && !u.aiOpinion) {
    const poll = async () => {
      const next = await api(`/core/uw/cases/${caseId}`).catch(() => null);
      if (next?.aiOpinion) document.getElementById('ai').innerHTML = renderMarkdown(next.aiOpinion);
      else timers.push(setTimeout(poll, 4000));
    };
    timers.push(setTimeout(poll, 4000));
  }
  if (pending) {
    document.getElementById('decide').onclick = async () => {
      const err = document.getElementById('err'); err.hidden = true;
      try {
        const choices = Object.fromEntries([...view.querySelectorAll('.choice')].map((s) => [s.dataset.cov, s.value]));
        await post(`/core/uw/cases/${caseId}/decision`, {
          choices, underwriter: document.getElementById('uw-name').value.trim(), comment: document.getElementById('uw-comment').value,
        });
        location.hash = `#/applications/${u.applicationId}`;
      } catch (e) { showError(err, e); }
    };
  }
}

// ============================================================ 보험금 청구
async function claims() {
  const [contracts, kcd, recent] = await Promise.all([api('/core/contracts'), kcdList(), api('/core/claims')]);
  view.innerHTML = `
  <h1>보험금 청구</h1>
  <p class="sub">접수 즉시 <b>부담보·고지의무 위반</b>을 규칙으로 점검하고, AI가 약관 기준으로 검토한 뒤 심사자가 지급을 결정합니다.</p>
  <datalist id="kcd-list">${kcd.map((k) => `<option value="${esc(k.code)}">${esc(k.name)}</option>`).join('')}</datalist>
  <div class="card"><h2>청구 접수</h2>
    <div class="row">
      <div style="grid-column: span 2"><label>계약</label><select id="c-contract">${contracts.filter((c) => c.status === 'ACTIVE').map((c) =>
        `<option value="${c.id}">${esc(c.customerName)} · ${esc(c.contractNo)} (${c.startDate}~)</option>`).join('')}</select></div>
      <div><label>청구 유형</label><select id="c-type"><option value="OUTPATIENT">통원</option><option value="INPATIENT">입원</option><option value="PRESCRIPTION">처방조제</option></select></div>
    </div>
    <div class="row">
      <div><label>진료일</label><input id="c-date" type="date" value="${today()}" max="${today()}"></div>
      <div><label>질병코드</label><input id="c-kcd" list="kcd-list" placeholder="예) K29.7"></div>
      <div><label>청구 금액(원)</label><input id="c-amount" type="number" min="1" step="1000" value="50000"></div>
    </div>
    <div style="margin-bottom:10px"><label>진단/치료 내용</label><input id="c-diag" value="외래 진료"></div>
    <div class="actions"><button class="btn" id="c-submit">청구 접수</button></div>
    <div class="error" id="c-err" hidden></div></div>
  <div id="claim"></div>
  <div class="card"><h2>최근 청구</h2>${table(['청구번호', '고객', '진료일', '내용', '청구액', '점검', '상태'], recent.map((x) => `
    <tr class="click" data-id="${x.id}"><td class="small">${esc(x.claimNo)}</td><td>${esc(x.customerName)}</td><td>${x.treatmentDate}</td>
    <td class="small">${esc(x.diagnosis)} ${x.kcdCode ? `<span class="rule">${esc(x.kcdCode)}</span>` : ''}</td><td class="num">${won(x.claimedAmount)}</td>
    <td>${x.checkNotes?.length ? tag(`${x.checkNotes.length}건`, 't-bad') : ''}</td><td>${tag(...(CLAIM_STATUS[x.status] || [x.status, 't-muted']))}</td></tr>`))}</div>`;

  let current = null;
  const box = document.getElementById('claim');
  function render(c) {
    current = c;
    const decidable = c.status === 'AI_REVIEWED' || c.status === 'MANUAL_REVIEW';
    box.innerHTML = `<div class="card"><h2>청구 ${esc(c.claimNo)} · ${esc(c.customerName)} ${tag(...(CLAIM_STATUS[c.status] || [c.status, 't-muted']))}</h2>
      <p class="small muted">${esc(c.contractNo)} · ${c.treatmentDate} · ${esc(c.diagnosis)} ${c.kcdCode ? `<span class="rule">${esc(c.kcdCode)}</span>` : ''} · 청구 ${won(c.claimedAmount)}</p>
      <h2 style="margin-top:12px">규칙 점검</h2>
      ${c.checkNotes?.length ? c.checkNotes.map((n) => `<div class="notice bad">${esc(n)}</div>`).join('') : '<p class="muted">특이사항 없음</p>'}
      <h2 style="margin-top:12px">AI 약관 검토</h2>
      ${c.aiOpinion ? renderMarkdown(c.aiOpinion) : '<p class="muted">AI가 약관을 검토 중입니다… (20~60초)</p>'}
      ${decidable ? `<div class="actions" style="margin-top:12px"><input id="pay" type="number" min="1" value="${c.claimedAmount}" style="max-width:160px">
        <button class="btn" id="approve">지급 승인</button><button class="btn ghost" id="reject">부지급</button>
        <span class="small muted">AI·규칙은 참고, 최종 결정은 심사자</span></div>` : ''}
      ${c.paidAmount != null ? `<p><b>결정:</b> ${tag(...CLAIM_STATUS[c.status])} 지급액 ${won(c.paidAmount)} · ${esc(c.reviewerComment || '')}</p>` : ''}
      <div class="error" id="d-err" hidden></div></div>`;
    if (decidable) {
      const decide = async (action, body) => {
        try { render(await post(`/core/claims/${c.id}/${action}`, body)); } catch (e) { showError(document.getElementById('d-err'), e); }
      };
      document.getElementById('approve').onclick = () => decide('approve', { paidAmount: Number(document.getElementById('pay').value), comment: '데모 심사자 승인' });
      document.getElementById('reject').onclick = () => decide('reject', { comment: '데모 심사자 부지급' });
    }
    if (c.status === 'RECEIVED' || c.status === 'AI_REVIEWING') {
      timers.push(setTimeout(async () => render(await api(`/core/claims/${c.id}`)), 3000));
    }
  }
  view.querySelectorAll('tr.click').forEach((tr) => (tr.onclick = async () => { clearTimers(); render(await api(`/core/claims/${tr.dataset.id}`)); box.scrollIntoView(); }));
  document.getElementById('c-submit').onclick = async () => {
    const err = document.getElementById('c-err'); err.hidden = true; clearTimers();
    try {
      render(await post('/core/claims', {
        contractId: Number(document.getElementById('c-contract').value), type: document.getElementById('c-type').value,
        treatmentDate: document.getElementById('c-date').value, kcdCode: document.getElementById('c-kcd').value.trim() || null,
        diagnosis: document.getElementById('c-diag').value.trim(), amount: Number(document.getElementById('c-amount').value),
      }));
    } catch (e) { showError(err, e); }
  };
}

// ============================================================ 약관 Q&A
async function qa() {
  const EXAMPLES = ['쌍꺼풀 수술하면 실손보험 보상 받을 수 있어?', '통원 치료비 5만원 나왔으면 얼마 받아?', '보험금 청구할 때 필요한 서류가 뭐야?', '출산으로 입원하면 보상돼?'];
  view.innerHTML = `<h1>약관 Q&amp;A</h1><p class="sub">AI 에이전트가 약관을 검색해 조항을 근거로 답합니다. (로컬 GPU · Gemma 4)</p>
  <div class="card"><label>질문</label><textarea id="q"></textarea>
    <div class="actions" style="margin:8px 0 12px">${EXAMPLES.map((e) => `<button class="btn ghost sm ex">${esc(e)}</button>`).join('')}</div>
    <div class="actions"><button class="btn" id="ask">질문하기</button><span class="small muted" id="hint">20~60초 정도 걸려요.</span></div>
    <div class="error" id="err" hidden></div><div id="answer" style="margin-top:14px"></div></div>`;
  view.querySelectorAll('.ex').forEach((b) => (b.onclick = () => (document.getElementById('q').value = b.textContent)));
  document.getElementById('ask').onclick = async () => {
    const question = document.getElementById('q').value.trim();
    if (!question) return;
    const btn = document.getElementById('ask'), hint = document.getElementById('hint'), err = document.getElementById('err');
    btn.disabled = true; err.hidden = true;
    const started = Date.now();
    const timer = setInterval(() => (hint.textContent = `AI가 약관을 검토 중… ${Math.round((Date.now() - started) / 1000)}초`), 1000);
    try {
      const r = await post('/ai/chat', { question });
      document.getElementById('answer').innerHTML = renderMarkdown(r.answer || '(빈 응답)') +
        `<p class="small muted" style="margin-top:12px">도구 호출: ${r.trace.map((t) => `<span class="rule">${esc(t.tool)}</span>`).join(' → ')} · ${esc(r.model)} · ${(r.latency_ms / 1000).toFixed(1)}초</p>`;
    } catch (e) { showError(err, e); } finally { clearInterval(timer); btn.disabled = false; hint.textContent = '20~60초 정도 걸려요.'; }
  };
}

route();
