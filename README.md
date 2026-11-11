# MCC VIP Lab — scoped LSPosed client-entitlement spoof

**Authorized local test only.** Targets `br.art.code.meucronogramacapilar`, **exact installed version `20.2609.3009`**, and RevenueCat's Flutter `premium_noads` entitlement. This is a QA probe for client-side trust, not a genuine purchase. It does not deliberately modify Google Play Billing, send receipts, or directly write Nhost/GraphQL, but the target application may still react to spoofed client state with its own backend calls. Use a separate test account / staging backend.

## How it works

1. After application attachment, verifies package/version and installs two LSPosed hooks.
2. Hooks the verified Android SDK method `CustomerInfoMapperKt.map(CustomerInfo) -> Map` and locally adds `premium_noads` to `entitlements.active` and `.all`, plus active subscription lists/dates. It copies maps instead of modifying the original SDK object.
3. Wraps `PurchasesFlutterPlugin.getCustomerInfo(MethodChannel.Result)` to ensure the asynchronous Flutter-facing response carries the same test entitlement.
4. Displays a one-time Android toast after successful spoofing and logs `MCCVIPLAB: HOOK_*` and `MCCVIPLAB: PATCH_*` in LSPosed logs.

No native Flutter AOT (`libapp.so`) patch is attempted. The hooks have concrete method signatures verified from this exact APK's DEX, but **there is no runtime device validation**. If your app derives `premiumLevel` from local cache or Nhost independently of RevenueCat, you may see `PATCH_*_PASS` without the VIP UI unlocking. That would be an actionable finding, not a qualified bypass.

## Build via GitHub Actions (recommended)

Publish this directory as a **private** repo. It contains `.github/workflows/android.yml`. A push to `main` triggers unit tests and an `app-debug.apk` artifact. It uses hosted Gradle; no local Gradle needed. The build checks `xposed_init`/scope inside the APK.

```zsh
git init -b main
git add .
git commit -m 'test: add app-scoped MCC VIP LSPosed lab'
gh repo create MCC-VIP-Lab --private --source=. --remote=origin --push
gh run watch -R Mikeyphw/MCC-VIP-Lab --exit-status
mkdir -p ./artifacts
gh run download -R Mikeyphw/MCC-VIP-Lab -n mcc-vip-lab-debug -D ./artifacts
find ./artifacts -name '*.apk' -type f -print
```

## Install / test

```zsh
adb -s localhost:5555 install -r ./artifacts/app-debug.apk
```

Enable **MCC VIP Lab** in LSPosed/Vector and scope it **only** to **Meu Cronograma Capilar**, not System Framework. Force-stop and restart the target app:

```zsh
adb -s localhost:5555 shell am force-stop br.art.code.meucronogramacapilar
adb -s localhost:5555 shell monkey -p br.art.code.meucronogramacapilar 1
adb -s localhost:5555 logcat -d -v brief | grep -E 'MCCVIPLAB|Xposed|LSPosed' | tail -100
```

The one-time toast indicates local CustomerInfo patching, **not** proof that premium features unlocked. The acceptance test is whether the target's VIP-only feature can be opened using a *non-paying test account*. Keep cloud permissions and entitlements unchanged.

## Interpreting results

- `TARGET_PROCESS` but no `HOOK_*_INSTALLED`: inspect Xposed API/runtime and class loading.
- `HOOK_MAPPER_INSTALLED` + `PATCH_MAPPER_PASS` or `PATCH_CUSTOMER_INFO_PASS`: local RevenueCat result spoof actually fired.
- Spoof fired but VIP UI remains locked: another check (Dart store/Nhost/cache) is authoritative, so this is **not** an unlock.
- VIP UI unlocks while server refuses premium action: local-only entitlement bypass, backend control works.
- VIP server capability succeeds on a test account without verified purchase: **security finding**, fix server-side authorization.

## Scope and reversibility

Disable module in LSPosed, force-stop target app, and optionally uninstall `dev.mikey.mccviplab`. Nothing in the original target APK is modified. Verify server test account afterward because the target app may synchronize client state itself.

## Source verification

The complete split set used to derive the hook signatures includes `PurchasesFlutterPlugin.getCustomerInfo(MethodChannel$Result)` and `CustomerInfoMapperKt.map(CustomerInfo) -> java.util.Map`. See the accompanying `source_evidence.json` for hashes. No runtime hook success is claimed.
