# MCC VIP Lab P03 — bounded local diagnostics

Base: `Mikeyphw/MCC-VIP-Lab` main at `09e75967f243cd776726297746c3f1b6e6021434`, exact app build `20.2609.3009`.

## Evidence from supplied log

- `22:08:27` RevenueCat `restorePurchases` was called; the mapper later showed the **synthetic** `premium_noads` entitlement active.
- No verified VIP feature-access event was captured. `MCCVIPSTATE` is not a gate-decision tracer; those messages report SDK maps.
- No diamond balance reads/writes or awarded-reward events were captured in the submitted log.
- Local `premium`-category timestamps and `expiry`-category values are coarse SharedPreferences *key categories*, not verified entitlement fields.

## New instrumentation

- `AdInstanceTrace` hooks the Android `AdInstanceManager` methods identified in the supplied APK DEX. Each local ad id is classified as `banner`, `rewarded`, `interstitial`, or `other`. It observes `TRACK`, `LOAD_OK`, `LOAD_FAILED`, `IMPRESSION`, `REWARD_CALLBACK`, and fullscreen show/failure/dismiss events. It emits `REWARD_CONTEXT_*` after a reward callback, showing only what previous signals were observed for that ad ID; missing signals are not proof of fraud. **Ad events are never manufactured or rewritten.** A callback does not establish a valid AdMob SSV transaction.
- `DiamondProbe` observes integer/long and getAll-based Android SharedPreferences reads, plus numeric write attempts, for diamond-like keys. Its optional modes (`observe`, `freeze`, `high`) apply **only** to exactly allowed numeric local preference keys, only in the app process, and **only if that Java read path is actually used**. It does not write account balances or synchronize to any backend. Flutter's Dart AOT `diamondBalance`, SQLite, and server state are not directly changed. A mode setting alone is **not** evidence of an applied override.
- `NativeBannerProbe` recognizes the APK's `FlutterBannerAd` and `FlutterAdManagerBannerAd` Java objects. When explicitly enabled, it hides a returned native view with `View.GONE`. It may **not** remove a Flutter-rendered layout gap, an uncreated banner slot, or a Facebook/other banner. It is intended only for blocked-ads/test-ad scenarios, not to misrepresent ad impressions.
- `mcc_p03_device.py popup` optionally locates the *existing* Flutter accessibility label `Continuar com bloqueio` and taps it on the user's device, while checking the target is foreground. It does not patch `_checkAdBlock` or permanently remove the Dart popup; repeated dialogs may recur. It may fail if Flutter's accessibility tree does not expose the button.
- `qa_reward_callback_fixture.py` exercises a synthetic blocked-ad completion and duplicate callback offline. It demonstrates that a deliberately naive client-callback policy can credit 3 or 6 diamonds while a server-verification fixture grants 0 until a unique valid transaction is supplied. **This is not an experiment on the actual MCC balance.**
- `mcc_p03_report.py` provides per-ad-id and client-local-observation summaries, without reading account IDs, raw ad payloads, or storing sensitive device logs.

## Usage after CI build and reinstall

```zsh
cd ~/Code/MCC_VIP_LAB
git pull --ff-only
python3 scripts/install_latest_successful.py

# ADB controls (ad blocker remains enabled)
python3 scripts/mcc_p03_device.py status
python3 scripts/mcc_p03_device.py diamond observe
python3 scripts/mcc_p03_device.py diamond freeze
python3 scripts/mcc_p03_device.py diamond high --value 999999
python3 scripts/mcc_p03_device.py banner hide
python3 scripts/mcc_p03_device.py banner observe
python3 scripts/mcc_p03_device.py popup --watch 90
python3 scripts/mcc_p03_device.py screen

# Mark the precise moment just before tapping a VIP feature
python3 scripts/mcc_p03_device.py mark vip
python3 scripts/mcc_p03_report.py --serial localhost:5555
python3 scripts/qa_reward_callback_fixture.py  # entirely offline, no device interaction

# Return to non-mutating modes
python3 scripts/mcc_p03_device.py diamond observe
python3 scripts/mcc_p03_device.py banner observe
```

Only enable diamond `freeze/high` in an isolated nonpaying test account/staging, with network-blocked or dry-run environments where possible. These modes can distort display but cannot legitimately grant VIP or server-authorized diamonds. Do not use them to claim an advertisement was watched. Do not un-block advertisements merely to test banner hiding.

### Interpreting runtime evidence

`MCCDIAMOND: PREF_NUMERIC_PROBE_INSTALLED` means only that a Java hook installed. An actual `MCCDIAMOND: READ ... changed=true` or `READ_ALL ... changed=true` is needed to show that any local override happened. No event means no evidence of a Java preference path.

`MCCBANNER: BANNER_PROBE_INSTALLED` means a method hook was registered. Only `NATIVE_VIEW_HIDDEN_LOCAL_ONLY` shows an actual native banner view was hidden. Flutter layout and DNS popup remain independent.

`MCCADID: LOAD_FAILED type=banner` is not a rewarded ad. `REWARD_CALLBACK` is only an app-side callback, not verified network proof or a diamond credit.

There is no fully reliable LSPosed Java-only mechanism to modify the compiled Dart `diamondBalance`, `_checkAdBlock` dialog, or Flutter `SizedBox` structure. Real fixes to those components require source-level Flutter changes or specialized Dart AOT instrumentation. This P03 does not claim otherwise.

## Ad-reward callback security question

The compiled SDK exposes `onRewardedAdUserEarnedReward` and `onRewardedInterstitialAdUserEarnedReward`. These callbacks can be simulated in an **offline fixture**, proving that callback-only award logic is inherently spoofable. This **does not prove** the current binary trusts them for diamonds; a device trace of the callback and a balance mutation, or the app’s source/backend ledger, is required. `WRITE_ATTEMPT` is not proof of a persisted transaction and `REWARD_CONTEXT_FAILED_SEEN` is only a heuristic. No fake SDK reward is injected into the installed app, and no real ad impression is reported.
