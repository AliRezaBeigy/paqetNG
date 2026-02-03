#!/bin/bash
# Build hev-socks5-tunnel for Android (same as v2rayNG).
# Requires: Android NDK, set NDK_HOME. Run from project root.
# Output: app/src/main/jniLibs/{arm64-v8a,armeabi-v7a,x86,x86_64}/libhev-socks5-tunnel.so
set -o errexit
set -o pipefail
set -o nounset
__dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
__file="${__dir}/$(basename "${BASH_SOURCE[0]}")"

if [[ ! -d "${NDK_HOME:-}" ]]; then
  echo "Android NDK: NDK_HOME not set or not a directory. Set env NDK_HOME."
  exit 1
fi

if [[ ! -d "$__dir/hev-socks5-tunnel" ]]; then
  echo "hev-socks5-tunnel not found. Run: git submodule update --init"
  exit 1
fi

NDK_BUILD="$NDK_HOME/ndk-build"
[[ ! -x "$NDK_BUILD" && -f "$NDK_HOME/ndk-build.cmd" ]] && NDK_BUILD="$NDK_HOME/ndk-build.cmd"
if [[ ! -x "$NDK_BUILD" && ! -f "$NDK_BUILD" ]]; then
  echo "ndk-build not found in NDK_HOME"
  exit 1
fi

TMPDIR=$(mktemp -d)
cleanup() { rm -rf "$TMPDIR"; }
trap 'cleanup; exit 1' ERR INT

mkdir -p "$TMPDIR/jni"
echo 'include $(call all-subdir-makefiles)' > "$TMPDIR/jni/Android.mk"
ln -s "$__dir/hev-socks5-tunnel" "$TMPDIR/jni/hev-socks5-tunnel"

"$NDK_BUILD" \
  NDK_PROJECT_PATH="$TMPDIR" \
  APP_BUILD_SCRIPT="$TMPDIR/jni/Android.mk" \
  "APP_ABI=armeabi-v7a arm64-v8a x86 x86_64" \
  APP_PLATFORM=android-24 \
  NDK_LIBS_OUT="$TMPDIR/libs" \
  NDK_OUT="$TMPDIR/obj" \
  "APP_CFLAGS=-O3 -DPKGNAME=com/v2ray/ang/service" \
  "APP_LDFLAGS=-Wl,--build-id=none -Wl,--hash-style=gnu"

OUT="$__dir/app/src/main/jniLibs"
mkdir -p "$OUT"
cp -r "$TMPDIR/libs/"* "$OUT/"
cleanup
echo "Done. libs copied to $OUT"
