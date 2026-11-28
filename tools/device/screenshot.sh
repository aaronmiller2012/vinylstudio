#!/usr/bin/env bash
# screenshot.sh <out.png>
. "$(dirname "$0")/devlib.sh"
a exec-out screencap -p > "${1:?out.png}" && echo "$1"
