'use strict';

// ============================================================ 공통
const root = document.getElementById('view');
/**
 * 화면마다 새 컨테이너를 만든다. 빠르게 이동하면 이전 화면의 늦은 응답이 도착할 수 있는데,
 * 그 렌더링은 화면에 붙지 않은(버려진) 컨테이너에 쓰여 현재 화면을 덮어쓰지 않는다.
 */
let activeView = root;
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const won = (n) => (n == null ? '-' : Number(n).toLocaleString('ko-KR') + '원');
const amountText = (n) => {
  if (n == null) return '-';
  if (n >= 100_000_000 && n % 100_000_000 === 0) return n / 100_000_000 + '억원';
  if (n >= 10_000) return (n / 10_000).toLocaleString('ko-KR') + '만원';
  return won(n);
};
const day = (s) => (s ? String(s).slice(0, 10) : '-');
const dt = (s) => (s ? String(s).slice(0, 16).replace('T', ' ') : '-');
const maskPhone = (p) => (p ? p.replace(/^(\d{3})-(\d{3,4})-(\d{4})$/, '$1-****-$3') : '-');
const today = () => new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10);
const $ = (id) => document.getElementById(id);

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
  STANDARD: ['표준체', 't-ok', 'g'], CONDITIONAL: ['조건부 인수', 't-warn', 'y'], REFER: ['본사 이관', 't-info', 'b'],
  POSTPONE: ['연기', 't-warn', 'y'], DECLINE: ['거절', 't-bad', 'r'],
};
const APP_STATUS = { UNDER_REVIEW: 't-info', ISSUED: 't-ok', POSTPONED: 't-warn', DECLINED: 't-bad' };
const CONTRACT_STATUS = { ACTIVE: ['유지', 't-ok'], TERMINATED: ['해지', 't-muted'], WITHDRAWN: ['청약철회', 't-warn'] };
const CLAIM_STATUS = {
  RECEIVED: ['접수', 't-muted'], AI_REVIEWING: ['AI 검토 중', 't-info'], AI_REVIEWED: ['AI 검토 완료', 't-info'],
  MANUAL_REVIEW: ['수동심사', 't-warn'], APPROVED: ['지급', 't-ok'], REJECTED: ['부지급', 't-bad'],
};
const TREATMENT = { OUTPATIENT: '외래', INPATIENT: '입원', SURGERY: '수술', MEDICATION: '투약' };
const tag = (label, cls) => `<span class="tag ${cls}">${esc(label)}</span>`;
const outcomeTag = (o) => { const [l, c, light] = OUTCOME[o] || [o, 't-muted', 'b']; return `<span class="light ${light}"></span>${tag(l, c)}`; };
const rules = (list) => (list || []).map((r) => `<span class="rule">${esc(r)}</span>`).join(' ');
const exclusionText = (ex) => (ex || []).map((e) => `부담보: ${esc(e.label)} ${e.months ? e.months + '개월' : '~' + esc(e.until)}`).join('<br>');
const consumerTag = (c) => (c.consumerType === 'PROFESSIONAL' ? tag('전문금융소비자', 't-info') : tag('일반금융소비자', 't-muted'));

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
function bindRows(selector = 'tr.click') {
  activeView.querySelectorAll(selector).forEach((tr) => (tr.onclick = () => (location.hash = tr.dataset.href)));
}

const cache = {};
async function cached(key, path) { return (cache[key] ??= await api(path)); }
const questionnaire = () => cached('questionnaire', '/core/questionnaires/current');
const kcdList = () => cached('kcd', '/core/kcd');
const productTypes = () => cached('types', '/core/products/types');
const kcdDatalist = (kcd) => `<datalist id="kcd-list">${kcd.map((k) => `<option value="${esc(k.code)}">${esc(k.name)}</option>`).join('')}</datalist>`;

// ------------------------------------------------------------ 검색형 고객 선택기
/** 고객 3만 명 → 드롭다운 대신 이름·연락처 검색으로 고른다 */
async function customerPicker(el, selectedId, onChange) {
  let selected = null;
  if (selectedId) selected = (await api(`/core/customers/${selectedId}`)).customer;
  function render() {
    if (selected) {
      el.innerHTML = `<span class="chosen"><b>${esc(selected.name)}</b> 만 ${selected.age}세 ${selected.gender === 'M' ? '남' : '여'}
        · ${esc(selected.occupation)} ${consumerTag(selected)} <span class="small muted">#${selected.id}</span>
        <button class="btn ghost sm" type="button">변경</button></span>`;
      el.querySelector('button').onclick = () => { selected = null; render(); onChange(null); };
      return;
    }
    el.innerHTML = `<div class="picker"><input placeholder="고객 이름 또는 연락처로 검색 (예: 김정상)"><div class="results" hidden></div></div>`;
    const input = el.querySelector('input'), box = el.querySelector('.results');
    let timer;
    input.oninput = () => {
      clearTimeout(timer);
      timer = setTimeout(async () => {
        const q = input.value.trim();
        if (!q) { box.hidden = true; return; }
        let page;
        try {
          page = await api(`/core/customers?q=${encodeURIComponent(q)}`);
        } catch (e) {
          box.innerHTML = `<div class="error">${esc(e.message)}</div>`; box.hidden = false; return;
        }
        box.innerHTML = page.content.length ? page.content.slice(0, 12).map((c) => `<div data-id="${c.id}"><b>${esc(c.name)}</b>
          <span class="small muted">만 ${c.age}세 · ${c.gender === 'M' ? '남' : '여'} · ${esc(c.occupation)} · ${esc(maskPhone(c.phone))} · #${c.id}</span></div>`).join('')
          + (page.totalElements > 12 ? `<div class="small muted">외 ${page.totalElements - 12}명 — 더 구체적으로 검색하세요</div>` : '')
          : '<div class="muted">검색 결과 없음</div>';
        box.hidden = false;
        box.querySelectorAll('[data-id]').forEach((d) => (d.onclick = () => {
          selected = page.content.find((c) => String(c.id) === d.dataset.id);
          render(); onChange(selected);
        }));
      }, 250);
    };
    input.focus();
  }
  render();
  if (selected) onChange(selected);
}

// ------------------------------------------------------------ 상품 선택기
async function productPicker(el, selectedId, onChange) {
  const types = await productTypes();
  el.innerHTML = `
    <div class="row"><div><label>상품 유형</label><select class="p-type"><option value="">전체</option>
      ${types.map((t) => `<option value="${t.code}">${esc(t.label)}</option>`).join('')}</select></div>
      <div><label>상품명 검색</label><input class="p-q" placeholder="예: 든든, 간편심사형"></div></div>
    <div class="list-select p-list"></div>`;
  const list = el.querySelector('.p-list');
  async function load() {
    const type = el.querySelector('.p-type').value, q = el.querySelector('.p-q').value.trim();
    const page = await api(`/core/products?size=100${type ? '&type=' + type : ''}&q=${encodeURIComponent(q)}`);
    list.innerHTML = page.content.map((p) => `<div class="item ${String(p.id) === String(selectedId) ? 'on' : ''}" data-id="${p.id}">
      <b>${esc(p.name)}</b> ${p.simplifiedUw ? tag('간편심사', 't-warn') : ''}
      <div class="small muted">${esc(p.typeLabel)} · 만 ${p.minEntryAge}~${p.maxEntryAge}세 · ${p.paymentTerms.map((t) => (t === 1 ? '1년갱신' : t + '년납')).join('/')} · ${esc(p.insurancePeriod)} · ${esc(p.code)}</div></div>`).join('')
      || '<div class="empty">조건에 맞는 판매 중 상품이 없습니다.</div>';
    list.querySelectorAll('.item').forEach((it) => (it.onclick = async () => {
      selectedId = it.dataset.id;
      list.querySelectorAll('.item').forEach((x) => x.classList.toggle('on', x === it));
      onChange(await api(`/core/products/${selectedId}`));
    }));
  }
  el.querySelector('.p-type').onchange = load;
  let timer;
  el.querySelector('.p-q').oninput = () => { clearTimeout(timer); timer = setTimeout(load, 250); };
  await load();
  if (selectedId) onChange(await api(`/core/products/${selectedId}`));
}

