"""Offline checks for the P10 personal release contracts."""
import pathlib, unittest, subprocess, tempfile, shutil, textwrap

ROOT=pathlib.Path(__file__).resolve().parents[1]
JAVA=ROOT/'app/src/main/java/dev/mikey/mccviplab'

class PersonalTests(unittest.TestCase):
    def test_tested_version_does_not_block_new_version(self):
        s=(JAVA/'HookEntry.java').read_text()
        self.assertIn('record_only=true',s)
        self.assertNotIn('REFUSED_VERSION',s)
        self.assertIn('if (INSTALLED.compareAndSet(false,true))',s)

    def test_inapp_handoff_runs_in_module_uid(self):
        b=(JAVA/'InAppRefillButton.java').read_text()
        a=(JAVA/'RefillControlActivity.java').read_text()
        self.assertIn('setShareIdentityEnabled(true)',b)
        self.assertIn('getLaunchedFromUid()',a)
        self.assertIn('getLaunchedFromPackage()',a)
        self.assertNotIn('ProcessBuilder(',b)
        self.assertNotIn('getContentResolver().call(',b)

    def test_no_shell_relaunch_inside_root_operation(self):
        s=(JAVA/'RootRefillCommand.java').read_text()
        self.assertNotIn('am start',s)
        self.assertIn('MCC_REFILL_WRITE_VERIFIED',s)
        self.assertIn("printf '%s' '9'",s)

    def test_accessibility_anchor_and_fallback(self):
        b=(JAVA/'InAppRefillButton.java').read_text()
        self.assertIn('AccessibilityNodeProvider.HOST_VIEW_ID',b)
        self.assertIn('DiamondButtonPlacement.position',b)
        self.assertIn('diamondLabel',b)
        self.assertIn('dp(activity, 68)',b)

    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'Java compiler absent')
    def test_pure_java_position_algorithm_and_shell_generated(self):
        source=textwrap.dedent('''
          package dev.mikey.mccviplab;
          public class TestHarness {
            public static void main(String[] args) {
              int[] x=DiamondButtonPlacement.position(1080,2000,120,40,8,74,new int[]{764,20,860,56});
              if(x[0]!=636 || x[1]!=18) throw new AssertionError("anchor");
              x=DiamondButtonPlacement.position(1080,2000,120,40,8,74,null);
              if(x[0]!=952 || x[1]!=74) throw new AssertionError("fallback");
              String manual=RootRefillCommand.script(false);
              String auto=RootRefillCommand.script(true);
              if(!manual.contains("MCC_REFILL_WRITE_VERIFIED")) throw new AssertionError("write");
              if(manual.contains("am start")) throw new AssertionError("launch in root shell");
              if(!auto.contains("MCC_REFILL_AUTO_SKIP_NONZERO")) throw new AssertionError("auto scope");
              System.out.println("MCC_PERSONAL_PURE_JAVA_PASS");
            }
          }
        ''')
        with tempfile.TemporaryDirectory() as td:
            p=pathlib.Path(td)/'TestHarness.java'
            p.write_text(source)
            files=[str(JAVA/x) for x in ('DiamondButtonPlacement.java','RootRefillCommand.java')]
            subprocess.run(['javac','-d',td,*files,str(p)],check=True,capture_output=True)
            done=subprocess.run(['java','-cp',td,'dev.mikey.mccviplab.TestHarness'],check=True,capture_output=True,text=True)
            self.assertIn('MCC_PERSONAL_PURE_JAVA_PASS',done.stdout)

if __name__=='__main__': unittest.main()
