#!/system/bin/sh
# app ↔ cammock 握手：一次性 nonce + 私有 token。
[ -n "${PORTALEX_CAMMOCK_TOKEN:-}" ] || { echo "cammock auth: missing token"; exit 1; }
[ -n "${PORTALEX_CAMMOCK_NONCE:-}" ] || { echo "cammock auth: missing nonce"; exit 1; }
[ -f "$DIR/auth.token" ] || { echo "cammock auth: no token file"; exit 1; }
[ -f "$DIR/auth.nonce" ] || { echo "cammock auth: no nonce file"; exit 1; }
[ "$PORTALEX_CAMMOCK_TOKEN" = "$(cat "$DIR/auth.token")" ] || { echo "cammock auth: token mismatch"; exit 1; }
[ "$PORTALEX_CAMMOCK_NONCE" = "$(cat "$DIR/auth.nonce")" ] || { echo "cammock auth: nonce mismatch"; exit 1; }
rm -f "$DIR/auth.nonce"