// ============================================================ 라우터
const routes = [
  [/^\/$/, home],
  [/^\/customers$/, customers],
  [/^\/customers\/(\d+)$/, customerDetail],
  [/^\/products$/, products],
  [/^\/design$/, design],
  [/^\/proposals\/(\d+)$/, proposalDetail],
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
    a.classList.toggle('on', href === '/' ? path === '/' : path === href || path.startsWith(href + '/'));
  });
  const container = document.createElement('div');
  root.replaceChildren(container);
  activeView = container;
  for (const [re, fn] of routes) {
    const m = path.match(re);
    if (m) {
      container.innerHTML = '<div class="empty">불러오는 중…</div>';
      try { await fn(params, ...m.slice(1)); } catch (e) { container.innerHTML = `<div class="error">${esc(e.message)}</div>`; }
      window.scrollTo(0, 0);
      return;
    }
  }
  container.innerHTML = '<div class="empty">페이지를 찾을 수 없습니다.</div>';
}
window.addEventListener('hashchange', route);

/** 운영 상태: 응답한 기간계 인스턴스(로드밸런싱)와 AI 서킷브레이커 상태 */
async function refreshOps() {
  try {
    const s = await api('/core/ops/status');
    const c = s.aiCircuit.state;
    const cls = c === 'CLOSED' ? 't-ok' : c === 'OPEN' ? 't-bad' : 't-warn';
    $('ops').innerHTML = `기간계 <b>${esc(s.instance)}</b> · AI 서킷 ${tag(c, cls)}`;
  } catch { $('ops').textContent = ''; }
}
window.addEventListener('hashchange', refreshOps);
refreshOps();

// ============================================================ 개요
async function home() {
  const view = activeView;
  const scenarios = [
    ['김정상', '이력 없음', '가입설계 → 청약 → 자동 승인 → 계약 → 15일 내 청약철회'],
    ['이위염', '외부기관: 5개월 전 위염 치료 중', '선심사 → 위·십이지장 부담보 예상 / 미고지 가입 후 위염 청구 → <b>고지의무 위반 의심</b>'],
    ['박당뇨', '외부기관: 당뇨 투약 3년', 'Q4에 E11.9 고지 → <b>본사 이관</b> → AI 의견 → 담보별 결정'],
    ['최고령', '만 71세 일반소비자', '일반 종신은 나이 초과 / 간편심사형은 가능 + <b>고령자 보호 확인</b> 필수'],
    ['한골절', '외부기관: 6개월 전 골절 수술', 'Q3 고지 → 골절 부위 부담보 계약 → 골절 청구 시 <b>부담보 해당</b>'],
    ['정전문', '전문금융소비자', '변액 주식형 → <b>적합성 원칙 적용 제외</b>'],
    ['오안정', '투자성향 안정형', '변액 주식형 → <b>부적합</b>, 고객 확인 없이는 설계서 발행 불가'],
    ['강공격', '투자성향 공격투자형', '변액 주식형 적합 → 청약 시 원금손실 확인 필수'],
    ['윤위험', '건설현장 근로자(위험 3등급)', '운전자보험 → 상해 담보 <b>50% 할증</b>'],
    ['서임신', '임신 중', 'Q5 고지 → 수술·입원 담보 <b>임신·출산 부담보</b>'],
    ['문다청구', '최근 1년 청구 4회', '실손 청약 → <b>본사 이관</b> (잦은 청구)'],
    ['유어린', '만 10세', '종신 나이 미달 / 어린이보험 가입'],
  ];
  view.innerHTML = `
  <h1>보험 업무 시스템 데모</h1>
  <p class="sub">고객 → 가입설계(상품·보험료·예시표) → 선심사 → 알릴의무 고지 → 청약서·전자서명 → 전산심사 → 본사심사(AI 의견) → 계약 → 청구</p>
  <div class="card"><div class="flow">[채널계]  고객관리 ─ 가입설계(상품 201종, 요율표, 적합성 진단) ─ 설계서 발행
                     └ 선심사(동의 → 청구이력·외부기관 조회 → 예상 결과)
          청약 ─ 알릴의무 고지 ─ 청약서 확인 ─ 전자서명(필수 확인 항목, 문서 해시) ─ 제출
[심사]    전산심사(룰 엔진) ─┬─ 자동 승인 → 계약 성립(부담보·할증 반영) → 15일 청약철회 기간
                         ├─ 본사 이관 → AI 참고의견 → 심사자 담보별 결정
                         └─ 연기 / 거절
[보상]    청구 ─ 부담보·고지의무 위반 점검 ─ AI 약관 검토 ─ 심사자 지급 결정</div></div>
  <div class="card"><h2>데이터 규모 (가상)</h2>
    <p>상품 201개(12종) · 요율 약 11.5만 행 · 고객 약 3만 명 · 기존 계약 6만 건 · 청구 50만 건 · 외부기관 이력 5만 건</p></div>
  <div class="card"><h2>체험 시나리오</h2>
  ${table(['고객', '특징', '해볼 것'], scenarios.map((r) => `<tr><td><b>${r[0]}</b></td><td class="small">${r[1]}</td><td class="small">${r[2]}</td></tr>`))}
  <p class="small muted" style="margin-top:10px">상세: <a href="https://github.com/creepereye1204/ax-insurance/blob/main/docs/scenarios.md">docs/scenarios.md</a>
   · 심사 기준·약관·고객은 모두 가상입니다.</p></div>`;
}

// ============================================================ 고객관리
const JOBS = [['회사원(사무직)', 1], ['공무원', 1], ['교사', 1], ['대학생', 1], ['주부', 1], ['IT 개발자', 1], ['자영업', 1],
  ['영업직', 2], ['간호사', 2], ['택배기사', 2], ['건설현장 근로자', 3], ['소방관', 3], ['어업 종사자', 3], ['금융업 종사자', 1]];

async function customers(params) {
  const view = activeView;
  const q = params.get('q') || '';
  const page = Number(params.get('page') || 0);
  const data = await api(`/core/customers?q=${encodeURIComponent(q)}&page=${page}`);
  view.innerHTML = `
  <div class="actions"><h1>고객관리</h1><span class="spacer"></span><button class="btn" id="new-btn">고객 등록</button></div>
  <p class="sub">이름·연락처로 검색. 연락처는 목록에서 마스킹됩니다.</p>
  <div class="card" id="new-form" hidden>
    <h2>고객 등록</h2>
    <div class="row">
      <div><label>이름</label><input id="n-name" maxlength="50"></div>
      <div><label>생년월일</label><input id="n-birth" type="date"></div>
      <div><label>성별</label><select id="n-gender"><option value="M">남</option><option value="F">여</option></select></div>
      <div><label>연락처</label><input id="n-phone" placeholder="010-0000-0000"></div>
    </div>
    <div class="row">
      <div><label>직업 (위험등급)</label><select id="n-job">${JOBS.map(([j, r]) => `<option value="${esc(j)}" data-risk="${r}">${esc(j)} (${r}등급)</option>`).join('')}</select></div>
      <div><label>금융소비자 구분</label><select id="n-type"><option value="GENERAL">일반금융소비자</option><option value="PROFESSIONAL">전문금융소비자</option></select></div>
    </div>
    <div class="actions"><button class="btn" id="n-save">저장</button><button class="btn ghost" id="n-cancel">취소</button></div>
    <div class="error" id="n-err" hidden></div>
  </div>
  <div class="card">
    <form class="actions" id="search" style="margin-bottom:12px">
      <input id="q" value="${esc(q)}" placeholder="이름 또는 연락처" style="max-width:280px">
      <button class="btn ghost">검색</button>
      <span class="spacer"></span><span class="muted small">총 ${data.totalElements.toLocaleString()}명</span>
    </form>
    ${table(['ID', '이름', '생년월일', '나이', '성별', '직업', '소비자 구분', '투자성향', '연락처'], data.content.map((c) => `
      <tr class="click" data-href="#/customers/${c.id}"><td>${c.id}</td><td><b>${esc(c.name)}</b></td><td>${c.birthDate}</td><td>만 ${c.age}세</td>
      <td>${c.gender === 'M' ? '남' : '여'}</td><td class="small">${esc(c.occupation)} (${c.jobRiskGrade})</td><td>${consumerTag(c)}</td>
      <td class="small">${esc(c.investmentProfileLabel || '-')}</td><td>${esc(maskPhone(c.phone))}</td></tr>`))}
    <div class="actions" style="margin-top:10px">
      ${page > 0 ? `<a class="btn ghost sm" href="#/customers?q=${encodeURIComponent(q)}&page=${page - 1}">이전</a>` : ''}
      <span class="muted small">${data.totalPages ? page + 1 : 0} / ${data.totalPages.toLocaleString()}</span>
      ${page + 1 < data.totalPages ? `<a class="btn ghost sm" href="#/customers?q=${encodeURIComponent(q)}&page=${page + 1}">다음</a>` : ''}
    </div>
  </div>`;
  $('search').onsubmit = (e) => { e.preventDefault(); location.hash = `#/customers?q=${encodeURIComponent($('q').value.trim())}`; };
  bindRows();
  $('new-btn').onclick = () => ($('new-form').hidden = false);
  $('n-cancel').onclick = () => ($('new-form').hidden = true);
  $('n-save').onclick = async () => {
    $('n-err').hidden = true;
    try {
      const job = $('n-job').selectedOptions[0];
      const c = await post('/core/customers', {
        name: $('n-name').value, birthDate: $('n-birth').value || null, gender: $('n-gender').value,
        phone: $('n-phone').value.trim() || null, consumerType: $('n-type').value,
        occupation: job.value, jobRiskGrade: Number(job.dataset.risk),
      });
      location.hash = `#/customers/${c.id}`;
    } catch (e) { showError($('n-err'), e); }
  };
}

