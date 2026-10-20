#!/usr/bin/env bash
# 기간계 롤링(무중단) 배포
#
#   1. 새 이미지를 한 번 빌드한다
#   2. core-a 를 교체 → readiness 헬스체크 통과까지 대기 (그동안 core-b 가 트래픽 처리)
#   3. core-b 를 교체 → 헬스체크 통과까지 대기 (그동안 core-a 가 트래픽 처리)
#
# 게이트웨이(nginx)는 응답 없는 인스턴스를 잠시 제외하고 다른 인스턴스로 재시도하며,
# 종료되는 인스턴스는 graceful shutdown 으로 처리 중인 요청을 마치고 내려간다.
# 헬스체크에 실패하면 거기서 멈춘다(나머지 한 대는 이전 버전으로 계속 서비스).
#
# 사용법: scripts/deploy.sh [--skip-build]
set -euo pipefail
cd "$(dirname "$0")/.."

wait_healthy() {
  local svc=$1 id status
  id=$(docker compose ps -q "$svc")
  for _ in $(seq 1 90); do
    status=$(docker inspect -f '{{.State.Health.Status}}' "$id" 2>/dev/null || echo unknown)
    if [ "$status" = healthy ]; then
      return 0
    fi
    sleep 2
  done
  echo "[deploy] $svc 헬스체크 실패 (상태: $status) — 배포를 중단합니다." >&2
  docker compose logs --tail 30 "$svc" >&2
  return 1
}

started=$(date +%s)
if [ "${1:-}" != "--skip-build" ]; then
  echo "[deploy] 이미지 빌드"
  docker compose build core-a
fi

for svc in core-a core-b; do
  echo "[deploy] $svc 교체 중..."
  docker compose up -d --no-deps --force-recreate "$svc"
  wait_healthy "$svc"
  echo "[deploy] $svc 정상 ($(docker compose exec -T "$svc" curl -fs localhost:8080/ops/status))"
done

echo "[deploy] 완료 ($(($(date +%s) - started))초)"
