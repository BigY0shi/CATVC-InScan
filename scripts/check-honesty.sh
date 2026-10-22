#!/usr/bin/env bash
# Fails if production code contains patterns that fake data, hide errors, or show developer jargon.
# Owner rules: nothing fails silently, no fabricated state, no dev jargon on screen.
set -u
SRC="app/src/main"
fail=0
check() {
  local pattern="$1" why="$2"
  local hits
  hits=$(grep -rnE --include='*.kt' --include='*.xml' -- "$pattern" "$SRC" || true)
  if [ -n "$hits" ]; then
    echo "FAIL: $why"
    echo "$hits"
    echo
    fail=1
  fi
}
check 'printStackTrace\(\)'            'errors must be shown or logged, not printed and dropped'
check '\?: 0\.0'                        'a value that fails to parse must be refused, not turned into zero'
check 'ifBlank \{ "N/A" \}'             'blank fields must block a document, not print N/A'
check 'localizedMessage'                'raw exception text must not reach the screen'
check 'example\.com'                    'no placeholder email addresses'
check 'SimulateChip|createMockBitmap'   'no simulated scans or mock photos in production'
check 'unsplash'                        'no stock photos standing in for real documents'
check 'Moab|MOAB|Utah'                  'another dealer'"'"'s name or state on our paperwork'
check 'Office Jet|Canon Pro|spooled'    'no fictional printers'
check 'GEMINI_API_KEY'                  'no API key compiled into the app'
if [ "$fail" -eq 0 ]; then echo "check-honesty: clean"; fi
exit "$fail"
