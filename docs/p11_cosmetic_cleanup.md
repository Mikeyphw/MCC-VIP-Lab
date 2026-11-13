# P11 — Cosmetic banner and premium-upsell cleanup

Feature branch: `feature/p11-banner-upsell-cleanup`. The original `main` remains unchanged until device QA.

## What was grounded in the target APK

- Target app: `br.art.code.meucronogramacapilar`. Last tested package record: `20.2609.3009`; newer builds remain best-effort.
- The app contains Dart AOT widgets named `AdContainer`, `AdScaffoldContainer` and `PremiumView`. Its bundled localization has the precise promotional labels "Assine Premium", "Quero assinar Premium", "Remova propagandas e tenha diamantes infinitos", "Diamantes infinitos e sem anúncios por ...", etc.
- Native Android advertising includes Google Mobile Ads `AdView`, `AdManagerAdView` and Facebook Audience Network `AdView` (banner-only types). This feature avoids rewarded and interstitial ad classes, callbacks and SDK purchase handlers entirely.

## Implementation

- **Hide ad banners**: native banner `View.GONE` on relevant Google/Facebook classes; Flutter Google banner-platform view wrappers still hooked. No ad callback fabrication. It may leave Dart-reserved white space; hiding a native view does not reflow Flutter widgets.
- **Hide Premium promotions**: scans Flutter semantics nodes on the active Flutter Activity and masks **only exact, short promotion labels**. It never masks generic `Premium`, subscription status, `Gerenciar assinaturas`, diamond count or rewarded-ad CTAs. A cosmetics mask is not a structural widget deletion, so background mismatch or empty gaps are possible. If Flutter semantics is not available, it does nothing and logs the absence without collecting private text.
- **Controls**: two independent switches on the installed MCC VIP Lab control screen. They default to enabled in the experimental feature build. Changing a switch requires the module's already-granted KernelSU root permission to update a fixed `Settings.Global` key; reopen the target app after changing either switch.
- For direct device recovery (no module UI):

  ```sh
  su -c 'settings put global mcc_lab_banner_mode show'
  su -c 'settings put global mcc_lab_upsell_mode show'
  ```

  `show` disables a hide feature; `hide` enables it.

## Validation and limitations

- Source-contract checks, exact-label positive/negative JUnit tests, and Python offline regressions are bundled in hosted CI.
- Does **not** grant real Premium, forge purchases, change the underlying Flutter entitlement state, trigger rewarded-ad callbacks, or change the diamond refill implementation.
- The exact premium screens and banner placements have **not been verified on your physical device**. Screenshots are necessary to confirm no visible blanks or accidental overlap. An app update can change Dart UI semantics or ad classes.
- No production server authorization or subscription benefit is implied by cosmetic hiding.