const PROFILE_QUESTIONS = [
  ['투자 경험 기간', ['없음', '1년 미만', '1~3년', '3~5년', '5년 이상']],
  ['투자 목적', ['원금 보존', '이자 수익', '균형 성장', '적극 성장', '고수익 추구']],
  ['감내할 수 있는 손실', ['손실 불가', '5% 이내', '10% 이내', '20% 이내', '20% 초과도 감수']],
  ['투자 예정 기간', ['1년 미만', '1~2년', '2~3년', '3~5년', '5년 이상']],
  ['금융상품 이해도', ['매우 낮음', '낮음', '보통', '높음', '매우 높음']],
];

async function customerDetail(params, id) {
  const view = activeView;
  const d = await api(`/core/customers/${id}`);
  const c = d.customer;
  const validConsent = d.consents.find((x) => x.valid);
  view.innerHTML = `
  <div class="actions"><h1>${esc(c.name)} <span class="muted" style="font-size:15px;font-weight:400">고객번호 ${c.id}</span></h1>
    <span class="spacer"></span>
    <a class="btn ghost" href="#/pre-uw?customer=${c.id}">선심사</a>
    <a class="btn" href="#/design?customer=${c.id}">가입설계</a></div>
  <p class="sub"><a href="#/customers">← 고객 목록</a></p>
  ${c.protectedSenior ? '<div class="notice">만 65세 이상 일반금융소비자 — 청약 시 고령자 보호 확인 절차가 필요합니다.</div>' : ''}
  <div class="grid2">
    <div class="card"><h2>기본 정보</h2><dl class="kv">
      <dt>생년월일</dt><dd>${c.birthDate} (만 ${c.age}세)</dd><dt>성별</dt><dd>${c.gender === 'M' ? '남' : '여'}</dd>
      <dt>연락처</dt><dd>${esc(maskPhone(c.phone))}</dd><dt>직업</dt><dd>${esc(c.occupation)} (위험 ${c.jobRiskGrade}등급)</dd>
      <dt>소비자 구분</dt><dd>${consumerTag(c)}</dd><dt>등록일</dt><dd>${dt(c.createdAt)}</dd></dl></div>
    <div class="card"><h2>투자성향 (변액보험 적합성)</h2>
      <p>${c.investmentProfileLabel ? `${tag(c.investmentProfileLabel, 't-info')} <span class="small muted">진단 ${dt(c.profiledAt)}</span>` : tag('미진단', 't-muted')}</p>
      <button class="btn ghost sm" id="profile-btn">투자성향 진단</button>
      <div id="profile-form" hidden style="margin-top:10px">
        ${PROFILE_QUESTIONS.map(([q, opts], i) => `<div style="margin-bottom:8px"><label>${i + 1}. ${q}</label>
          <select class="pq">${opts.map((o, j) => `<option value="${j + 1}">${o}</option>`).join('')}</select></div>`).join('')}
        <button class="btn sm" id="profile-save">진단 결과 저장</button></div>
      <h2 style="margin-top:14px">정보조회 동의</h2>
      ${validConsent ? `<p>${tag('유효', 't-ok')} <span class="small">${esc(validConsent.label)} ~${dt(validConsent.expiresAt)}</span></p>`
        : `<p>${tag('없음', 't-muted')} <span class="small muted">선심사 전에 동의가 필요합니다.</span></p>`}
      <button class="btn ghost sm" id="consent">동의 받기 (30일 유효)</button>
      <div class="error" id="c-err" hidden></div></div>
  </div>
  <div class="card"><h2>가입설계서</h2>${table(['설계번호', '상품', '월보험료', '발행일', '유효기간', '상태'], d.proposals.map((p) => `
    <tr class="click" data-href="#/proposals/${p.id}"><td>${esc(p.proposalNo)}</td><td>${esc(p.productName)}</td><td class="num">${won(p.monthlyPremium)}</td>
    <td>${dt(p.issuedAt)}</td><td>${p.validUntil}</td><td>${esc(p.statusLabel)}</td></tr>`))}</div>
  <div class="card"><h2>계약</h2>${table(['계약번호', '상품', '기간', '담보 (조건)', '월보험료', '상태', ''], d.contracts.map((k) => `
    <tr><td>${esc(k.contractNo)}<br><span class="small muted">${esc(k.applicationNo || '이관 계약')}</span></td><td>${esc(k.productName)}<br><span class="small muted">${esc(k.productType)}</span></td>
    <td class="small">${k.startDate}<br>~ ${k.endDate}</td>
    <td class="small">${k.coverages.map((v) => `${esc(v.name)} ${v.insuredAmount ? amountText(v.insuredAmount) : ''}${v.ratingPct ? ` <span class="t-warn">할증 ${v.ratingPct}%</span>` : ''}${v.exclusions.length ? `<br><span class="t-warn">${exclusionText(v.exclusions)}</span>` : ''}`).join('<br>')}</td>
    <td class="num">${won(k.monthlyPremium)}</td><td>${tag(...(CONTRACT_STATUS[k.status] || [k.status, 't-muted']))}</td>
    <td>${k.withdrawable ? `<button class="btn ghost sm withdraw" data-id="${k.id}">청약철회</button><br><span class="small muted">~${k.withdrawalDeadline}</span>` : ''}</td></tr>`))}
    <div class="error" id="w-err" hidden></div></div>
  <div class="grid2">
    <div class="card"><h2>청약</h2>${table(['청약번호', '제출일', '상태'], d.applications.map((a) => `
      <tr class="click" data-href="#/applications/${a.id}"><td>${esc(a.applicationNo)}</td><td>${dt(a.submittedAt)}</td>
      <td>${tag(a.statusLabel, APP_STATUS[a.status])}</td></tr>`))}</div>
    <div class="card"><h2>선심사 이력</h2>${table(['일시', '상품', '예상 결과'], d.preUwCases.map((p) => `
      <tr><td class="small">${dt(p.createdAt)}</td><td class="small">${esc(p.productName)}</td><td class="small">${p.outcomes.map(esc).join('<br>')}</td></tr>`))}</div>
  </div>
  <div class="grid2">
    <div class="card"><h2>보험금 청구 <span class="small muted">${d.claims.length}건</span></h2>${table(['진료일', '내용', '청구액', '상태'], d.claims.slice(0, 30).map((x) => `
      <tr><td>${x.treatmentDate}</td><td class="small">${esc(x.diagnosis)} ${x.kcdCode ? `<span class="rule">${esc(x.kcdCode)}</span>` : ''}</td>
      <td class="num">${won(x.claimedAmount)}</td><td>${tag(...(CLAIM_STATUS[x.status] || [x.status, 't-muted']))}</td></tr>`))}
      ${d.claims.length > 30 ? `<p class="small muted">최근 30건만 표시</p>` : ''}</div>
    <div class="card"><h2>외부기관 의료이력 <span class="small muted">(Mock)</span></h2>
      ${table(['기관', '질병코드', '진단일', '치료', '완치'], d.externalRecords.map((r) => `
      <tr><td class="small">${esc(r.institution)}</td><td><span class="rule">${esc(r.kcdCode)}</span></td><td>${r.diagnosedOn}</td>
      <td>${esc(r.treatment)}</td><td>${r.cured ? '예' : '아니오'}</td></tr>`))}</div>
  </div>`;
  bindRows();
  $('consent').onclick = async () => {
    try { await post(`/core/customers/${id}/consents`, { type: 'PRE_UW_INQUIRY' }); route(); } catch (e) { showError($('c-err'), e); }
  };
  $('profile-btn').onclick = () => ($('profile-form').hidden = false);
  $('profile-save').onclick = async () => {
    try {
      await post(`/core/customers/${id}/investment-profile`, { scores: [...view.querySelectorAll('.pq')].map((s) => Number(s.value)) });
      route();
    } catch (e) { showError($('c-err'), e); }
  };
  view.querySelectorAll('.withdraw').forEach((b) => (b.onclick = async () => {
    try { await post(`/core/contracts/${b.dataset.id}/withdraw`, {}); route(); } catch (e) { showError($('w-err'), e); }
  }));
}

