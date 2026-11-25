#!/usr/bin/env bash
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
python3 tests/check-rc173-w2-hardening.py
python3 tests/check-rc173-w3-dev-build.py
bash tests/run-rc173-play-readiness-gate.sh
WORK="$(mktemp -d)"
trap 'rm -rf -- "$WORK"' EXIT
mkdir -p "$WORK/com/halftonestudio"
cat > "$WORK/com/halftonestudio/PhotoMemoryPolicyHostTest.java" <<'JAVA'
package com.halftonestudio;
public final class PhotoMemoryPolicyHostTest {
  public static void main(String[] args){
    check(PhotoMemoryPolicy.previewLimit(1800,128L<<20,128,true)==960);
    check(PhotoMemoryPolicy.previewLimit(2400,192L<<20,192,false)==1280);
    check(PhotoMemoryPolicy.previewLimit(2400,512L<<20,512,false)==2400);
    check(PhotoMemoryPolicy.previewLimit(1800,512L<<20,512,false)==1800);
    System.out.println("PASS executable low-memory bitmap preview policy");
  }
  static void check(boolean value){if(!value)throw new AssertionError("photo preview memory limit");}
}
JAVA
javac -d "$WORK" app/src/main/java/com/halftonestudio/PhotoMemoryPolicy.java "$WORK/com/halftonestudio/PhotoMemoryPolicyHostTest.java"
java -cp "$WORK" com.halftonestudio.PhotoMemoryPolicyHostTest
printf '\nPASS RC173-W2 host regression / release hardening gates (Android SDK remains required).\n'
