# Ax — 보험 AI 심사 보조 시스템

[![CI](https://github.com/creepereye1204/ax-insurance/actions/workflows/ci.yml/badge.svg)](https://github.com/creepereye1204/ax-insurance/actions/workflows/ci.yml)

> 데모: https://www.insurance.r-e.kr

보험 가입(가입설계 → 선심사 → 알릴의무 고지 → 청약서·전자서명 → 전산심사)부터 계약·청구까지의 **보험 업무 시스템**과,
약관을 근거로 심사·지급 검토 의견을 내는 **AI 에이전트**.

- 가상 상품 201개(종신·정기·건강·암·실손·어린이·운전자·치아·연금·변액연금·변액유니버설·저축), 요율표 11.5만 행
- 가상 고객 3만 명, 기존 계약 6만 건, 보험금 청구 50만 건 — 성능 개선 실험용
- 금융소비자보호법: 일반/전문 금융소비자, 변액 적합성 원칙, 고령 소비자 보호
- 업무 시나리오 14개 자동 테스트 → [docs/scenarios.md](docs/scenarios.md)

보험사 채널계 SI 실무를 하면서 "레거시 업무 시스템에 AI 를 어떻게 안전하게 붙일 수 있을까?" 라는 질문에서 시작했다.

## 아키텍처

```
                    ┌──────────────────────── Docker Compose ────────────────────────┐
[브라우저] ─ HAProxy ─▶ gateway (nginx)                                                │
                    │   ├ /           데모 화면                                        │
                    │   ├ /core/*  ─▶ core  (Spring Boot 3, Java 21)  ── 미니 기간계    │
                    │   │              계약 / 청구 원장, 청구 상태 머신                   │
                    │   │              청구 접수 → 커밋 후 비동기 AI 검토 요청 ──┐        │
                    │   └ /ai/*    ─▶ api   (FastAPI, Python 3.12)  ◀──────────┘        │
                    │                  Agent Loop (직접 구현)                            │
                    │                  ├ list_products  상품 목록                        │
                    │                  ├ search_policy  약관 벡터 검색                   │
                    │                  └ get_article    조항 전문 조회                   │
                    │                        │                                         │
                    │   db (PostgreSQL 16 + pgvector)   core 스키마 / 약관 청크 / 실행 로그 │
                    └────────────────────────┼─────────────────────────────────────────┘
                                             ▼
                                Ollama (gemma4:12b, RTX 2080 Ti)  ※ Claude API 로 전환 가능
```

## 핵심 설계

### 1. AI 는 의견만, 결정은 사람이
청구 상태 머신: `RECEIVED → AI_REVIEWING → AI_REVIEWED → APPROVED / REJECTED`
AI 검토가 끝나야 심사 결정이 가능하고, 지급액은 청구액을 넘을 수 없다. 규칙은 엔티티(`Claim`)에 두고 단위 테스트로 검증한다.

### 2. AI 가 죽어도 기간계는 멈추지 않는다
- 청구 접수는 즉시 응답(202), AI 검토는 **트랜잭션 커밋 이후** `@TransactionalEventListener` + `@Async` 로 비동기 수행
- AI 호출(수십 초)은 트랜잭션 밖에서 수행해 DB 커넥션을 오래 점유하지 않음
- 타임아웃·통신 오류 시 `MANUAL_REVIEW` 로 전환 → 심사자가 수동 처리

### 3. 근거 있는 답변
- 약관을 `제N조(제목)` 단위로 청킹 → 답변에 `[상품명 제N조(제목), p.페이지]` 인용
- 에이전트는 LangChain 등 프레임워크 없이 tool use 루프를 직접 구현
- 모든 실행의 도구 호출 기록·토큰·지연시간을 `agent_runs` 에 저장 (실패 분석 / Eval 용)

### 4. 공개 배포 시 공용 자원 보호
- 게이트웨이에서 데모에 필요한 API 만 허용 (`/ai/ingest`, 실행 로그, 고객 API 등 차단)
- LLM 호출은 IP 당 분당 6회 + 전체 동시 2건으로 제한 (공용 GPU 보호)

## 문제 해결 기록

개발·배포 중 겪은 문제를 원인 분석부터 정리한다 → [docs/troubleshooting.md](docs/troubleshooting.md)

- 에이전트가 도구를 쓰지 않고 되묻던 문제 (실행 로그로 원인 추적)
- Spring → FastAPI 연계 시 요청 본문 유실 (JDK HttpClient 의 h2c 업그레이드)
- 공유 프록시 타임아웃과 LLM 응답 시간 충돌 (공유 인프라 최소 변경)
- 다단 프록시에서 IP별 요청 제한이 전체 제한으로 동작할 뻔한 문제
- nginx 설정 수정이 반영되지 않던 문제 (파일 bind mount 와 inode)
- 요청 제한에 걸린 검색이 조용히 실패하던 문제
- SPA 화면 전환 시 경쟁 상태로 이전 화면이 덮어쓰던 문제
- 로드밸런서 적용 후 Host 헤더의 밑줄 때문에 전 요청이 400 이던 문제
- 무중단 배포 테스트가 실제로는 컨테이너를 교체하지 않았던 문제

## 실행

```bash
cp .env.example .env     # POSTGRES_PASSWORD 변경, Claude 사용 시 LLM_PROVIDER=anthropic + ANTHROPIC_API_KEY
docker compose up -d --build
```

- 데모 화면: http://localhost:8032
- AI 서비스 Swagger: http://localhost:8030/docs
- 기간계: http://localhost:8031

최초 기동 시 `data/policies/` 의 약관을 자동 적재한다. 약관을 추가했으면 `docker compose exec api python -m app.ingest`.

> Ollama 는 별도 compose(`llm_default` 네트워크)에서 실행 중인 것을 사용한다. 다른 환경이면 `OLLAMA_HOST` 를 지정하거나 `LLM_PROVIDER=anthropic` 을 쓴다.

### 테스트

```bash
cd core && gradle test              # 단위 테스트 (룰 엔진, 청구 상태, 예시표 등 24개)
python3 scripts/scenarios.py        # 업무 시나리오 14개 (API 대상)
```

## 디렉토리

```
api/        AI 서비스 (FastAPI) — ingest, tools, agent loop
core/       미니 기간계 (Spring Boot) — customer, product, contract, claim, ai(client)
gateway/    nginx 설정 + 데모 화면
db/         초기 스키마
data/       약관 문서 (샘플은 테스트용 가상 약관)
```

## 로드맵

- [x] 약관 RAG 에이전트 + 실행 로그
- [x] 로컬 LLM(Gemma 4) / Claude 전환
- [x] 미니 기간계 (계약·청구) + 비동기 AI 검토 + 장애 시 수동심사
- [x] 공개 배포 (게이트웨이, 요청 제한)
- [ ] 실제 약관 PDF 적재 (표 / 특약 구조)
- [ ] Eval: 질문-정답 조항 셋으로 검색 Recall@k·답변 정확도 측정, Gemma vs Claude 비교
- [x] 고객관리 · 선심사 · 청약/알릴의무 고지 · 전산심사(룰 엔진) · 본사심사(AI 의견) · 계약 성립 ([설계 문서](docs/underwriting-design.md))
- [x] 청구 시 부담보·고지의무 위반 의심 점검
- [x] 가입설계(상품 201종, 요율표, 예시표, 적합성) → 설계서 → 청약서·전자서명 → 청약철회
- [x] 대량 가상 데이터 (고객 3만, 계약 6만, 청구 50만)
- [x] 보험나이(상령일), 상령 후 보험료, 초회·환산보험료, 설계서 유효기간 ≤ 상령일 전날
- [x] 동적 고지(표준 / 간편 3-2-5), 룰 적용 범위·룰셋 v2, 고지 기반 선심사 + 신호등
- [x] 특약 연관 규칙(필수 동반·동시 가입 불가·가입금액 비율 한도), 플랜(기본·설계사 저장), 가입설계 임시저장
- [x] 고객유형(가망/계약)·운전 위험(룰셋 v3)·가족관계·가상고객 즉시 생성·가상 주민번호(AES-GCM·블라인드 인덱스·열람 감사)·변액 당일 재진단
- [ ] 법인고객(단체·종업원)
- [x] 서킷브레이커(AI 호출), 기간계 2대 로드밸런싱, 롤링 무중단 배포, CI(GitHub Actions) — [docs/operations.md](docs/operations.md)
- [x] CD (main CI 성공 → 운영 서버 self-hosted runner 롤링 배포)
- [ ] 성능 측정·개선 (설계 문서 12절)
- [ ] 직접 구현 에이전트 vs LangGraph 비교 (Eval)
- [ ] 위험도 예측 모델 (건강검진 공개데이터) + 심사 의견 에이전트
- [ ] 대외계: 가상 병원 시스템 → 고정길이 전문 게이트웨이 → 청구 자동 접수
