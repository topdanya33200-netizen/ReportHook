#!/usr/bin/env bash
# End-to-end test of ReportHook on a real Paper server (used by .github/workflows/server-test.yml).
#
#  1. downloads the newest Paper build of PAPER_LINE (e.g. 26.1 or 26.3) and installs the built plugin
#  2. serves a fake Discord API on https://discord.com: the name points at 127.0.0.1 and the server
#     JVM trusts a self-signed certificate; the very first request gets HTTP 429 to test retries
#  3. starts the server and plays scenario.mjs with two bot players
#  4. stops the server and fails if the plugin logged a stack trace
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
HERE="$ROOT/.github/server-test"
WORK="$ROOT/build/server-test"
PAPER_LINE=${PAPER_LINE:-26.3}
BOT_VERSION=${BOT_VERSION:-26.1}
TOKEN=test_token_abcdefghijklmnopqrstuvwxyz
UA="ReportHook-CI (+https://github.com/${GITHUB_REPOSITORY:-topdanya33200-netizen/ReportHook})"
API=https://fill.papermc.io/v3/projects/paper

rm -rf "$WORK"
mkdir -p "$WORK/server/plugins/ReportHook"
cd "$WORK"

echo "::group::Download Paper $PAPER_LINE"
VERSION=$(curl -fsS -H "User-Agent: $UA" "$API" \
  | jq -r --arg line "$PAPER_LINE" '.versions | to_entries[] | .value[] | select(. == $line or startswith($line + "."))' \
  | sort -V | tail -n 1)
[ -n "$VERSION" ] || { echo "No Paper version matches $PAPER_LINE"; exit 1; }
URL=$(curl -fsS -H "User-Agent: $UA" "$API/versions/$VERSION/builds" \
  | jq -r 'first(.[] | select(.channel == "STABLE") | .downloads."server:default".url) // .[0].downloads."server:default".url')
echo "Paper $VERSION: $URL"
curl -fsSL -H "User-Agent: $UA" -o server/paper.jar "$URL"
echo "::endgroup::"

echo "::group::Install plugins"
cp "$ROOT"/build/libs/ReportHook-*.jar server/plugins/
if [[ "$VERSION" != "$BOT_VERSION" && "$VERSION" != "$BOT_VERSION".* ]]; then
  # The bots speak $BOT_VERSION; ViaVersion and ViaBackwards let them join newer servers
  gh release download --repo ViaVersion/ViaVersion --pattern 'ViaVersion-*.jar' --dir server/plugins
  gh release download --repo ViaVersion/ViaBackwards --pattern 'ViaBackwards-*.jar' --dir server/plugins
fi
ls server/plugins
unzip -p "$ROOT"/build/libs/ReportHook-*.jar config.yml > server/plugins/ReportHook/config.yml
sed -i \
  -e 's|^  enabled: false|  enabled: true|' \
  -e "s|^  webhook-url: \"\"|  webhook-url: \"https://discord.com/api/webhooks/123456789012345678/$TOKEN\"|" \
  -e 's|^  mention-role-id: ""|  mention-role-id: "424242424242"|' \
  server/plugins/ReportHook/config.yml
grep -n "enabled:\|webhook-url:\|mention-role-id:" server/plugins/ReportHook/config.yml | sed "s|$TOKEN|<token>|"
echo "eula=true" > server/eula.txt
cat > server/server.properties <<'EOF'
online-mode=false
enforce-secure-profile=false
white-list=false
level-type=minecraft\:flat
generate-structures=false
spawn-protection=0
view-distance=4
simulation-distance=4
motd=ReportHook server test
EOF
echo "::endgroup::"

echo "::group::Fake Discord API"
openssl req -x509 -newkey rsa:2048 -nodes -days 2 -subj "/CN=discord.com" \
  -addext "subjectAltName=DNS:discord.com" -keyout discord.key -out discord.crt 2>/dev/null
cp "$JAVA_HOME/lib/security/cacerts" truststore.jks
chmod u+w truststore.jks
keytool -importcert -noprompt -alias fake-discord -file discord.crt -keystore truststore.jks -storepass changeit
echo "127.0.0.1 discord.com" | sudo tee -a /etc/hosts
: > discord-requests.jsonl
sudo python3 "$HERE/fake_discord.py" "$WORK/discord-requests.jsonl" discord.crt discord.key 443 --rate-limit-first \
  > fake-discord.log 2>&1 &
sleep 1
cat fake-discord.log
echo "::endgroup::"

echo "::group::Start Paper $VERSION"
: > console.in
tail -n +1 -f "$WORK/console.in" | (cd server && exec java -Xms1G -Xmx2G \
  -Dterminal.jline=false -Dterminal.ansi=false \
  -Djavax.net.ssl.trustStore="$WORK/truststore.jks" -Djavax.net.ssl.trustStorePassword=changeit \
  -jar paper.jar --nogui) > server.log 2>&1 &
SERVER_PID=$!
for _ in $(seq 1 300); do
  grep -q 'Done (' server.log && break
  kill -0 "$SERVER_PID" 2>/dev/null || { cat server.log; echo "::error::Paper exited during startup"; exit 1; }
  sleep 1
done
grep -q 'Done (' server.log || { tail -n 100 server.log; echo "::error::Paper did not start in time"; exit 1; }
# Newer versions enable the whitelist by default; the bots must be able to join
echo "whitelist off" >> console.in
for _ in $(seq 1 20); do grep -qi "whitelist is" server.log && break; sleep 0.5; done
grep -E "\[(ReportHook|ViaVersion|ViaBackwards)\]|Done \(|[Ww]hitelist is" server.log || true
echo "::endgroup::"

echo "::group::Scenario"
(cd "$HERE" && npm install --no-audit --no-fund --loglevel=error)
set +e
SERVER_LOG="$WORK/server.log" CONSOLE_IN="$WORK/console.in" DISCORD_LOG="$WORK/discord-requests.jsonl" \
  BOT_VERSION="$BOT_VERSION" WEBHOOK_TOKEN="$TOKEN" timeout 300 node "$HERE/scenario.mjs"
STATUS=$?
set -e
echo "::endgroup::"

echo "::group::Stop Paper"
echo "stop" >> console.in
for _ in $(seq 1 60); do
  kill -0 "$SERVER_PID" 2>/dev/null || break
  sleep 1
done
kill "$SERVER_PID" 2>/dev/null || true
pkill -f "tail -n +1 -f $WORK/console.in" || true
sudo pkill -f fake_discord.py || true
grep "\[ReportHook\]" server.log || true
echo "::endgroup::"

# Our package only appears in the log inside stack traces
if grep -n "\.reporthook\." server.log; then
  echo "::error::ReportHook logged a stack trace, see server.log"
  STATUS=1
fi
if [ "$STATUS" -ne 0 ]; then
  echo "::group::Server log (warnings, errors and connections)"
  grep -n -i -E "WARN|ERROR|exception|lost connection|disconnect|kicked|left the game|joined the game" server.log | head -n 200 || true
  echo "::endgroup::"
fi
exit "$STATUS"
