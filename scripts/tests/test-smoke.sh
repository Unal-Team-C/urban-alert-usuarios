#!/usr/bin/env bash
# Tests smoke-gateway-usuarios.sh with a fake `curl` on the PATH that plays the gateway (no network needed).
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
script="$here/../smoke-gateway-usuarios.sh"
fake_bin="$(mktemp -d)"
trap 'rm -rf "$fake_bin"' EXIT

# FAKE_MODE: good (correct gateway), open-post (unsigned POST accepted), down (every call 503).
cat >"$fake_bin/curl" <<'FAKE'
#!/usr/bin/env bash
out="" method="GET" url=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    -o) out="$2"; shift 2 ;;
    -X) method="$2"; shift 2 ;;
    -w|-H|-d|--max-time) shift 2 ;;
    -s) shift ;;
    *) url="$1"; shift ;;
  esac
done
path="/${url#https://*/}"
respond() { printf '%s' "$2" >"$out"; printf '%s' "$1"; }
[[ "${FAKE_MODE:-good}" == "down" ]] && { respond 503 '{"message":"Service Unavailable"}'; exit 0; }
admin='{"id":"018f4c2a-0000-7000-8000-000000000001","name":"Administrator","email":"admin@urbanalert.com","role":"ADMIN"}'
case "$method $path" in
  "GET /api/v1/usuarios") respond 200 "[$admin]" ;;
  "GET /api/v1/usuarios/018f4c2a-0000-7000-8000-000000000001") respond 200 "$admin" ;;
  "POST /api/v1/usuarios")
    if [[ "${FAKE_MODE:-good}" == "open-post" ]]; then respond 201 '{"id":"x"}'; else respond 403 '{"message":"Forbidden"}'; fi ;;
  "DELETE /api/v1/usuarios/"*) respond 403 '{"message":"Forbidden"}' ;;
  "GET /api/v1/usuarios/"*/role) respond 404 '{"message":"Not Found"}' ;;
  "GET /api/v1/usuarios/"*) respond 404 '{"code":404,"message":"User not found"}' ;;
  *) respond 404 '{"message":"Not Found"}' ;;
esac
FAKE
chmod +x "$fake_bin/curl"

failures=0
check() {
  local name="$1" mode="$2" expected_code="$3" expected_text="$4" output code
  set +e
  local target="https://api123.execute-api.us-east-2.amazonaws.com"
  [[ "$name" == "bad argument is rejected" ]] && target="http://insecure"
  output="$(FAKE_MODE="$mode" SMOKE_ATTEMPTS=2 SMOKE_SLEEP=0 PATH="$fake_bin:$PATH" bash "$script" "$target" 2>&1)"
  code=$?
  set -e
  if [[ "$code" == "$expected_code" && "$output" == *"$expected_text"* ]]; then
    echo "ok   $name"
  else
    echo "FAIL $name (exit $code): $output"
    failures=$((failures + 1))
  fi
}

check "correct gateway passes" good 0 "All smoke checks passed."
check "unsigned POST accepted fails" open-post 1 "FAIL unsigned POST -> 201"
check "unavailable service fails" down 1 "attempt 2: GET /api/v1/usuarios returned 503"
check "bad argument is rejected" good 2 "usage:"

exit "$failures"