// ============================================================ 상품
async function products(params) {
  const view = activeView;
  const types = await productTypes();
  const type = params.get('type') || '', q = params.get('q') || '', all = params.get('all') === '1';
  const page = Number(params.get('page') || 0);
  const data = await api(`/core/products?size=30&page=${page}&onSaleOnly=${!all}${type ? '&type=' + type : ''}&q=${encodeURIComponent(q)}`);
  const link = (o) => `#/products?${new URLSearchParams({ type, q, all: all ? '1' : '', page: 0, ...o })}`;
  view.innerHTML = `
  <h1>상품</h1><p class="sub">가상 상품 ${data.totalElements}개 · 유형을 고르거나 이름으로 검색하세요.</p>
  <div class="card">
    <form class="row" id="pf">
      <div><label>유형</label><select id="pt"><option value="">전체</option>${types.map((t) => `<option value="${t.code}" ${t.code === type ? 'selected' : ''}>${esc(t.label)}</option>`).join('')}</select></div>
      <div><label>검색</label><input id="pq" value="${esc(q)}"></div>
      <div><label>&nbsp;</label><label style="display:flex;gap:6px;color:var(--ink)"><input type="checkbox" id="pa" ${all ? 'checked' : ''}> 판매중지 포함</label></div>
    </form>
    ${table(['코드', '상품명', '유형', '가입나이', '납입', '보험기간', '담보', '판매', ''], data.content.map((p) => `
      <tr><td class="small">${esc(p.code)}</td><td><b>${esc(p.name)}</b></td><td>${esc(p.typeLabel)}</td><td>${p.minEntryAge}~${p.maxEntryAge}세</td>
      <td class="small">${p.paymentTerms.map((t) => (t === 1 ? '1년갱신' : t + '년')).join(', ')}</td><td class="small">${esc(p.insurancePeriod)}</td>
      <td>${p.coverageCount}</td><td>${p.saleStatus === 'ON_SALE' ? tag('판매중', 't-ok') : tag('판매중지', 't-muted')}</td>
      <td>${p.saleStatus === 'ON_SALE' ? `<a class="btn ghost sm" href="#/design?product=${p.id}">설계</a>` : ''}</td></tr>`))}
    <div class="actions" style="margin-top:10px">
      ${page > 0 ? `<a class="btn ghost sm" href="${link({ page: page - 1 })}">이전</a>` : ''}
      <span class="small muted">${page + 1} / ${data.totalPages}</span>
      ${page + 1 < data.totalPages ? `<a class="btn ghost sm" href="${link({ page: page + 1 })}">다음</a>` : ''}</div>
  </div>`;
  const go = () => (location.hash = `#/products?${new URLSearchParams({ type: $('pt').value, q: $('pq').value.trim(), all: $('pa').checked ? '1' : '' })}`);
  $('pt').onchange = go; $('pa').onchange = go;
  $('pf').onsubmit = (e) => { e.preventDefault(); go(); };
}

// ============================================================ 가입설계
async function design(params) {
  const view = activeView;
  view.innerHTML = `
  <h1>가입설계</h1>
  <p class="sub">고객과 상품을 고르고 담보·가입금액(저축성은 월 납입액·펀드)을 정해 보험료를 계산한 뒤 설계서를 발행합니다.</p>
  <div class="wizard"><span class="on">1. 고객·상품</span><span>2. 설계·보험료</span><span>3. 설계서 발행</span><span>4. 선심사</span><span>5. 청약</span></div>
  <div class="card"><h2>1. 고객</h2><div id="cust"></div></div>
  <div class="card"><h2>2. 상품</h2><div id="prod"></div></div>
  <div id="form"></div><div id="quote"></div>`;
  let customer = null, product = null;
  const refresh = () => renderDesignForm(customer, product);
  await customerPicker($('cust'), params.get('customer'), (c) => { customer = c; refresh(); });
  await productPicker($('prod'), params.get('product'), (p) => { product = p; refresh(); });
}

function amountOptions(c) {
  const opts = [];
  for (let a = c.minAmount; a <= c.maxAmount && opts.length < 200; a += c.amountStep) opts.push(a);
  return opts.map((a) => `<option value="${a}" ${a === c.defaultAmount ? 'selected' : ''}>${amountText(a)}</option>`).join('');
}

function renderDesignForm(customer, product) {
  const form = $('form');
  $('quote').innerHTML = '';
  if (!customer || !product) { form.innerHTML = ''; return; }
  const s = product.summary;
  const age = customer.age;
  const ageOk = age >= s.minEntryAge && age <= s.maxEntryAge;
  form.innerHTML = `
  <div class="card"><h2>3. 설계 — ${esc(s.name)}</h2>
    <p class="small muted">${esc(s.typeLabel)} · 가입나이 만 ${s.minEntryAge}~${s.maxEntryAge}세 · 보험기간 ${esc(s.insurancePeriod)}
      ${ageOk ? '' : ` · <span class="t-bad">고객 나이(만 ${age}세)로는 가입할 수 없습니다</span>`}</p>
    <div class="row">
      <div><label>납입기간</label><select id="term">${s.paymentTerms.map((t) => `<option value="${t}" ${t === 20 ? 'selected' : ''}>${t === 1 ? '1년 갱신' : t + '년납'}</option>`).join('')}</select></div>
      ${product.savings ? `<div><label>월 납입액 (${amountText(product.minMonthlyContribution)}~${amountText(product.maxMonthlyContribution)})</label>
        <input id="contrib" type="number" step="10000" min="${product.minMonthlyContribution}" max="${product.maxMonthlyContribution}" value="300000"></div>` : ''}
      ${product.annuityStartAges ? `<div><label>연금개시나이</label><select id="annuity">${product.annuityStartAges.map((a) => `<option ${a === 65 ? 'selected' : ''}>${a}</option>`).join('')}</select></div>` : ''}
    </div>
    ${product.coverages.some((c) => !c.accumulation) ? table(['가입', '담보', '분류', '가입금액'], product.coverages.filter((c) => !c.accumulation).map((c) => `
      <tr><td><input type="checkbox" class="cov" value="${c.id}" ${c.required ? 'checked disabled' : 'checked'}></td>
      <td>${esc(c.name)} ${c.required ? tag('주계약', 't-info') : tag('특약', 't-muted')}</td><td class="small">${esc(c.categoryLabel)}</td>
      <td><select class="amt" data-id="${c.id}" style="max-width:160px">${amountOptions(c)}</select></td></tr>`)) : ''}
    ${product.variable ? `<h2 style="margin-top:14px">펀드 편입비율 <span class="small muted">합계 100%, 10% 단위</span></h2>
      ${table(['펀드', '자산', '위험등급', '보수', '비율'], product.funds.map((f, i) => `
        <tr><td>${esc(f.name)}</td><td class="small">${esc(f.assetClass)}</td><td>${f.riskGrade}등급</td><td class="small">${(f.annualFeeBp / 100).toFixed(2)}%</td>
        <td><select class="fund" data-code="${f.code}" style="max-width:90px">${[0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100]
          .map((p) => `<option ${p === (f.code === 'F-BOND' ? 50 : f.code === 'F-MMF' ? 50 : 0) ? 'selected' : ''}>${p}</option>`).join('')}</select></td></tr>`))}
      <p class="small muted">고객 투자성향: ${customer.consumerType === 'PROFESSIONAL' ? '전문금융소비자 (적합성 원칙 적용 제외)'
        : esc(customer.investmentProfileLabel || '미진단 — 고객 상세에서 투자성향을 먼저 진단하세요')}</p>` : ''}
    <div class="actions" style="margin-top:12px"><button class="btn" id="calc">보험료 계산</button></div>
    <div class="error" id="d-err" hidden></div>
  </div>`;
  $('calc').onclick = () => calculate(customer, product, false);
}

function designBody(customer, product, ack) {
  return {
    customerId: customer.id, productId: product.summary.id, paymentTerm: Number($('term').value),
    coverages: [...activeView.querySelectorAll('.cov:checked')].map((c) => ({
      coverageId: Number(c.value), amount: Number(activeView.querySelector(`.amt[data-id="${c.value}"]`).value),
    })),
    monthlyContribution: $('contrib') ? Number($('contrib').value) : null,
    annuityStartAge: $('annuity') ? Number($('annuity').value) : null,
    funds: product.variable ? [...activeView.querySelectorAll('.fund')].filter((f) => Number(f.value) > 0)
      .map((f) => ({ fundCode: f.dataset.code, pct: Number(f.value) })) : null,
    unsuitableAcknowledged: ack,
  };
}

