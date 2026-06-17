#!/bin/sh
set -eu

BUCKET="${BUCKET:-ilmai-materials}"
ACCESS_KEY_ID="${ACCESS_KEY_ID:-ilmai-dev}"
SECRET_ACCESS_KEY="${SECRET_ACCESS_KEY:-ilmai-dev-secret}"
KEY_NAME="${KEY_NAME:-ilmai-dev-key}"
ADMIN_URL="${ADMIN_URL:-http://garage:3903}"
ADMIN_TOKEN="${ADMIN_TOKEN:-ilmai-dev-admin-token}"
ZONE="${ZONE:-dc1}"
CAPACITY="${CAPACITY:-1000000000}"

if ! command -v curl >/dev/null 2>&1 || ! command -v jq >/dev/null 2>&1; then
  echo "[garage-init] installing curl + jq..."
  apk add --no-cache curl jq >/dev/null
fi

AUTH="Authorization: Bearer ${ADMIN_TOKEN}"

echo "[garage-init] waiting for garage admin API at ${ADMIN_URL}..."
i=0
until curl -fsS -H "$AUTH" "${ADMIN_URL}/v1/status" >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -gt 60 ]; then
    echo "[garage-init] admin API not reachable after 60s"
    curl -v -H "$AUTH" "${ADMIN_URL}/v1/status" || true
    exit 1
  fi
  sleep 1
done

STATUS_JSON="$(curl -fsS -H "$AUTH" "${ADMIN_URL}/v1/status")"
NODE_ID="$(echo "$STATUS_JSON" | jq -r '.node')"
if [ -z "$NODE_ID" ] || [ "$NODE_ID" = "null" ]; then
  echo "[garage-init] could not resolve node id from /v1/status:"
  echo "$STATUS_JSON"
  exit 1
fi
echo "[garage-init] node id: ${NODE_ID}"

LAYOUT_JSON="$(curl -fsS -H "$AUTH" "${ADMIN_URL}/v1/layout")"
CURRENT_ROLE="$(echo "$LAYOUT_JSON" | jq -r --arg id "$NODE_ID" '.roles[]? | select(.id==$id) | .id' | head -n1 || true)"

if [ -z "$CURRENT_ROLE" ]; then
  echo "[garage-init] staging layout role for node..."
  STAGE_BODY="$(jq -n \
    --arg id "$NODE_ID" \
    --arg zone "$ZONE" \
    --argjson capacity "$CAPACITY" \
    '[{id:$id, zone:$zone, capacity:$capacity, tags:[]}]')"
  curl -fsS -X POST -H "$AUTH" -H 'Content-Type: application/json' \
    -d "$STAGE_BODY" "${ADMIN_URL}/v1/layout" >/dev/null

  NEXT_VERSION="$(curl -fsS -H "$AUTH" "${ADMIN_URL}/v1/layout" | jq -r '.version + 1')"
  echo "[garage-init] applying layout version ${NEXT_VERSION}..."
  curl -fsS -X POST -H "$AUTH" -H 'Content-Type: application/json' \
    -d "{\"version\": ${NEXT_VERSION}}" \
    "${ADMIN_URL}/v1/layout/apply" >/dev/null
else
  echo "[garage-init] layout already assigned for node."
fi

echo "[garage-init] ensuring bucket '${BUCKET}'..."
BUCKET_BODY="$(jq -n --arg ga "$BUCKET" '{globalAlias:$ga}')"
CREATE_BUCKET="$(curl -sS -o /tmp/cb.json -w '%{http_code}' \
  -X POST -H "$AUTH" -H 'Content-Type: application/json' \
  -d "$BUCKET_BODY" "${ADMIN_URL}/v1/bucket")"
if [ "$CREATE_BUCKET" != "200" ] && [ "$CREATE_BUCKET" != "409" ]; then
  echo "[garage-init] bucket create failed (HTTP ${CREATE_BUCKET}):"
  cat /tmp/cb.json
  exit 1
fi

BUCKET_ID="$(curl -fsS -H "$AUTH" \
  "${ADMIN_URL}/v1/bucket?globalAlias=${BUCKET}" | jq -r '.id')"
echo "[garage-init] bucket id: ${BUCKET_ID}"

echo "[garage-init] ensuring access key '${KEY_NAME}' (${ACCESS_KEY_ID})..."
KEY_INFO="$(curl -sS -o /tmp/ki.json -w '%{http_code}' -H "$AUTH" \
  "${ADMIN_URL}/v1/key?id=${ACCESS_KEY_ID}")"
if [ "$KEY_INFO" != "200" ]; then
  IMPORT_BODY="$(jq -n \
    --arg name "$KEY_NAME" \
    --arg ak "$ACCESS_KEY_ID" \
    --arg sk "$SECRET_ACCESS_KEY" \
    '{name:$name, accessKeyId:$ak, secretAccessKey:$sk}')"
  curl -fsS -X POST -H "$AUTH" -H 'Content-Type: application/json' \
    -d "$IMPORT_BODY" "${ADMIN_URL}/v1/key/import" >/dev/null
fi

echo "[garage-init] granting permissions on bucket..."
ALLOW_BODY="$(jq -n \
  --arg bid "$BUCKET_ID" \
  --arg ak "$ACCESS_KEY_ID" \
  '{bucketId:$bid, accessKeyId:$ak, permissions:{read:true, write:true, owner:true}}')"
curl -fsS -X POST -H "$AUTH" -H 'Content-Type: application/json' \
  -d "$ALLOW_BODY" "${ADMIN_URL}/v1/bucket/allow" >/dev/null

echo "[garage-init] done. bucket=${BUCKET} key=${ACCESS_KEY_ID}"
