#!/usr/bin/env bash
# CD: main 의 CI 가 성공하면 self-hosted runner 가 실행한다 (.github/workflows/cd.yml).
#
#   1. 운영 디렉토리를 해당 커밋으로 fast-forward
#      (로컬에 커밋 안 된 변경이 있거나 브랜치가 갈라졌으면 실패 → 배포 중단, 작업 내용 보호)
#   2. 바뀐 부분만 배포
#      core/              → 기간계 롤링 무중단 배포 (scripts/deploy.sh)
#      api/               → AI 서비스 재빌드 (1대라 잠깐 중단, 그동안 기간계는 서킷브레이커 → 수동심사)
#      gateway/conf.d/    → nginx 설정 검사 후 reload
#      gateway/html/      → 디렉토리 마운트라 pull 만으로 반영
#
# 사용법: scripts/cd.sh <commit-sha>
set -euo pipefail
SHA=${1:?배포할 커밋 SHA 를 지정하세요}
cd "$(dirname "$0")/.."

before=$(git rev-parse HEAD)
git fetch --quiet origin main
git merge --ff-only --quiet "$SHA"
after=$(git rev-parse HEAD)
changed=$(git diff --name-only "$before" "$after")

echo "[cd] ${before:0:7} → ${after:0:7}"
if [ -z "$changed" ]; then
  echo "[cd] 변경 없음"
  exit 0
fi
echo "$changed" | sed 's/^/[cd]   /'

if grep -q '^core/' <<<"$changed"; then
  scripts/deploy.sh
fi
if grep -q '^api/' <<<"$changed"; then
  echo "[cd] AI 서비스 재빌드"
  docker compose up -d --build --no-deps api
fi
if grep -q '^gateway/conf.d/' <<<"$changed"; then
  echo "[cd] 게이트웨이 설정 reload"
  docker compose exec -T gateway nginx -t
  docker compose exec -T gateway nginx -s reload
fi

echo "[cd] 운영 상태: $(curl -fs localhost:8032/core/ops/status)"
