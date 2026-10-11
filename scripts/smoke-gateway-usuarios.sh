#!/usr/bin/env bash
# Smoke test of the Usuarios service through the shared API Gateway (spec 001-migracion-aws HU-5, RF-019;
# plan D-011). Read-only: it never creates, changes or deletes users.
# Usage: scripts/smoke-gateway-usuarios.sh https://<api-id>.execute-api.<region>.amazonaws.com
# Exit 0 when every check passes. SMOKE_ATTEMPTS (default 30, every SMOKE_SLEEP=10 s) bounds the wait of the
# first check. Signed writes are verified manually with scripts/signed-request.sh: the runner role must not
# have execute-api:Invoke.
set -euo pipefail

if [[ $# -ne 1 || "$1" != https://* ]]; then
  echo "usage: $0 https://<api-id>.execute-api.<region>.amazonaws.com" >&2
  exit 2
fi
gateway="${1%/}"
base="$gateway/api/v1/usuarios"
admin_id="018f4c2a-0000-7000-8000-000000000001"
body="$(mktemp)"
trap 'rm -f "$body"' EXIT
failures=0

request() { # method url [data] -> prints the status code, body in $body
  local method="$1" url="$2" data="${3:-}"
  if [[ -n "$data" ]]; then
    curl -s -o "$body" -w '%{http_code}' --max-time 15 -X "$method" -H 'Content-Type: application/json' -d "$data" "$url" || true
  else
    curl -s -o "$body" -w '%{http_code}' --max-time 15 -X "$method" "$url" || true
  fi
}

pass() { echo "ok   $1"; }
fail() {
  echo "FAIL $1: $(head -c 300 "$body")"
  failures=$((failures + 1))
}

# 1. Public list: 200 with the seeded administrator. Retries while the new routes and tasks settle.
code=""
for attempt in $(seq 1 "${SMOKE_ATTEMPTS:-30}"); do
  code="$(request GET "$base")"
  [[ "$code" == "200" ]] && break
  echo "attempt $attempt: GET /api/v1/usuarios returned ${code:-no answer}"
  sleep "${SMOKE_SLEEP:-10}"
done
if [[ "$code" == "200" ]] && jq -e 'type == "array" and any(.[]; .email == "admin@urbanalert.com")' "$body" >/dev/null 2>&1; then
  pass "GET /api/v1/usuarios -> 200 with the administrator"
else
  fail "GET /api/v1/usuarios -> ${code:-no answer}, expected 200 with admin@urbanalert.com"
fi

# 2. Public lookup of the seeded administrator.
code="$(request GET "$base/$admin_id")"
if [[ "$code" == "200" ]] && jq -e '.email == "admin@urbanalert.com" and .role == "ADMIN"' "$body" >/dev/null 2>&1; then
  pass "GET /api/v1/usuarios/{admin} -> 200"
else
  fail "GET /api/v1/usuarios/{admin} -> ${code:-no answer}, expected 200 with the administrator"
fi

# 3. Unknown id: the service itself answers 404 with its error shape.
uuid="$(cat /proc/sys/kernel/random/uuid 2>/dev/null || uuidgen | tr '[:upper:]' '[:lower:]')"
code="$(request GET "$base/$uuid")"
if [[ "$code" == "404" ]] && jq -e '.code == 404' "$body" >/dev/null 2>&1; then
  pass "GET /api/v1/usuarios/{unknown} -> 404 from the service"
else
  fail "GET /api/v1/usuarios/{unknown} -> ${code:-no answer}, expected the service's {\"code\":404}"
fi

# 4. Writes reject unsigned requests at the gateway (they never reach the service).
code="$(request POST "$base" '{"name":"Smoke","email":"smoke@example.com","role":"USER"}')"
if [[ "$code" == "403" ]]; then pass "unsigned POST /api/v1/usuarios -> 403"; else fail "unsigned POST -> ${code:-no answer}, expected 403"; fi

code="$(request DELETE "$base/$uuid")"
if [[ "$code" == "403" ]]; then pass "unsigned DELETE /api/v1/usuarios/{id} -> 403"; else fail "unsigned DELETE -> ${code:-no answer}, expected 403"; fi

# 5. Routes the service does not publish stay unregistered.
for path in "/actuator/health" "/api/v1/usuarios/$uuid/role" "/api/v1/users"; do
  code="$(request GET "$gateway$path")"
  if [[ "$code" == "404" ]]; then pass "GET $path -> 404"; else fail "GET $path -> ${code:-no answer}, expected 404"; fi
done

if ((failures > 0)); then
  echo "$failures smoke check(s) failed"
  exit 1
fi
echo "All smoke checks passed."