function illustrationTable(il) {
  if (!il) return '';
  return `<h3>적립금(해지환급금) 예시 <span class="small muted" style="font-weight:400">${esc(il.assumption)}</span></h3>
    ${table(['경과', '나이', '납입 누계', ...il.scenarios.map((s) => `${esc(s)} 적립금 (환급률)`)], il.rows.map((r) => `
      <tr><td>${r.year}년</td><td>${r.age}세</td><td class="num">${won(r.totalPaid)}</td>
      ${r.values.map((v, i) => `<td class="num">${won(v)} <span class="small ${r.refundRates[i] >= 100 ? 't-ok' : 't-bad'}">(${r.refundRates[i]}%)</span></td>`).join('')}</tr>`))}
    ${il.monthlyAnnuities ? `<p>연금개시 후 20년 확정 지급 시 월 연금액: ${il.monthlyAnnuities.map((a, i) => `${esc(il.scenarios[i])} <b>${won(a)}</b>`).join(' · ')}</p>` : ''}`;
}

async function calculate(customer, product, ack) {
  $('d-err').hidden = true;
  try {
    const q = await post('/core/proposals/quote', designBody(customer, product, ack));
    const unsuitable = q.suitability === 'UNSUITABLE';
    $('quote').innerHTML = `
    <div class="card"><h2>보험료 계산 결과</h2>
      <p>월 보험료 <span class="big">${won(q.monthlyPremium)}</span> <span class="small muted">· 만 ${q.age}세 기준 · ${q.paymentTerm === 1 ? '1년 갱신' : q.paymentTerm + '년납'}</span></p>
      ${table(['담보', '분류', '가입금액', '월 보험료'], q.coverages.map((c) => `<tr><td>${esc(c.name)}</td><td class="small">${esc(c.categoryLabel)}</td>
        <td class="num">${c.insuredAmount ? amountText(c.insuredAmount) : '-'}</td><td class="num">${c.monthlyPremium ? won(c.monthlyPremium) : '-'}</td></tr>`)
        .concat(q.monthlyContribution ? [`<tr><td>적립 보험료</td><td class="small">저축·연금</td><td></td><td class="num">${won(q.monthlyContribution)}</td></tr>`] : []))}
      ${q.suitability !== 'NOT_APPLICABLE' ? `<h3>적합성 진단</h3><p>${tag(q.suitabilityLabel, unsuitable ? 't-bad' : 't-ok')}</p>
        ${q.suitabilityIssues.map((i) => `<div class="notice bad">${esc(i)}</div>`).join('')}
        ${unsuitable ? `<label style="display:flex;gap:6px;color:var(--ink)"><input type="checkbox" id="ack"> 고객이 투자성향보다 위험한 상품임을 설명 듣고 확인했습니다 (부적합 확인서)</label>` : ''}` : ''}
      ${illustrationTable(q.illustration)}
      <div class="actions" style="margin-top:14px"><button class="btn" id="issue">설계서 발행</button>
        <span class="small muted">발행 시점의 나이·보험료·예시가 고정되며 30일간 유효합니다.</span></div>
      <div class="error" id="i-err" hidden></div></div>`;
    $('issue').onclick = async () => {
      $('i-err').hidden = true;
      try {
        const p = await post('/core/proposals', designBody(customer, product, $('ack')?.checked || q.suitability === 'UNSUITABLE_ACKNOWLEDGED'));
        location.hash = `#/proposals/${p.id}`;
      } catch (e) { showError($('i-err'), e); }
    };
  } catch (e) { showError($('d-err'), e); }
}

async function proposalDetail(params, id) {
  const view = activeView;
  const p = await api(`/core/proposals/${id}`);
  const canApply = p.status === 'ISSUED' && !p.expired;
  view.innerHTML = `
  <div class="wizard no-print"><span class="done">1. 고객·상품</span><span class="done">2. 설계·보험료</span><span class="on">3. 설계서 발행</span><span>4. 선심사</span><span>5. 청약</span></div>
  <div class="actions no-print" style="margin-bottom:12px">
    <a class="btn ghost" href="#/customers/${p.customerId}">← 고객</a><span class="spacer"></span>
    <button class="btn ghost" onclick="window.print()">출력</button>
    <a class="btn ghost" href="#/pre-uw?customer=${p.customerId}&product=${p.productId}">선심사</a>
    ${canApply ? `<a class="btn" href="#/apply?proposal=${p.id}">청약 진행</a>` : `<span class="small muted">${p.expired ? '유효기간 만료' : esc(p.statusLabel)}</span>`}
  </div>
  <div class="doc">
    <h2 class="title">보험 가입설계서</h2><div class="docno">${esc(p.proposalNo)} · 발행 ${dt(p.issuedAt)} · 유효기간 ${p.validUntil}까지 · ${esc(p.statusLabel)}</div>
    <h3>계약 정보</h3><dl class="kv">
      <dt>계약자·피보험자</dt><dd>${esc(p.customerName)} (만 ${p.insuredAge}세, ${esc(p.consumerType)})</dd>
      <dt>상품</dt><dd>${esc(p.productName)} <span class="small muted">${esc(p.productType)}</span></dd>
      <dt>보험기간</dt><dd>${esc(p.insurancePeriod)}</dd><dt>납입기간</dt><dd>${p.paymentTerm === 1 ? '1년 갱신' : p.paymentTerm + '년 (월납)'}</dd>
      ${p.annuityStartAge ? `<dt>연금개시</dt><dd>만 ${p.annuityStartAge}세</dd>` : ''}
      <dt>월 보험료</dt><dd><span class="big">${won(p.monthlyPremium)}</span></dd></dl>
    <h3>보장 내용</h3>${table(['담보', '분류', '가입금액', '월 보험료'], p.coverages.map((c) => `
      <tr><td>${esc(c.name)} ${c.required ? '(주계약)' : ''}</td><td>${esc(c.categoryLabel)}</td>
      <td class="num">${c.insuredAmount ? amountText(c.insuredAmount) : '-'}</td><td class="num">${c.monthlyPremium ? won(c.monthlyPremium) : '-'}</td></tr>`))}
    ${p.monthlyContribution ? `<p>적립 보험료: 월 ${won(p.monthlyContribution)}</p>` : ''}
    ${p.funds ? `<h3>펀드 편입비율</h3><p>${p.funds.map((f) => `${esc(f.fundCode)} ${f.pct}%`).join(' · ')}</p>
      <p>적합성: <b>${esc(p.suitabilityLabel)}</b></p>${(p.suitabilityIssues || []).map((i) => `<div class="notice bad">${esc(i)}</div>`).join('')}` : ''}
    ${illustrationTable(p.illustration)}
    <p class="small muted" style="margin-top:16px">※ 이 설계서는 포트폴리오용 가상 상품의 예시이며 실제 보험계약이 아닙니다.</p>
  </div>`;
}

