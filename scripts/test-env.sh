#!/usr/bin/env bash
set -euo pipefail

PORTS=(9099 8080 5001 9199)
PROJECT_ID="${SEED_PROJECT_ID:-cymbal-fresh}"
AUTH_HOST="127.0.0.1:9099"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND="$ROOT/../Corus-Web/backend/functions"

if [[ -z "${JAVA_HOME:-}" ]]; then
  JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  if [[ -x "$JBR/bin/java" ]]; then
    export JAVA_HOME="$JBR"
  else
    echo "JAVA_HOME unset and no Android Studio JBR found." >&2
    echo "Set JAVA_HOME to a JDK 17+ before running." >&2
    exit 1
  fi
fi
export PATH="$JAVA_HOME/bin:$PATH"

if ! curl -sf "http://$AUTH_HOST/emulator/v1/projects/$PROJECT_ID/config" >/dev/null; then
  echo "Firebase emulators are not running." >&2
  echo "Start them in another shell, then re-run this script:" >&2
  echo "  cd $BACKEND && JAVA_HOME=\"$JAVA_HOME\" npm run emulator" >&2
  exit 1
fi

if ! adb get-state >/dev/null 2>&1; then
  echo "No Android device or emulator attached." >&2
  echo "Launch one from Android Studio, or: emulator -avd <name>" >&2
  exit 1
fi

for port in "${PORTS[@]}"; do
  adb reverse "tcp:$port" "tcp:$port" >/dev/null
done

HOST_PROP="$(grep -E '^firebase\.emulator\.host=' "$ROOT/local.properties" 2>/dev/null | cut -d= -f2- || true)"
if [[ -z "$HOST_PROP" ]]; then
  echo "firebase.emulator.host is not set in local.properties." >&2
  echo "Add this line, then re-run:" >&2
  echo "  firebase.emulator.host=127.0.0.1" >&2
  exit 1
fi

(cd "$BACKEND" && npm run --silent emulator:seed)

(cd "$ROOT" && ./gradlew --quiet :app:assembleDebug)
adb install -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk" >/dev/null
adb shell am force-stop fm.corus.android
adb shell monkey -p fm.corus.android -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1

cat <<EOF

Ready. Sign in with +1 555 123 4567, then read the code with:
  curl -s http://$AUTH_HOST/emulator/v1/projects/$PROJECT_ID/verificationCodes
EOF
