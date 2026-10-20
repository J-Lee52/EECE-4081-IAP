#!/usr/bin/env bash
# Fault survey for M6: applies each candidate one-line fault to the app code on
# its own, runs the JVM test suite, prints which tests failed, and restores the
# file with `git checkout`. Results are written to evidence/m6/fault-survey.txt.
#
# Run from the repository root with a clean app/src/main (no uncommitted
# changes there), for example in Git Bash on Windows:
#   JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" bash evidence/m6/fault-survey.sh
# Needs bash, git and python on the PATH.

set -u
cd "$(git rev-parse --show-toplevel)"

if [ -n "$(git status --porcelain app/src/main)" ]; then
  echo "app/src/main has uncommitted changes; commit or stash them first." >&2
  exit 1
fi

GRADLE=./gradlew
[ -f gradlew.bat ] && [ "${OS:-}" = "Windows_NT" ] && GRADLE=./gradlew.bat
SRC=app/src/main/java/com/example/eece4081_iap
RESULTS=app/build/test-results/testDebugUnitTest
OUT=evidence/m6/fault-survey.txt
export PYTHONIOENCODING=utf-8

# Replaces exactly one occurrence of OLD with NEW in FILE.
apply_fault() {
  python - "$1" "$2" "$3" <<'PY'
import sys
path, old, new = sys.argv[1:]
text = open(path, encoding="utf-8").read()
if text.count(old) != 1:
    sys.exit(f"expected exactly one match in {path}, found {text.count(old)}")
open(path, "w", encoding="utf-8", newline="").write(text.replace(old, new))
PY
}

# Prints "CAUGHT by N: Class.test, ..." or "SURVIVED" from the JUnit XML results.
failed_tests() {
  python - "$RESULTS" <<'PY'
import glob, sys, xml.etree.ElementTree as ET
files = glob.glob(sys.argv[1] + "/*.xml")
if not files:
    print("NO RESULTS (the build failed; check that the fault compiles)")
    sys.exit()
failed = []
for path in files:
    for case in ET.parse(path).getroot().iter("testcase"):
        if case.find("failure") is not None or case.find("error") is not None:
            failed.append(case.get("classname").split(".")[-1] + "." + case.get("name"))
print("SURVIVED (suite green)" if not failed
      else f"CAUGHT by {len(failed)}: " + ", ".join(sorted(failed)))
PY
}

survey() { # name file old new
  local name="$1" file="$SRC/$2"
  if ! apply_fault "$file" "$3" "$4"; then
    echo "$name: FAULT NOT APPLIED" | tee -a "$OUT"
    return
  fi
  rm -rf "$RESULTS" # so a build failure cannot reuse the previous fault's results
  "$GRADLE" testDebugUnitTest --console=plain > /dev/null 2>&1
  echo "$name: $(failed_tests)" | tee -a "$OUT"
  git checkout -- "$file"
}

{
  echo "# Fault survey: candidate one-line faults, each applied alone, suite run, file restored with git checkout"
  echo "# $(date -Iseconds)  branch $(git branch --show-current)  commit $(git rev-parse --short HEAD)"
  echo "# Script: evidence/m6/fault-survey.sh"
  echo
} > "$OUT"

survey A_avg_div_by_shots   domain/SessionStatistics.kt   '} / splits.size' '} / shots.size'
survey B_cooldown_boundary  audio/ShotDetector.kt         'at - previous < cooldown' 'at - previous <= cooldown'
survey C_threshold_boundary audio/ShotDetector.kt         'peakAmplitude(samples) < threshold' 'peakAmplitude(samples) <= threshold'
survey D_origin_beep_end    engine/TimerEngine.kt         'if (state != TimerState.WaitingForStart) return
        setState(TimerState.Recording)' 'if (state != TimerState.WaitingForStart) return
        beepStartedAt = clock.elapsed()
        setState(TimerState.Recording)'
survey E_split_from_first   domain/ShootingSession.kt     'splitTime = previous?.let { timestamp - it.timestamp }' 'splitTime = previous?.let { timestamp - shots.first().timestamp }'
survey F_round_not_truncate ui/DurationFormat.kt          'val hundredths = inWholeMilliseconds / 10' 'val hundredths = (inWholeMilliseconds + 5) / 10'
survey G_drop_single_shot   ui/TimerViewModel.kt          'session.shots.isEmpty()) return' 'session.shots.size <= 1) return'
survey H_history_oldest     data/FileSessionRepository.kt 'compareByDescending<ShootingSession> { it.startTime }' 'compareBy<ShootingSession> { it.startTime }'
survey I_peak_ignores_neg   audio/ShotDetector.kt         'val magnitude = abs(sample.toInt())' 'val magnitude = sample.toInt()'
survey J_total_is_first     domain/SessionStatistics.kt   'totalTime = shots.maxOf { it.timestamp }' 'totalTime = shots.first().timestamp'
survey K_fastest_is_max     domain/SessionStatistics.kt   'fastestSplit = splits.minOrNull()' 'fastestSplit = splits.maxOrNull()'
survey L_late_audio_kept    engine/TimerEngine.kt         'if (state != TimerState.Recording) return
        val current' 'if (state == TimerState.Idle) return
        val current'

git status --short app/src/main
