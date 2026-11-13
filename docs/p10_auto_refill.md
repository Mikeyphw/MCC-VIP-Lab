# P10 HF01 — In-app “♦ Set 9” and reliable P09-compatible restart

This change applies only to `feature/p10-auto-refill-diamonds-9`; it does not
modify `main`. P10 initially replaced the disk file while the Flutter engine
still kept zero in MobX memory. That was inadequate for the visible and
spendable balance.

## New behavior

- The target's Flutter Activity receives a small **♦ Set 9** button, added to
  its own decor view. No overlay permission; no button appears over AdMob's
  separate advertising activities.
- The module APK also has a **MCC VIP Lab** launchable control screen with a
  **Set diamonds to 9 and reopen app** button and an **automatic refill** switch.
- The button requests a fixed action through a ContentProvider hosted by
  `dev.mikey.mccviplab`. The provider authenticates the Binder caller UID as
  the exact target app package. It never accepts paths, quantities or shell
  commands supplied by callers.
- A root process running under **MCC VIP Lab's own package** performs the same
  sequence that succeeded in P09: force-stop MCC, discover exactly one app-
  private `*-DiamondBalance` file, validate one ASCII digit, set `9`, verify,
  then reopen MCC via its launch intent. This makes Flutter rebuild its in-
  memory state from the modified persisted value.
- Automatic mode watches the file for an exact zero and requests that **same
  stop/write/relaunch sequence** from the module process after a short quiet
  period. Other values are never automatically modified; a 12-second module-
  persisted cooldown prevents rapid restart loops.

## Root permission and scope

Grant superuser permission to **MCC VIP Lab** in KernelSU. The earlier P09 test
used Termux's root permission; that does not give the module its own root access.
The module's control screen is available by opening MCC VIP Lab from the app
launcher. The in-app button uses the same module bridge and root permission.
This feature modifies local app-private data only and has not demonstrated
server-side authorization. It may interrupt the current app screen, and the
app could later synchronize a different value. Avoid enabling the feature when
the app is updating other important information.

The local module uses only fixed file commands, never alters sync markers, and
never fakes reward callbacks. The UI does not run in an ad Activity.

## Validation and CI

`python3 scripts/verify_mcc_p10.py` verifies the targeted source contract.
`RootRefillCommandTest` checks the shell sequence, exact-one-file guard and
zero-only condition. Android compilation and JVM tests run on GitHub Actions;
no local Gradle needed. Physical device acceptance is outstanding.

```zsh
python3 scripts/install_p10_branch.py --serial localhost:5555
```

Expected log markers: `MCCREFILL: TARGET_UI_BUTTON_ADDED`,
`MCCREFILL: MANUAL_SET_9_RESTART_PASS`, and `MCCREFILL: AUTO_0_TO_9_RESTART_PASS`.
For errors, open MCC VIP Lab and inspect its last status or `adb logcat -d -s MCCREFILL:I`.
