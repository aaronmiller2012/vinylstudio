#!/usr/bin/env bash
# meminfo.sh  -> one line: pss_kb rss_kb java_heap_kb native_heap_kb graphics_kb
. "$(dirname "$0")/devlib.sh"
sh_ dumpsys meminfo "$VINYL_PKG" | tr -d '\r' | awk '/TOTAL PSS:/{pss=$3; rss=$6} /Java Heap:/{j=$3} /Native Heap:/{n=$3} /Graphics:/{g=$2} END{print pss,rss,j,n,g}'
