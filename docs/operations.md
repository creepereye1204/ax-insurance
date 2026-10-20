# 운영: 로드밸런싱 · 무중단 배포 · 서킷브레이커 · CI

## 구성

```
인터넷 ─ HAProxy(공유, TLS) ─ gateway(nginx) ─┬─ core-a (Spring Boot) ─┐
                                              ├─ core-b (Spring Boot) ─┼─ db (PostgreSQL)
                                              └─ api (FastAPI) ─ Ollama │
                                                       ▲                │
                                 core-a/b ─ 서킷브레이커 ┘                │
```

| 구성 요소 | 역할 |
|---|---|
| gateway | 정적 화면, 공개 API 화이트리스트, 요청 제한, **기간계 로드밸런싱** |
| core-a, core-b | 같은 이미지의 기간계 2대. 데모 데이터 적재는 core-a 만 (`AX_DEMO_ENABLED`) |
| api | AI 서비스 1대 (로컬 GPU 를 쓰므로 수평 확장하지 않음) |

## 로드밸런싱

`gateway/conf.d/default.conf`

```nginx
upstream core_backend {
    zone core_backend 64k;
    server core-a:8080 resolve max_fails=1 fail_timeout=5s;
    server core-b:8080 resolve max_fails=1 fail_timeout=5s;
    keepalive 16;
}
```

- **라운드로빈**: 10회 요청 → core-a 5회, core-b 5회 (`X-Ax-Instance` 응답 헤더로 확인)
- **resolve**: 컨테이너가 재생성돼 IP 가 바뀌어도 도커 DNS 를 다시 조회 (nginx 1.27.3+)
- **passive health check**: 응답하지 않는 인스턴스는 5초간 제외
- **재시도**: `proxy_next_upstream error timeout http_502 http_503` — 연결 실패 시 다른 인스턴스로.
  POST 같은 비멱등 요청은 요청을 보내기 전 실패(연결 실패)일 때만 재시도되므로 중복 처리되지 않는다

## 롤링(무중단) 배포

```bash
scripts/deploy.sh               # 이미지 빌드 → core-a 교체 → 헬스 확인 → core-b 교체 → 헬스 확인
scripts/deploy.sh --skip-build  # 이미지 그대로 재기동
```

- 컨테이너 헬스체크는 `/actuator/health/readiness` — 데이터 적재(ApplicationRunner)까지 끝나야 트래픽을 받는다
- 종료되는 인스턴스는 **graceful shutdown**(최대 30초)으로 처리 중인 요청을 마친다
- 헬스체크에 실패하면 배포를 멈춘다. 남은 한 대가 이전 버전으로 계속 서비스한다
- 게이트웨이는 바뀌지 않으므로 **공유 HAProxy 재시작이 필요 없다** (다른 서비스에 영향 없음)

**검증 결과 (2026-10-20)**: 두 인스턴스를 차례로 교체하는 50초 동안 0.2초 간격으로 요청

| 항목 | 결과 |
|---|---|
| 요청 | 223건, **전부 200** |
| 처리 인스턴스 | core-a 112건, core-b 111건 |
| 응답시간 | 평균 0.031초, 최대 0.27초 |

## 서킷브레이커 (기간계 → AI 서비스)

Resilience4j, `application.yml` 의 `resilience4j.circuitbreaker.instances.ai`

| 설정 | 값 | 의미 |
|---|---|---|
| sliding-window-size | 10 | 최근 10건 기준 |
| minimum-number-of-calls | 5 | 5건 이상 쌓여야 판단 |
| failure-rate-threshold | 50% | 실패율 50% 이상이면 OPEN |
| slow-call-duration-threshold | 120s | 2분 넘는 호출은 느린 호출로 집계 |
| wait-duration-in-open-state | 30s | 30초간 호출 차단 후 HALF_OPEN |
| permitted-calls-in-half-open | 2 | 시험 호출 2건으로 회복 판단 |

