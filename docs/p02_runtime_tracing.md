# MCC VIP / Ad Reward P02 — Scoped runtime evidence

## What changed

- Preserves the existing local RevenueCat `premium_noads` spoof and logs sanitized entitlement fields **before and after** the mapping. `MCCVIPSTATE: MAPPER_BEFORE` is the earliest observed state; `CUSTOMER_INFO_BEFORE` may already be modified by the mapper. These observations do **not** prove the separate Flutter AOT `premiumLevel`/`validUntil` authorization decision.
- Adds read-only RevenueCat platform-call names under `MCCVIPFLOW` (no arguments) and Android SharedPreferences observation for keys whose names contain diamond, gem, coin, premium, subscription, VIP, entitlement, expiry, or ad-block hints. Only numeric/boolean values are printed, never full key names, subscription identifiers, account IDs, tokens, or string values. This cannot inspect Dart-only MobX state or backend records.
- Deduplicates ad bridge observations within 350ms under `MCCADQA: BRIDGE_GROUP`, but this is only a **log group**, never a count of unique network requests. No callbacks are forged, no ad network traffic is faked, and no diamond balance is changed.
- Adds `scripts/mcc_lab_diagnose.py` to summarize on-device logs and a synthetic **offline** completion-after-failure test. This checks the detection harness, **not** the original app's reward enforcement.

## Test with ads still blocked

1. After a successful hosted build, download/install the exact latest APK with `python3 scripts/install_latest_successful.py`, then enable the updated module in Vector/LSPosed, enable it in Vector/LSPosed for the exact app package, and restart the app.
2. Observe `MCCVIPSTATE: MAPPER_BEFORE`/`AFTER`, `MCCLOCALQA`, `MCCADQA` and existing `MCCVIPLAB` messages. Try a VIP feature, then a diamond-reward action. Note the balance before and after manually.
3. Execute `python3 scripts/mcc_lab_diagnose.py --serial localhost:5555` from your **existing MCC-VIP-Lab repository** to summarize the Android logcat.
4. Record whether a VIP-only feature actually unlocks and whether the diamond balance changes. A toast or a `PATCH_*_PASS` line alone is not proof.

## Observability boundaries

`premiumLevel`, `validUntil`, `diamondBalance`, `incrementDiamondBalance`, and `_checkAdBlock` were identified in the Flutter AOT executable, not in the Java layer. LSPosed Java hooks cannot inherently observe their reads/writes. Backend GraphQL authorization and authoritative diamond balances remain unknown. To validate those boundaries, inspect an owned staging backend or an instrumented Flutter build with app source; do not infer from native bridge events alone.

## Security invariants to enforce server-side

- Only verified store subscriptions can grant VIP backend capabilities.
- Rewarded ad credit requires server-side validation (e.g., AdMob SSV where supported), an idempotent transaction identifier and a server-maintained ledger.
- `onAdFailedToLoad`, `rewarded_closed`, a manipulated client entitlement, and a synthetic `rewarded_complete` event must never be accepted as evidence of a real paid ad or purchase.
- Provide any promised fallback feature without turning an unavailable ad into a fictitious watched impression.
