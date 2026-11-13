#!/usr/bin/env python3
"""P11 cosmetic-only contract: avoid ads/reward/entitlement side effects."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
java = root/'app/src/main/java/dev/mikey/mccviplab'
read = lambda n: (java/n).read_text(encoding='utf-8')
entry = read('HookEntry.java')
assert entry.count('NativeBannerProbe.install(loader, appContext);') == 1
assert entry.count('PremiumUpsellOverlay.install();') == 1
native = read('NativeBannerProbe.java')
for required in ('FlutterBannerAd', 'FlutterAdManagerBannerAd', 'com.facebook.ads.AdView',
                 'com.google.android.gms.ads.AdView', 'getPlatformView', 'View.GONE',
                 'mcc_lab_banner_mode', 'banner_only=true'):
    assert required in native, required
for prohibited in ('RewardedAd', 'InterstitialAd', 'setResult(', '.invokeMethod(',
                   'onUserEarnedRewardCallback(', 'destroy(', 'setReward('):
    assert prohibited not in native, prohibited
upsell = read('PremiumUpsellOverlay.java')
for required in ('AccessibilityNodeProvider.HOST_VIEW_ID', 'CosmeticPolicy.isUpsell',
                 'mcc_lab_upsell_mode', 'VISUAL_PATCH_COUNT', 'no_flutter_layout_reflow=true',
                 'only_exact_promo_text=true', 'Math.min(node.getChildCount(), 24)'):
    assert required in upsell, required
for prohibited in ('PremiumView_build', 'PlatformViewsController', 'setResult(',
                   'getCustomerInfo(', 'purchasePackage(', 'purchaseProduct(', 'onAdEvent('):
    assert prohibited not in upsell, prohibited
policy = read('CosmeticPolicy.java')
for required in ('assine premium', 'quero assinar premium',
                 'remova propagandas e tenha diamantes infinitos', 'diamantes infinitos e sem anuncios por'):
    assert required in policy, required
controls = read('CosmeticControls.java')
assert 'mcc_lab_banner_mode' in controls and 'mcc_lab_upsell_mode' in controls
assert 'ProcessBuilder("su", "-c", cmd)' in controls
assert 'REFUSED_UNKNOWN_MODE' in controls
activity = read('RefillControlActivity.java')
assert 'addCosmeticSwitch(panel,' in activity
assert 'Hide banner ads' in activity and 'Hide Premium promotions' in activity
assert (root/'app/src/test/java/dev/mikey/mccviplab/CosmeticPolicyTest.java').is_file()
workflow = (root/'.github/workflows/android.yml').read_text()
assert 'feature/p11-banner-upsell-cleanup' in workflow
assert 'python3 scripts/verify_mcc_p11.py' in workflow
assert 'PremiumUpsellOverlay.install();' not in read('RootRefillCommand.java')
print('MCC_P11_BANNER_UPSELL_COSMETIC_SCOPE_CONTRACT_PASS')
