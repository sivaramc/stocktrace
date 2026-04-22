#!/usr/bin/env bash
# Deploy stocktrace to a remote VPS over SSH.
#
# Usage:
#     deploy/deploy.sh                    # uses env vars below
#     VPS_HOST=... VPS_USER=... deploy/deploy.sh
#
# The script is idempotent. Run it from the repo root after each release.

set -euo pipefail

VPS_HOST="${VPS_HOST:?VPS_HOST is required (e.g. vps.example.com or 203.0.113.5)}"
VPS_USER="${VPS_USER:?VPS_USER is required (e.g. ubuntu)}"
VPS_SSH_KEY="${VPS_SSH_KEY:-$HOME/.ssh/id_ed25519}"
REMOTE_DIR="${REMOTE_DIR:-/opt/stocktrace}"

SSH=(ssh -i "$VPS_SSH_KEY" -o StrictHostKeyChecking=accept-new "$VPS_USER@$VPS_HOST")
RSYNC=(rsync -e "ssh -i $VPS_SSH_KEY -o StrictHostKeyChecking=accept-new" -avz --delete)

echo "==> Ensuring docker + docker compose plugin are installed on $VPS_HOST"
"${SSH[@]}" bash -s <<'REMOTE'
set -euo pipefail
if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sudo sh
  sudo usermod -aG docker "$USER" || true
fi
if ! docker compose version >/dev/null 2>&1; then
  sudo apt-get update
  sudo apt-get install -y docker-compose-plugin
fi
REMOTE

echo "==> Creating remote dir $REMOTE_DIR"
"${SSH[@]}" "sudo install -d -m 0755 -o $VPS_USER -g $VPS_USER $REMOTE_DIR"

echo "==> Syncing deploy/ to $VPS_HOST:$REMOTE_DIR"
"${RSYNC[@]}" deploy/docker-compose.yml deploy/Caddyfile "$VPS_USER@$VPS_HOST:$REMOTE_DIR/"

echo "==> Ensuring $REMOTE_DIR/.env exists"
# If the caller provided VPS_ENV_FILE, rsync it; otherwise require the remote
# to already have one.
if [[ -n "${VPS_ENV_FILE:-}" ]]; then
  "${RSYNC[@]}" "$VPS_ENV_FILE" "$VPS_USER@$VPS_HOST:$REMOTE_DIR/.env"
else
  "${SSH[@]}" "test -f $REMOTE_DIR/.env || { echo 'Missing $REMOTE_DIR/.env on server (copy deploy/.env.example, fill in values).' >&2; exit 1; }"
fi

echo "==> Pulling latest image and restarting stack"
"${SSH[@]}" bash -s <<REMOTE
set -euo pipefail
cd "$REMOTE_DIR"
sudo docker compose --env-file .env -f docker-compose.yml pull
sudo docker compose --env-file .env -f docker-compose.yml up -d --remove-orphans
sudo docker compose --env-file .env -f docker-compose.yml ps
REMOTE

echo "==> Deploy complete. Watch logs with:"
echo "    ssh $VPS_USER@$VPS_HOST 'sudo docker compose -f $REMOTE_DIR/docker-compose.yml logs -f app'"