- OPEN 동안에는 AI 를 호출하지 않고 **즉시 수동심사로** 넘긴다 (장애 난 서비스에 요청을 쌓지 않음)
- 서킷이 열려도 `/actuator/health` 는 UP 유지(`allow-health-indicator-to-fail: false`) — AI 장애 때문에 로드밸런서가 정상 기간계를 빼지 않도록
- 서킷 상태는 인스턴스별이다. 화면 상단과 `GET /core/ops/status` 로 확인

**장애 주입 검증 (2026-10-20)**: AI 서비스 컨테이너를 중지하고 청구 7건 접수

| 단계 | 결과 |
|---|---|
| 청구 1~5 | AI 호출 실패(I/O error) → 수동심사, 실패율 100% |
| 서킷 | **OPEN** |
| 청구 6~7 | AI 호출 없이 즉시 수동심사 ("서킷 오픈" 메시지), notPermittedCalls 2 |
| AI 재기동 + 30초 | **HALF_OPEN** |
| 청구 2건 | AI 검토 성공 → **CLOSED** |

## CI (GitHub Actions)

`.github/workflows/ci.yml` — main 푸시와 PR 마다

| Job | 내용 |
|---|---|
| 기간계 단위 테스트 | `./gradlew test` (룰 엔진, 상태 머신, 예시표, 서킷브레이커 등) |
| 정적 검사 | Python 문법, 화면 JS 문법, docker compose 설정 |
| 업무 시나리오 테스트 | 도커로 DB + 기간계 기동(데이터 축소) → `scripts/scenarios.py` 14개 |

PR 에서는 읽기 권한만 쓰고, 비밀값이나 운영 서버에 접근하지 않는다.

### CD

`.github/workflows/cd.yml` → 운영 서버의 self-hosted runner(라벨 `ax-deploy`)가 `scripts/cd.sh <sha>` 실행

```
main 푸시 → CI(테스트) 성공 → CD: 운영 디렉토리 fast-forward → 바뀐 부분만 배포
                                   core/            → 롤링 무중단 배포
                                   api/             → AI 서비스 재빌드
                                   gateway/conf.d/  → nginx reload
                                   gateway/html/    → 반영 (디렉토리 마운트)
```

**공개 레포에서 self-hosted runner 를 쓰기 위한 안전장치**

| 위험 | 대응 |
|---|---|
| 악의적인 fork PR 이 워크플로를 고쳐 서버에서 코드 실행 | 외부 기여자 PR 워크플로는 **소유자 승인 후에만** 실행 (`fork-pr-contributor-approval: all_external_contributors`) |
| PR 이 배포 단계 변조 | `workflow_run` 은 항상 **main 의 워크플로 파일**로 실행 |
| PR 의 CI 결과로 배포 | `workflow_run.event == 'push'` 인 CI 만 배포 |
| 운영 디렉토리의 작업 내용 손실 | `git merge --ff-only` — 로컬 변경·분기가 있으면 배포 중단 |
| 동시 배포 | `concurrency: deploy` |

**러너 설치** (서버 관리자가 1회 실행, 사용자 `smalllab` 권한 서비스로 등록)

```bash
mkdir -p ~/actions-runner-ax && cd ~/actions-runner-ax
curl -sSL -o runner.tar.gz https://github.com/actions/runner/releases/download/v2.338.0/actions-runner-linux-x64-2.338.0.tar.gz
tar xzf runner.tar.gz && rm runner.tar.gz
./config.sh --url https://github.com/creepereye1204/ax-insurance \
  --token "$(gh api -X POST repos/creepereye1204/ax-insurance/actions/runners/registration-token -q .token)" \
  --name ax-homeserver --labels ax-deploy --work _work --unattended
sudo ./svc.sh install smalllab && sudo ./svc.sh start
```

제거: `sudo ./svc.sh stop && sudo ./svc.sh uninstall && ./config.sh remove --token <제거 토큰>`
