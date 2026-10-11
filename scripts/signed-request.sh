#!/usr/bin/env bash
# Sends a request signed with AWS SigV4 (service execute-api) to the shared API Gateway: the way to call the
# write routes of Usuarios (POST, PUT, PATCH …/role, DELETE), which require AWS_IAM (spec 001-migracion-aws
# RF-019). The identity needs execute-api:Invoke on those routes (account administrators have it).
#
# Credentials come from the environment, e.g. `eval "$(aws configure export-credentials --format env)"`:
#   AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY and, for temporary credentials, AWS_SESSION_TOKEN.
# Region: AWS_REGION (default us-east-2).
#
# Usage: scripts/signed-request.sh <METHOD> <url> [json body]
#   scripts/signed-request.sh POST "$GATEWAY/api/v1/usuarios" '{"name":"Ana","email":"ana@example.com","role":"USER"}'
#   scripts/signed-request.sh DELETE "$GATEWAY/api/v1/usuarios/<id>"
set -euo pipefail

if [[ $# -lt 2 || $# -gt 3 || "$2" != https://* ]]; then
  echo "usage: $0 <METHOD> <https url> [json body]" >&2
  exit 2
fi
if [[ -z "${AWS_ACCESS_KEY_ID:-}" || -z "${AWS_SECRET_ACCESS_KEY:-}" ]]; then
  echo "signed-request: set AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY (and AWS_SESSION_TOKEN if temporary)," >&2
  echo "e.g. eval \"\$(aws configure export-credentials --format env)\"" >&2
  exit 2
fi

method="$1"
url="$2"
region="${AWS_REGION:-us-east-2}"

args=(-sS -i -X "$method" --aws-sigv4 "aws:amz:${region}:execute-api")
if [[ $# -eq 3 ]]; then
  args+=(-H "Content-Type: application/json" --data "$3")
fi

# Credentials go through a curl config on stdin, never on the command line (visible to other processes).
{
  printf 'user = "%s:%s"\n' "$AWS_ACCESS_KEY_ID" "$AWS_SECRET_ACCESS_KEY"
  if [[ -n "${AWS_SESSION_TOKEN:-}" ]]; then
    printf 'header = "x-amz-security-token: %s"\n' "$AWS_SESSION_TOKEN"
  fi
} | curl --config - "${args[@]}" "$url"
echo
