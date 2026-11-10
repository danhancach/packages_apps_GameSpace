#!/usr/bin/env bash
# Host check: compile + run pure Kotlin sources (no Android build/SDK).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KOTLINC="${KOTLINC:-/home/tuan/android/external/kotlinc/bin/kotlinc}"
OUT="${TMPDIR:-/tmp}/gamespace-host-phase-check"
rm -rf "$OUT"
mkdir -p "$OUT"

# Strip package Android-only files; only pure sources.
SOURCES=(
  "$ROOT/app/src/main/java/io/chaldeaprjkt/gamespace/ui/motion/MiuixTitleMotion.kt"
  "$ROOT/tools/HostMiuixTitleMotionCheck.kt"
  "$ROOT/app/src/main/java/io/chaldeaprjkt/gamespace/ui/overscroll/StretchMath.kt"
  "$ROOT/app/src/main/java/io/chaldeaprjkt/gamespace/ui/overscroll/MiuixScrollPhase.kt"
  "$ROOT/app/src/main/java/io/chaldeaprjkt/gamespace/ui/nav/NavIndicatorGeometry.kt"
  "$ROOT/app/src/main/java/io/chaldeaprjkt/gamespace/ui/gesture/PagerAxisOwnership.kt"
  "$ROOT/app/src/main/java/io/chaldeaprjkt/gamespace/preferences/LibraryListOrder.kt"
  "$ROOT/app/src/main/java/io/chaldeaprjkt/gamespace/ui/popup/LibraryPopupGeometry.kt"
  "$ROOT/app/src/main/java/io/chaldeaprjkt/gamespace/preferences/appselector/AppSelectorFilter.kt"
  "$ROOT/app/src/main/java/io/chaldeaprjkt/gamespace/ui/motion/MiuixPageMotion.kt"
  "$ROOT/app/src/main/java/io/chaldeaprjkt/gamespace/ui/motion/AddGamePredictiveBack.kt"
  "$ROOT/tools/HostPhaseCheck.kt"
  "$ROOT/tools/HostGestureOrderCheck.kt"
  "$ROOT/tools/HostPopupGeometryCheck.kt"
  "$ROOT/tools/HostAppSelectorFilterCheck.kt"
  "$ROOT/tools/HostMiuixPageMotionCheck.kt"
  "$ROOT/tools/HostAddGamePredictiveBackCheck.kt"
)

KOTLIN_LIB="$(cd "$(dirname "$KOTLINC")/../lib" && pwd)"
STDLIB="$KOTLIN_LIB/kotlin-stdlib.jar"
"$KOTLINC" -cp "$STDLIB" -d "$OUT/classes" "${SOURCES[@]}"
java -cp "$OUT/classes:$STDLIB" io.chaldeaprjkt.gamespace.tools.HostPhaseCheck
java -cp "$OUT/classes:$STDLIB" io.chaldeaprjkt.gamespace.tools.HostMiuixTitleMotionCheck
java -cp "$OUT/classes:$STDLIB" io.chaldeaprjkt.gamespace.tools.HostGestureOrderCheck
java -cp "$OUT/classes:$STDLIB" io.chaldeaprjkt.gamespace.tools.HostPopupGeometryCheck
java -cp "$OUT/classes:$STDLIB" io.chaldeaprjkt.gamespace.tools.HostAppSelectorFilterCheck
java -cp "$OUT/classes:$STDLIB" io.chaldeaprjkt.gamespace.tools.HostMiuixPageMotionCheck
java -cp "$OUT/classes:$STDLIB" io.chaldeaprjkt.gamespace.tools.HostAddGamePredictiveBackCheck
