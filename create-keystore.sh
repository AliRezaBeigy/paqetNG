#!/usr/bin/env bash
# Creates signing/release.keystore for PaqetNG release signing.
# Reads credentials from signing/keystore-credentials (ignored by git), or prompts if missing.
# File format: KEY_ALIAS=paqetng, KEYSTORE_PASSWORD=xxx, KEY_PASSWORD=xxx (one per line)
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SIGNING_DIR="$SCRIPT_DIR/signing"
KEYSTORE="$SIGNING_DIR/release.keystore"
CREDS_FILE="$SIGNING_DIR/keystore-credentials"

mkdir -p "$SIGNING_DIR"

read_from_file() {
  local key="$1"
  if [ -f "$CREDS_FILE" ]; then
    grep -E "^\s*${key}\s*=" "$CREDS_FILE" 2>/dev/null | head -1 | sed -E "s/^\s*${key}\s*=\s*//" | tr -d '\r'
  fi
}

ALIAS=$(read_from_file "KEY_ALIAS")
STORE_PASS=$(read_from_file "KEYSTORE_PASSWORD")
KEY_PASS=$(read_from_file "KEY_PASSWORD")

PROMPTED=0
if [ -z "$STORE_PASS" ]; then
  echo "Credentials file not found or incomplete: $CREDS_FILE"
  echo "Enter values (they will be saved to the file for next time)"
  echo ""
  if [ -z "$ALIAS" ]; then
    read -r -p "Key alias [paqetng]: " ALIAS
    ALIAS="${ALIAS:-paqetng}"
  fi
  read -r -s -p "Keystore password: " STORE_PASS
  echo ""
  read -r -s -p "Key password (Enter = same as keystore): " KEY_PASS
  echo ""
  KEY_PASS="${KEY_PASS:-$STORE_PASS}"
  PROMPTED=1
else
  ALIAS="${ALIAS:-paqetng}"
  KEY_PASS="${KEY_PASS:-$STORE_PASS}"
fi

# Save credentials to file when we prompted (so next run reads from file)
if [ "$PROMPTED" = "1" ]; then
  {
    echo "KEY_ALIAS=$ALIAS"
    echo "KEYSTORE_PASSWORD=$STORE_PASS"
    echo "KEY_PASSWORD=$KEY_PASS"
  } > "$CREDS_FILE"
  echo "Credentials saved to $CREDS_FILE (ignored by git)"
  echo ""
fi

if [ -f "$KEYSTORE" ]; then
  echo "Keystore already exists: $KEYSTORE"
  echo "Delete it first if you want to regenerate (rm $KEYSTORE)"
  echo ""
  echo "To get base64 for GitHub Secret ANDROID_KEYSTORE_BASE64, run:"
  echo "  base64 -w0 $KEYSTORE"
  echo ""
  echo "Use these GitHub Secrets (same values from your credentials file):"
  echo "  ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS, ANDROID_KEY_PASSWORD"
  exit 0
fi

keytool -genkey -v -keystore "$KEYSTORE" -keyalg RSA -keysize 2048 -validity 10000 \
  -alias "$ALIAS" -storepass "$STORE_PASS" -keypass "$KEY_PASS" \
  -dname "CN=PaqetNG, OU=Android, O=PaqetNG, L=Unknown, ST=Unknown, C=US"

echo ""
echo "Keystore created: $KEYSTORE"
echo ""
echo "=============================================="
echo "Add these as GitHub Repository Secrets:"
echo "  Settings -> Secrets and variables -> Actions -> New repository secret"
echo "=============================================="
echo ""
echo "1. ANDROID_KEYSTORE_BASE64"
echo "   Value: (run the command below and paste the single line of output)"
echo "   Command: base64 -w0 $KEYSTORE"
echo ""
BASE64=$(base64 -w0 "$KEYSTORE" 2>/dev/null || base64 < "$KEYSTORE")
echo "   (copy below - one line, no spaces/breaks)"
echo "$BASE64"
echo ""
echo "2. ANDROID_KEYSTORE_PASSWORD"
echo "   Value: $STORE_PASS"
echo ""
echo "3. ANDROID_KEY_ALIAS"
echo "   Value: $ALIAS"
echo ""
echo "4. ANDROID_KEY_PASSWORD"
echo "   Value: $KEY_PASS"
echo "=============================================="
