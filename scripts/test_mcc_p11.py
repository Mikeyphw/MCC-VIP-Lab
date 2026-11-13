"""Offline P11 negative tests. No Android emulator or SDK calls required."""
from pathlib import Path
import shutil, subprocess, tempfile, textwrap, unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT/'app/src/main/java/dev/mikey/mccviplab'

class P11Tests(unittest.TestCase):
    def test_upsell_hiding_does_not_modify_entitlements(self):
        code = (JAVA/'PremiumUpsellOverlay.java').read_text()
        for bad in ('VipPayload.', 'premium_noads', 'setResult(', 'purchasePackage(', 'RevenueCat', 'onAdEvent('):
            self.assertNotIn(bad, code)
        self.assertIn('AccessibilityNodeProvider.HOST_VIEW_ID', code)
        self.assertIn('matches.size() < 8', code)
        self.assertIn('visited <= 160', code)

    def test_banner_hiding_does_not_affect_rewarded_ads(self):
        code = (JAVA/'NativeBannerProbe.java').read_text()
        self.assertIn('FlutterBannerAd', code)
        self.assertIn('com.facebook.ads.AdView', code)
        self.assertNotIn('RewardedAd', code)
        self.assertNotIn('InterstitialAd', code)
        self.assertNotIn('onUserEarnedReward', code)

    def test_control_modes_are_independent(self):
        control = (JAVA/'CosmeticControls.java').read_text()
        self.assertIn('mcc_lab_banner_mode', control)
        self.assertIn('mcc_lab_upsell_mode', control)
        self.assertIn('REFUSED_UNKNOWN_MODE', control)
        self.assertIn('getString(ctx.getContentResolver()', control)
        self.assertIn('MODE_SAVED_REOPEN_TARGET_TO_APPLY', control)

    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'javac not installed')
    def test_exact_label_java_policy(self):
        harness = textwrap.dedent('''
          package dev.mikey.mccviplab;
          class P11Harness {
            public static void main(String[] argv) {
              String[] yes={"Assine Premium", "Quero assinar Premium", 
                "Remova propagandas e tenha diamantes infinitos", 
                "Diamantes infinitos e sem anúncios por R$ 9,90", "Suscríbete a Premium"};
              String[] no={"Premium", "Assinante Premium", "Gerenciar assinaturas",
                "Veja um anúncio para ganhar 3 diamantes", "Recompensas", "diamantes"};
              for(String s:yes) if(!CosmeticPolicy.isUpsell(s)) throw new AssertionError("missing "+s);
              for(String s:no) if(CosmeticPolicy.isUpsell(s)) throw new AssertionError("overmatch "+s);
              System.out.println("MCC_P11_PURE_JAVA_EXACT_LABELS_PASS");
            }
          }
        ''')
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp)/'P11Harness.java';source.write_text(harness)
            run = subprocess.run(['javac','-encoding','UTF-8','-d',tmp,
                                  str(JAVA/'CosmeticPolicy.java'),str(source)],
                                 capture_output=True,text=True)
            self.assertEqual(run.returncode,0,run.stderr)
            run = subprocess.run(['java','-cp',tmp,'dev.mikey.mccviplab.P11Harness'],
                                 capture_output=True,text=True)
            self.assertEqual(run.returncode,0,run.stderr)
            self.assertIn('MCC_P11_PURE_JAVA_EXACT_LABELS_PASS',run.stdout)

if __name__=='__main__':unittest.main()