// ============================================================ 선심사
async function preUw(params) {
  const view = activeView;
  view.innerHTML = `
  <h1>선심사</h1>
  <p class="sub">청약 전에 고객 동의를 받아 <b>당사 청구이력 + 외부기관(Mock)</b>을 조회하고, 같은 심사 룰로 담보별 <b>예상</b> 결과를 냅니다. 구속력은 없습니다.</p>
  <div class="card"><h2>고객</h2><div id="cust"></div><div id="consent-box" class="small" style="margin-top:8px"></div></div>
  <div class="card"><h2>상품</h2><div id="prod"></div></div>
  <div class="card"><div class="actions"><button class="btn" id="run">선심사 실행</button></div><div class="error" id="err" hidden></div></div>
  <div id="result"></div>`;
  let customer = null, product = null;
  async function refreshConsent() {
    if (!customer) { $('consent-box').innerHTML = ''; return; }
    const d = await api(`/core/customers/${customer.id}`);
    const v = d.consents.find((x) => x.valid);
    $('consent-box').innerHTML = v ? `${tag('동의 유효', 't-ok')} ${esc(v.label)} (~${dt(v.expiresAt)})`
      : `${tag('동의 없음', 't-bad')} <label style="display:inline-flex;gap:6px;align-items:center;color:var(--ink)"><input type="checkbox" id="agree"> 고객이 정보조회에 동의함</label>`;
  }
  await customerPicker($('cust'), params.get('customer'), async (c) => { customer = c; await refreshConsent(); });
  await productPicker($('prod'), params.get('product'), (p) => { product = p; });
  $('run').onclick = async () => {
    $('err').hidden = true;
    try {
      if (!customer || !product) throw new Error('고객과 상품을 선택하세요.');
      const agree = $('agree');
      if (agree) {
        if (!agree.checked) throw new Error('고객 동의를 먼저 받아야 합니다.');
        await post(`/core/customers/${customer.id}/consents`, { type: 'PRE_UW_INQUIRY' });
        await refreshConsent();
      }
      const r = await post('/core/uw/pre', { customerId: customer.id, productId: product.summary.id });
      const c = r.uwCase;
      $('result').innerHTML = `
      <div class="card"><h2>예상 결과 · ${esc(c.customerName)} · ${esc(c.productName)} <span class="small muted">룰셋 v${c.ruleSetVersion}</span></h2>
        ${table(['담보', '예상 결과', '조건', '적용 룰', '사유'], c.results.map((x) => `
          <tr><td>${esc(x.coverageName)}</td><td>${outcomeTag(x.outcome)}</td>
          <td class="small">${exclusionText(x.exclusions)}${x.ratingPct ? `할증 ${x.ratingPct}%` : ''}</td>
          <td>${rules(x.matchedRules)}</td><td class="small">${x.reasons.map(esc).join('<br>')}</td></tr>`))}
        <div class="actions" style="margin-top:12px"><a class="btn" href="#/design?customer=${c.customerId}&product=${product.summary.id}">이 상품으로 가입설계</a></div></div>
      <div class="card"><h2>조회된 이력 <span class="small muted">(조회 스냅샷으로 저장됨)</span></h2>
        ${table(['출처', '질병코드', '진단일', '치료', '완치'], r.inquired.map((m) => `
          <tr><td class="small">${esc(m.source)}</td><td><span class="rule">${esc(m.kcdCode)}</span></td><td>${m.diagnosedOn}</td>
          <td>${TREATMENT[m.treatment] || esc(m.treatment)}</td><td>${m.cured ? '예' : '아니오'}</td></tr>`), '조회된 이력이 없습니다.')}</div>`;
    } catch (e) { showError($('err'), e); }
  };
}

// ============================================================ 청약
async function apply(params) {
  const view = activeView;
  const proposalId = params.get('proposal');
  if (!proposalId) {
    const list = await api('/core/proposals');
    view.innerHTML = `<h1>청약</h1><p class="sub">청약은 발행된 가입설계서에서 시작합니다.</p>
      <div class="card"><div class="actions" style="margin-bottom:10px"><a class="btn" href="#/design">새 가입설계</a></div>
      ${table(['설계번호', '고객', '상품', '월보험료', '유효기간', '상태'], list.map((p) => `
        <tr class="click" data-href="${p.statusLabel === '설계서 발행' ? `#/apply?proposal=${p.id}` : `#/proposals/${p.id}`}"><td>${esc(p.proposalNo)}</td><td>${esc(p.customerName)}</td>
        <td>${esc(p.productName)}</td><td class="num">${won(p.monthlyPremium)}</td><td>${p.validUntil}</td><td>${esc(p.statusLabel)}</td></tr>`), '발행된 설계서가 없습니다.')}</div>`;
    bindRows();
    return;
  }
  const [p, qn, kcd] = await Promise.all([api(`/core/proposals/${proposalId}`), questionnaire(), kcdList()]);
  if (p.status !== 'ISSUED' || p.expired) {
    view.innerHTML = `<div class="error">청약할 수 없는 설계서입니다 (${esc(p.expired ? '유효기간 만료' : p.statusLabel)}). <a href="#/proposals/${p.id}">설계서 보기</a></div>`;
    return;
  }
  view.innerHTML = `
  <h1>청약 — ${esc(p.customerName)}</h1>
  <p class="sub">설계서 <a href="#/proposals/${p.id}">${esc(p.proposalNo)}</a> · ${esc(p.productName)} · 월 ${won(p.monthlyPremium)}</p>
  <div class="wizard"><span class="done">설계서</span><span class="on" id="w1">1. 알릴의무 고지</span><span id="w2">2. 청약서 확인</span><span id="w3">3. 전자서명·제출</span></div>
  ${kcdDatalist(kcd)}
  <div id="step1">
    <div class="card"><h2>계약 전 알릴의무 <span class="small muted">${esc(qn.title)} · v${qn.version}</span></h2>
      <p class="small muted">사실대로 알리지 않으면 계약이 해지되거나 보장이 제한될 수 있습니다. "예"를 선택하면 질병·치료 내용을 입력합니다.</p>
      ${qn.questions.map((q) => `
      <div class="question" data-qid="${q.id}" data-detail="${q.detailRequired}">
        <div class="q">${esc(q.code)}. ${esc(q.text)} ${q.lookbackMonths ? `<small>(최근 ${q.lookbackMonths}개월)</small>` : ''}</div>
        <div class="yn"><label><input type="radio" name="q${q.id}" value="false" checked> 아니오</label>
          <label><input type="radio" name="q${q.id}" value="true"> 예</label></div>
        <div class="details"></div>
        ${q.detailRequired ? '<button class="btn ghost sm add" hidden style="margin-top:8px">+ 상세 추가</button>' : ''}
      </div>`).join('')}
      <div class="actions"><button class="btn ghost" id="simulate">전산심사</button><button class="btn" id="to-doc">청약서 확인</button>
        <span class="small muted">전산심사: 입력한 고지로 심사 결과를 미리 확인합니다 (저장 안 함)</span></div>
      <div class="error" id="err1" hidden></div>
      <div id="sim"></div></div>
  </div>
  <div id="step2"></div>`;

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
    const details = qEl.querySelector('.details'), add = qEl.querySelector('.add');
    qEl.querySelectorAll('input[type=radio]').forEach((r) => (r.onchange = () => {
      const yes = qEl.querySelector('input[type=radio]:checked').value === 'true';
      if (add) add.hidden = !yes;
      if (yes && qEl.dataset.detail === 'true' && !details.children.length) details.append(detailRow());
      if (!yes) details.innerHTML = '';
    }));
    if (add) add.onclick = () => details.append(detailRow());
  });
  const answers = () => [...view.querySelectorAll('.question')].map((qEl) => ({
    questionId: Number(qEl.dataset.qid),
    answer: qEl.querySelector('input[type=radio]:checked').value === 'true',
    details: [...qEl.querySelectorAll('.detail-row')].map((d) => ({
      kcdCode: d.querySelector('.d-kcd').value.trim(), diagnosedOn: d.querySelector('.d-date').value || null,
      treatment: d.querySelector('.d-treat').value, treatmentDays: Number(d.querySelector('.d-days').value || 0),
      cured: d.querySelector('.d-cured').value === 'true',
    })),
  }));

  $('simulate').onclick = async () => {
    $('err1').hidden = true;
    try {
      const r = await post('/core/applications/underwrite-preview', { proposalId: p.id, questionnaireId: qn.id, answers: answers() });
      const cls = r.verdict.startsWith('자동') ? 't-ok' : r.verdict.startsWith('본사') ? 't-info' : r.verdict.startsWith('거절') ? 't-bad' : 't-warn';
      $('sim').innerHTML = `<h2 style="margin-top:16px">전산심사 결과 ${tag(r.verdict, cls)} <span class="small muted">룰셋 v${r.ruleSetVersion} · 저장되지 않은 사전 결과</span></h2>
        ${table(['담보', '결과', '조건', '적용 룰', '사유'], r.coverages.map((x) => `
          <tr><td>${esc(x.coverageName)}</td><td>${outcomeTag(x.outcome)}</td>
          <td class="small">${exclusionText(x.exclusions)}${x.ratingPct ? `할증 ${x.ratingPct}%` : ''}</td>
          <td>${rules(x.matchedRules)}</td><td class="small">${x.reasons.map(esc).join('<br>')}</td></tr>`))}`;
    } catch (e) { showError($('err1'), e); }
  };
  $('to-doc').onclick = async () => {
    $('err1').hidden = true;
    try {
      const ans = answers();
      const doc = await post('/core/applications/preview', { proposalId: p.id, questionnaireId: qn.id, answers: ans });
      $('step1').hidden = true;
      $('w1').className = 'done'; $('w2').className = 'on';
      $('step2').innerHTML = `
        ${documentView(doc, null)}
        <div class="card" style="margin-top:14px"><h2>전자서명 (모의)</h2>
          <p class="small muted">아래 항목을 모두 확인하고, 계약자 본인 이름을 입력해 서명합니다.</p>
          ${doc.requiredAgreements.map((a) => `<label style="display:flex;gap:8px;align-items:flex-start;color:var(--ink);margin:6px 0">
            <input type="checkbox" class="agr" value="${a.code}"> <span>${esc(a.text)}</span></label>`).join('')}
          <div class="row" style="margin-top:10px"><div><label>서명자 이름 (계약자 본인)</label><input id="signer" placeholder="${esc(doc.customerName)}"></div></div>
          <div class="actions"><button class="btn ghost" id="back">← 고지 수정</button><button class="btn" id="submit">서명하고 청약 제출</button></div>
          <div class="error" id="err2" hidden></div></div>`;
      $('back').onclick = () => { $('step1').hidden = false; $('step2').innerHTML = ''; $('w1').className = 'on'; $('w2').className = ''; };
      $('submit').onclick = async () => {
        $('err2').hidden = true;
        $('submit').disabled = true;
        try {
          const r = await post('/core/applications', {
            proposalId: p.id, questionnaireId: qn.id, answers: ans,
            signature: { signerName: $('signer').value.trim(), agreements: [...view.querySelectorAll('.agr:checked')].map((x) => x.value) },
          });
          location.hash = `#/applications/${r.summary.id}`;
        } catch (e) { showError($('err2'), e); } finally { $('submit').disabled = false; }
      };
      window.scrollTo(0, 0);
    } catch (e) { showError($('err1'), e); }
  };
}

