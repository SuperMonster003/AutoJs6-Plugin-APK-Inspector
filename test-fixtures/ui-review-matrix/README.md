# UI screenshot review matrix

This matrix exercises the APK Inspector report and manifest viewer across every combination of:

- three screen profiles: 411 dp portrait, 320 dp portrait, and 320 dp-high landscape;
- 1.0x, 1.5x, and 2.0x font scales;
- light and dark system themes;
- English LTR and Arabic RTL layouts.

The Cartesian product contains 36 cases. Its canonical, JVM-tested definition is
`app/src/test/resources/ui-review-matrix/matrix.json`. The fixture is the privacy-neutral APKS
golden sample already used by the bundletool selection tests, so the run covers the report header,
long identifiers, manifest action, all device-simulation selectors and actions, the report tail,
and manifest search controls.

## Run the matrix

Build the debug APK, start an unlocked disposable Android 13 (API 33) or newer emulator, and run
from the repository root:

```text
./gradlew.bat :app:assembleDebug
python .python/run_ui_review_matrix.py --serial emulator-5554
```

Use an emulator that is not shared with another UI automation process. Android exposes a single
UI Automation accessibility service per device, so concurrent hierarchy dumps or another task
launching activities would invalidate both the foreground-state and accessibility evidence.

The runner installs the debug APK unless `--skip-install` is supplied. Use `--list` to print all
case IDs, `--case <exact-id>` to select one or more cases, or `--smoke` for four boundary cases.
The full Cartesian product is the default and is required before completing a UI milestone.

Generated artifacts are written below `captures/ui-review-matrix/<UTC timestamp>/`, which is
ignored by Git. `index.html` is the visual contact sheet, `results.json` is the machine-readable
summary, and every checkpoint has both a PNG and its UI Automator XML hierarchy. Each case also
contains `accessibility-order.txt`, the observed linear semantic traversal.

The automated audit fails a case when a required control cannot be reached by linear scrolling, a
clickable app control has no accessible label, an observed touch target never reaches 48 dp, the
screen/font/theme/locale or actual display rotation differs from the requested case, the foreground
hierarchy belongs to another package, report headings appear out of order, an RTL/LTR toolbar
action is on the wrong side, manifest search yields no navigable match, or visible report text
contains an ellipsis. Screenshots remain the oracle for visual wrapping, color contrast, and icon
mirroring.

## Manual review checklist

Open `index.html` and review every row, not only the boundary cases:

1. Confirm toolbar titles, labels, buttons, identifiers, and search status text wrap without being
   clipped or replaced by an ellipsis.
2. Confirm every report and manifest control is visible at its checkpoint and can be reached by
   linear scrolling; no control may sit behind a system bar or the keyboard.
3. Compare paired light/dark screenshots for readable contrast and paired LTR/RTL screenshots for
   mirrored navigation and action placement. Manifest XML itself intentionally remains LTR.
4. With TalkBack enabled, swipe forward from the report toolbar through the five headings and to
   the final findings text, then repeat through the manifest search controls. The spoken order must
   match `accessibility-order.txt`, with no unnamed actionable item or focus trap.
5. Record the artifact directory and reviewer/date in the release evidence. Do not commit generated
   screenshots, UI dumps, or device-specific output.

The runner snapshots display size/density, font scale, user-rotation mode, fixed-to-user-rotation
policy, actual display rotation, app locale, and night mode before the first case. A `finally`
cleanup restores those values and deletes only its uniquely named MediaStore fixture and temporary
UI dump, even when a case fails. Installation of the debug APK is intentionally not reverted.
