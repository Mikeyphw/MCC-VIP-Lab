# MCC Ad Reward & Ad-Blocking QA — P01 (read only)

## Findings on uploaded build 20.2609.3009

The compiled Flutter `libapp.so` includes `_checkAdBlock@198032045`, `_showAdIfNeeded@1468391437`, `_showAdMobRewarded@1468391437`, `_loadFacebookRewardedAd@1468391437`, `onRewardedAdUserEarnedReward`, `rewarded_complete`, `rewarded_closed`, and `incrementDiamondBalance`. These establish names of relevant functions/events but *not* execution order or reward validation logic. The DEX includes Google Mobile Ads SSV-related APIs; this is **not** evidence that ad reward verification is configured or enforced. No live ad callbacks were recorded.

## Scope fix

The legacy Xposed manifest lacked `xposedscope`; a standalone `assets/xposed_scope` does **not** provide the recommended-scope declaration for a legacy LSPosed module. Added manifest `xposedscope=br.art.code.meucronogramacapilar`; the package check in `HookEntry` is retained.

## Runtime diagnostic

`MCCADQA: FLUTTER_BRIDGE_PROBE_INSTALLED_READ_ONLY` confirms a Java `MethodChannel.invokeMethod` hook was registered. `MCCADQA: BRIDGE_EVENT ...` logs filtered ad-related *method names only*, never SDK payload, placement ID, user info, balance or account credentials. A max of 60 lines is enforced per process. Missing logs are inconclusive; Flutter/Dart may handle ad state without crossing this bridge. No reward status is fabricated or forced, and no ad impression is reported to networks.

## Safe four-condition test matrix (owned test account / staging)

1. **Control:** no ad blocker, real test rewarded ad. Log load, impression, user-earned-reward callback, and diamond balance before/after. No real paid impression should be intentionally generated for this audit; use provider test ad units.
2. **Blocked DNS:** make ad DNS unavailable and invoke a rewarded action. Expect ad load failure, no completion callback, no +3 credit. If the product promises free fallback, test that the feature *works without falsely crediting diamonds*.
3. **Offline:** disable connectivity before invoking a rewarded action. Expect no impression; no earned-reward record. Check separate fallback and non-spinning UI.
4. **Failure after load:** force the test ad to fail to show. Verify `onAdFailedToShowFullScreenContent`, not earned reward; repeat taps should not grant multiple credits.

For robustness, use an isolated Dart ad-controller fake in a **staging build** to deliver a deliberately synthetic completion event without any ad show or SDK network traffic, then assert that server-authoritative reward credit is **not** granted. Do not inject a production SDK completion event or fabricate ad-network events. For AdMob, verify server-side verification callbacks with idempotent transaction IDs and an append-only authoritative diamond ledger. Do not credit on `rewarded_closed`, `onAdLoaded`, or `onAdShowedFullScreenContent`.

## Interpreting logs

- Toast from VIP module: RevenueCat map was rewritten locally; **not** proof of VIP unlocking.
- `MCCADQA: BRIDGE_EVENT onAdFailedToLoad`: native bridge observed a failure method; *not* proof no Dart-side credit happened.
- `MCCADQA: BRIDGE_EVENT onAdEvent`: inspect targeted Dart instrumentation for payload/status; this intentionally logs no sensitive payloads.
- No ad traces: no conclusion about Dart or internal SDK callbacks. Add source-level event tracing or controlled tests.