/** 청약서 본문 (미리보기·서명 후 동일) */
function documentView(doc, signature) {
  return `<div class="doc">
    <h2 class="title">보험 청약서</h2>
    <div class="docno">${signature ? '청약번호 ' + esc(doc.applicationNo) + ' · ' : '미리보기 · '}설계서 ${esc(doc.proposalNo || '-')}</div>
    <h3>계약자·피보험자</h3><dl class="kv">
      <dt>성명</dt><dd>${esc(doc.customerName)}</dd><dt>생년월일</dt><dd>${doc.birthDate} (${doc.gender === 'M' ? '남' : '여'})</dd>
      <dt>소비자 구분</dt><dd>${esc(doc.consumerType)}${doc.protectedSenior ? ' · <b class="t-warn">고령 금융소비자 보호 대상</b>' : ''}</dd></dl>
    <h3>계약 내용</h3><dl class="kv">
      <dt>상품</dt><dd>${esc(doc.productName)} (${esc(doc.productType)})</dd><dt>보험기간</dt><dd>${esc(doc.insurancePeriod)}</dd>
      <dt>납입기간</dt><dd>${doc.paymentTerm === 1 ? '1년 갱신' : doc.paymentTerm + '년 월납'}</dd>
      ${doc.annuityStartAge ? `<dt>연금개시</dt><dd>만 ${doc.annuityStartAge}세</dd>` : ''}
      ${doc.suitability && doc.suitability !== '해당 없음' ? `<dt>적합성</dt><dd>${esc(doc.suitability)}</dd>` : ''}
      <dt>월 보험료</dt><dd><b>${won(doc.monthlyPremium)}</b></dd></dl>
    ${table(['담보', '분류', '가입금액', '월 보험료(표준체)'], doc.coverages.map((c) => `<tr><td>${esc(c.name)}</td><td>${esc(c.categoryLabel)}</td>
      <td class="num">${c.insuredAmount ? amountText(c.insuredAmount) : '-'}</td><td class="num">${won(c.basePremium)}</td></tr>`))}
    <h3>계약 전 알릴의무 (질문지 v${doc.questionnaireVersion})</h3>
    ${table(['문항', '응답', '상세'], doc.answers.map((a) => `
      <tr><td class="small"><b>${esc(a.questionCode)}</b> ${esc(a.questionText)}</td><td class="nowrap">${a.answer ? tag('예', 't-warn') : '아니오'}</td>
      <td class="small">${a.details.map((x) => `<span class="rule">${esc(x.kcdCode)}</span> ${x.diagnosedOn} ${esc(x.treatment)} ${x.treatmentDays}일 ${x.cured ? '완치' : '치료중'}`).join('<br>')}</td></tr>`))}
    <h3>청약철회 안내</h3>
    <p class="small">보험증권을 받은 날부터 15일 이내(청약일로부터 30일 이내)에 청약을 철회할 수 있으며, 납입한 보험료를 돌려받습니다.</p>
    ${signature ? `<h3>전자서명</h3><dl class="kv"><dt>서명자</dt><dd>${esc(signature.signerName)}</dd><dt>서명일시</dt><dd>${dt(signature.signedAt)}</dd>
      <dt>확인 항목</dt><dd class="small">${signature.agreements.map((a) => '✔ ' + esc(a.text)).join('<br>')}</dd>
      <dt>문서 해시</dt><dd class="hash">${esc(signature.documentHash)}</dd></dl>`
      : `<p class="hash" style="margin-top:12px">문서 해시(SHA-256): ${esc(doc.documentHash)}</p>`}
  </div>`;
}

async function applications() {
  const view = activeView;
  const list = await api('/core/applications');
  view.innerHTML = `<h1>청약 현황</h1><p class="sub">최근 청약 100건</p>
  <div class="card">${table(['청약번호', '고객', '상품', '월보험료', '제출일시', '상태'], list.map((a) => `
    <tr class="click" data-href="#/applications/${a.id}"><td>${esc(a.applicationNo)}</td><td>${esc(a.customerName)}</td><td>${esc(a.productName)}</td>
    <td class="num">${won(a.monthlyPremium)}</td><td>${dt(a.submittedAt)}</td><td>${tag(a.statusLabel, APP_STATUS[a.status])}</td></tr>`))}</div>`;
  bindRows();
}

function resultsTable(results, proposal) {
  return table(['담보', '가입금액', '보험료(표준체)', proposal ? '전산심사 제안' : '결과', '조건', '적용 룰', '사유'], results.map((x) => `
    <tr><td>${esc(x.coverageName)}</td><td class="num">${x.insuredAmount ? amountText(x.insuredAmount) : '-'}</td><td class="num">${won(x.basePremium)}</td>
    <td>${outcomeTag(x.outcome)}</td><td class="small">${exclusionText(x.exclusions)}${x.ratingPct ? `할증 ${x.ratingPct}%` : ''}</td>
    <td>${rules(x.matchedRules)}</td><td class="small">${(x.reasons || []).map(esc).join('<br>')}</td></tr>`));
}

function logsTable(logs) {
  return table(['일시', '단계', '판단 주체', '결정', '룰', '사유'], logs.map((l) => `
    <tr><td class="small">${dt(l.createdAt)}</td><td>${esc(l.stage)}</td><td>${esc(l.actor)}</td><td>${esc(l.decision)}</td>
    <td>${rules(l.matchedRules)}</td><td class="small">${esc(l.reason || '')}</td></tr>`));
}

async function applicationDetail(params, id) {
  const view = activeView;
  const d = await api(`/core/applications/${id}`);
  const s = d.summary, u = d.uwCase;
  view.innerHTML = `
  <div class="actions no-print"><h1>청약 ${esc(s.applicationNo)}</h1>${tag(s.statusLabel, APP_STATUS[s.status])}<span class="spacer"></span>
    <button class="btn ghost" onclick="window.print()">청약서 출력</button></div>
  <p class="sub no-print"><a href="#/customers/${s.customerId}">${esc(s.customerName)}</a> · ${esc(s.productName)} · 제출 ${dt(s.submittedAt)}</p>
  ${s.status === 'UNDER_REVIEW' ? `<div class="notice no-print">본사 심사로 이관되었습니다. 사유: ${esc(u.referReason)} · <a href="#/uw/${u.id}">심사 화면으로</a></div>` : ''}
  ${s.status === 'ISSUED' ? `<div class="notice no-print" style="border-left-color:var(--ok)">계약이 성립되었습니다. <a href="#/customers/${s.customerId}">고객 상세에서 계약 확인</a> (15일 이내 청약철회 가능)</div>` : ''}
  ${u ? `<div class="card no-print"><h2>심사 결과 <span class="small muted">${esc(u.stageLabel)} · ${esc(u.statusLabel)} · 룰셋 v${u.ruleSetVersion}</span></h2>${resultsTable(u.results)}</div>` : ''}
  ${documentView(d.document, d.signature)}
  ${u ? `<div class="card no-print" style="margin-top:14px"><h2>심사 이력 <span class="small muted">(추가만 가능한 감사 로그)</span></h2>${logsTable(u.logs)}</div>` : ''}`;
}

