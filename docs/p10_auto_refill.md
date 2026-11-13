# P10 — Auto-refill local `DiamondBalance` at zero (feature branch)

This branch is distinct from `main`. On the scoped app process, `AutoRefillWatcher`
monitors the app-private `databases` directory for the *single* account-scoped
`*-DiamondBalance` file. When it contains **exactly one ASCII byte `0`**, it
replaces the file with a one-byte `9` via an atomic same-directory rename.
Any other value, unexpected file format, symlink or multiple matching files is
left untouched. An event observer and a 1.2-second fallback poll cover native
Dart writes that Java `SharedPreferences` hooks never see.

## Boundaries

- No fake rewarded-ad events, no backend API calls, no purchases, and no SDK
  entitlement changes are introduced by this feature.
- This **does modify the app's local private balance file** and may interact
  with its existing Azure/Hasura synchronization. It has **not** been tested
  against server state, and server acceptance is not claimed.
- The module runs in the **target app process**. It does not run while that
  process is stopped, and Flutter's in-memory MobX balance is **not** patched.
  The UI may continue showing zero until the app re-reads the file.
- It defaults to on for this feature branch. To disable on a rooted test device:
  `su -c 'settings put global mcc_lab_refill_9_enabled 0'` then restart the app.
  Re-enable with `1` or `settings delete global mcc_lab_refill_9_enabled`.
- This build is isolated to `feature/p10-auto-refill-diamonds-9`; do not merge
  into main unless intentionally changing the default behavior.

## CI

Push to this feature branch triggers the existing GitHub Actions Android build.
JVM tests validate file selection, exact-zero behavior, and refusal cases.
There is no Termux Gradle build or automatic device test.

## Expected log markers

`MCCREFILL: INSTALLED`, `MCCREFILL: WATCHER_STARTED`, and
`MCCREFILL: ZERO_TO_NINE_PERSISTED`. Neither installer logs nor file writes
prove that the Flutter view has refreshed.

Once GitHub CI succeeds and you choose to install the feature build:

```zsh
python3 scripts/install_p10_branch.py --serial localhost:5555
```

This installer checks the feature branch and the exact local commit SHA; the
existing main-branch installer intentionally refuses feature branches.
