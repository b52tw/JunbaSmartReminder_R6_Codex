#!/usr/bin/env bash
# Secrets are supplied by GitHub Actions, never arguments or repository files.
set +x
set -euo pipefail
umask 077
if [[ $# != 2 ]]; then
    echo '用法：bash tools/sign_release_apk.sh unsigned.apk signed.apk' >&2
    exit 1
fi
for name in JUNBA_KEYSTORE_BASE64 JUNBA_STORE_PASSWORD JUNBA_KEY_ALIAS JUNBA_KEY_PASSWORD JUNBA_EXPECTED_CERT_SHA256; do
    if [[ -z "${!name:-}" ]]; then
        echo "缺少簽章設定：$name；停止，不會改用隨機 debug 金鑰。" >&2
        exit 1
    fi
done
expected_cert="$(printf '%s' "$JUNBA_EXPECTED_CERT_SHA256" | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]')"
if [[ ! "$expected_cert" =~ ^[0-9a-f]{64}$ ]]; then
    echo '簽章指紋必須是 SHA-256（64 個十六進位字元）。' >&2
    exit 1
fi
sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
build_tools="$sdk_root/build-tools/35.0.0"
for name in apksigner zipalign aapt; do
    test -x "$build_tools/$name" || { echo "缺少 Android build-tools 35.0.0：$name" >&2; exit 1; }
done
test -s "$1" || { echo '找不到未簽章 APK。' >&2; exit 1; }
if [[ -e "$2" || -L "$2" ]]; then
    echo '輸出檔案已存在；停止以免覆蓋。' >&2
    exit 1
fi
badging="$("$build_tools/aapt" dump badging "$1")"
case "$badging" in
    "package: name='tw.junba.reminder' "*) ;;
    *) echo '套件名稱不是 tw.junba.reminder，停止簽署。' >&2; exit 1 ;;
esac
if grep -q '^application-debuggable' <<< "$badging"; then
    echo '此檔案為 debug APK；正式簽章流程只接受 release APK。' >&2
    exit 1
fi
"$build_tools/zipalign" -c 4 "$1"
private_dir="$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/junba-signing.XXXXXX")"
trap 'rm -rf "$private_dir"' EXIT
printf '%s' "$JUNBA_KEYSTORE_BASE64" | base64 --decode > "$private_dir/signing.jks"
"$build_tools/apksigner" sign --ks "$private_dir/signing.jks" \
    --ks-key-alias "$JUNBA_KEY_ALIAS" \
    --ks-pass env:JUNBA_STORE_PASSWORD --key-pass env:JUNBA_KEY_PASSWORD \
    --out "$private_dir/signed.apk" "$1"
verification="$("$build_tools/apksigner" verify --verbose --print-certs "$private_dir/signed.apk")"
actual_cert="$(awk '/^Signer #1 certificate SHA-256 digest:/ {print $NF}' <<< "$verification")"
if [[ "$actual_cert" != "$expected_cert" ]]; then
    echo '簽署憑證與預期指紋不符；停止，不會輸出 APK。' >&2
    exit 1
fi
"$build_tools/zipalign" -c 4 "$private_dir/signed.apk"
mkdir -p "$(dirname "$2")"
# Publish the verified APK without overwriting an existing file, including races.
python3 - "$private_dir/signed.apk" "$2" <<'PY'
import os, pathlib, shutil, sys, tempfile
target = pathlib.Path(sys.argv[2])
fd, staging = tempfile.mkstemp(prefix='.junba-apk-', dir=target.parent)
try:
    with os.fdopen(fd, 'wb') as destination, open(sys.argv[1], 'rb') as source:
        shutil.copyfileobj(source, destination)
        destination.flush()
        os.fsync(destination.fileno())
    os.link(staging, target)  # Atomic publication; fails if the target already exists.
finally:
    os.unlink(staging)
PY
printf 'APK 簽章驗證通過；憑證 SHA-256：%s\n' "$actual_cert"