// ============================================================ 본사 심사
async function uwInbox() {
  const view = activeView;
  const list = await api('/core/uw/cases');
  view.innerHTML = `<h1>심사 대기함</h1><p class="sub">전산심사에서 본사로 이관된 청약</p>
  <div class="card">${table(['접수', '청약번호', '고객', '상품', '이관 사유', 'AI 의견'], list.map((c) => `
    <tr class="click" data-href="#/uw/${c.id}"><td class="small">${dt(c.createdAt)}</td><td>${esc(c.applicationNo)}</td><td>${esc(c.customerName)}</td>
    <td class="small">${esc(c.productName)}</td><td class="small">${esc(c.referReason)}</td><td>${c.aiOpinion ? tag('작성됨', 't-ok') : tag('작성 중', 't-muted')}</td></tr>`),
    '대기 중인 심사 건이 없습니다. 박당뇨 고객으로 당뇨(E11.9)를 고지해 청약해 보세요.')}</div>`;
  bindRows();
}

async function uwDetail(params, caseId) {
  const view = activeView;
  const u = await api(`/core/uw/cases/${caseId}`);
  const app = await api(`/core/applications/${u.applicationId}`);
  const pending = u.status === 'PENDING';
  view.innerHTML = `
  <div class="actions"><h1>본사 심사 · ${esc(u.customerName)}</h1>${tag(u.statusLabel, pending ? 't-info' : 't-ok')}</div>
  <p class="sub"><a href="#/uw">← 대기함</a> · 청약 <a href="#/applications/${u.applicationId}">${esc(u.applicationNo)}</a> · 이관 사유: ${esc(u.referReason || '-')}</p>
  <div class="card"><h2>${pending ? '전산심사 제안' : '최종 결과'}</h2>${resultsTable(u.results, pending)}</div>
  <div class="card"><h2>AI 심사 참고의견 <span class="small muted">약관 근거 · 참고용</span></h2>
    <div id="ai">${u.aiOpinion ? renderMarkdown(u.aiOpinion) : '<span class="muted">AI가 의견을 작성 중입니다… (로컬 GPU, 20~60초)</span>'}</div></div>
  ${pending ? `
  <div class="card"><h2>심사자 결정</h2>
    ${table(['담보', '결정'], u.results.map((x) => `
      <tr><td>${esc(x.coverageName)}</td><td><select class="choice" data-cov="${x.coverageId}" style="max-width:300px">
        ${(x.exclusions.length || x.ratingPct) ? `<option value="ACCEPT_PROPOSED">제안 조건으로 승인 (${x.exclusions.length ? '부담보' : ''}${x.ratingPct ? ' 할증' : ''})</option>` : ''}
        <option value="ACCEPT_STANDARD">표준체 승인</option><option value="DECLINE">거절</option></select></td></tr>`))}
    <div class="row" style="margin-top:10px">
      <div><label>심사자</label><input id="uw-name" value="심사자A"></div>
      <div style="grid-column: span 2"><label>심사 의견</label><input id="uw-comment" placeholder="예) 당뇨 투약 중, 실손 인수 불가"></div>
    </div>
    <div class="actions"><button class="btn" id="decide">결정 저장 → 계약 성립/거절</button></div>
    <div class="error" id="err" hidden></div></div>` : ''}
  ${documentView(app.document, app.signature)}
  <div class="card" style="margin-top:14px"><h2>심사 이력</h2>${logsTable(u.logs)}</div>`;

  if (pending && !u.aiOpinion) {
    const poll = async () => {
      const next = await api(`/core/uw/cases/${caseId}`).catch(() => null);
      if (next?.aiOpinion) $('ai').innerHTML = renderMarkdown(next.aiOpinion);
      else timers.push(setTimeout(poll, 4000));
    };
    timers.push(setTimeout(poll, 4000));
  }
  if (pending) {
    $('decide').onclick = async () => {
      $('err').hidden = true;
      try {
        const choices = Object.fromEntries([...view.querySelectorAll('.choice')].map((s) => [s.dataset.cov, s.value]));
        await post(`/core/uw/cases/${caseId}/decision`, { choices, underwriter: $('uw-name').value.trim(), comment: $('uw-comment').value });
        location.hash = `#/applications/${u.applicationId}`;
      } catch (e) { showError($('err'), e); }
    };
  }
}

// ============================================================ 보험금 청구
async function claims(params) {
  const view = activeView;
  const [kcd, recent] = await Promise.all([kcdList(), api('/core/claims')]);
  view.innerHTML = `
  <h1>보험금 청구</h1>
  <p class="sub">접수 즉시 <b>부담보·고지의무 위반</b>을 규칙으로 점검하고, AI가 약관 기준으로 검토한 뒤 심사자가 지급을 결정합니다.</p>
  ${kcdDatalist(kcd)}
  <div class="card"><h2>청구 접수</h2>
    <label>고객</label><div id="cust" style="margin-bottom:10px"></div>
    <div class="row">
      <div style="grid-column: span 2"><label>계약</label><select id="c-contract"><option value="">고객을 먼저 선택하세요</option></select></div>
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

  await customerPicker($('cust'), params.get('customer'), async (c) => {
    const sel = $('c-contract');
    if (!c) { sel.innerHTML = '<option value="">고객을 먼저 선택하세요</option>'; return; }
    const list = (await api(`/core/contracts?customerId=${c.id}`)).filter((k) => k.status === 'ACTIVE');
    sel.innerHTML = list.length ? list.map((k) => `<option value="${k.id}">${esc(k.contractNo)} · ${esc(k.productName)} (${k.startDate}~)</option>`).join('')
      : '<option value="">유지 중인 계약이 없습니다</option>';
  });

  const box = $('claim');
  function render(c) {
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
        try { render(await post(`/core/claims/${c.id}/${action}`, body)); } catch (e) { showError($('d-err'), e); }
      };
      $('approve').onclick = () => decide('approve', { paidAmount: Number($('pay').value), comment: '데모 심사자 승인' });
      $('reject').onclick = () => decide('reject', { comment: '데모 심사자 부지급' });
    }
    if (c.status === 'RECEIVED' || c.status === 'AI_REVIEWING') {
      timers.push(setTimeout(async () => render(await api(`/core/claims/${c.id}`)), 3000));
    }
  }
  view.querySelectorAll('tr.click').forEach((tr) => (tr.onclick = async () => { clearTimers(); render(await api(`/core/claims/${tr.dataset.id}`)); box.scrollIntoView(); }));
  $('c-submit').onclick = async () => {
    $('c-err').hidden = true; clearTimers();
    try {
      if (!$('c-contract').value) throw new Error('계약을 선택하세요.');
      render(await post('/core/claims', {
        contractId: Number($('c-contract').value), type: $('c-type').value, treatmentDate: $('c-date').value,
        kcdCode: $('c-kcd').value.trim() || null, diagnosis: $('c-diag').value.trim(), amount: Number($('c-amount').value),
      }));
    } catch (e) { showError($('c-err'), e); }
  };
}

// ============================================================ 약관 Q&A
async function qa() {
  const view = activeView;
  const EXAMPLES = ['쌍꺼풀 수술하면 실손보험 보상 받을 수 있어?', '통원 치료비 5만원 나왔으면 얼마 받아?', '보험금 청구할 때 필요한 서류가 뭐야?', '출산으로 입원하면 보상돼?'];
  view.innerHTML = `<h1>약관 Q&amp;A</h1><p class="sub">AI 에이전트가 약관을 검색해 조항을 근거로 답합니다. (로컬 GPU · Gemma 4)</p>
  <div class="card"><label>질문</label><textarea id="q"></textarea>
    <div class="actions" style="margin:8px 0 12px">${EXAMPLES.map((e) => `<button class="btn ghost sm ex">${esc(e)}</button>`).join('')}</div>
    <div class="actions"><button class="btn" id="ask">질문하기</button><span class="small muted" id="hint">20~60초 정도 걸려요.</span></div>
    <div class="error" id="err" hidden></div><div id="answer" style="margin-top:14px"></div></div>`;
  view.querySelectorAll('.ex').forEach((b) => (b.onclick = () => ($('q').value = b.textContent)));
  $('ask').onclick = async () => {
    const question = $('q').value.trim();
    if (!question) return;
    $('ask').disabled = true; $('err').hidden = true;
    const started = Date.now();
    const timer = setInterval(() => ($('hint').textContent = `AI가 약관을 검토 중… ${Math.round((Date.now() - started) / 1000)}초`), 1000);
    try {
      const r = await post('/ai/chat', { question });
      $('answer').innerHTML = renderMarkdown(r.answer || '(빈 응답)') +
        `<p class="small muted" style="margin-top:12px">도구 호출: ${r.trace.map((t) => `<span class="rule">${esc(t.tool)}</span>`).join(' → ')} · ${esc(r.model)} · ${(r.latency_ms / 1000).toFixed(1)}초</p>`;
    } catch (e) { showError($('err'), e); } finally { clearInterval(timer); $('ask').disabled = false; $('hint').textContent = '20~60초 정도 걸려요.'; }
  };
}

route();
