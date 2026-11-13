package dev.mikey.mccviplab;

import static org.junit.Assert.*;
import org.junit.Test;

/** Exact P09 lifecycle and root-shell injection safety contracts. */
public class RootRefillCommandTest {
    @Test public void manualSequenceDoesNotRequireBalanceZero() {
        String s = RootRefillCommand.script(false);
        assertTrue(s.contains("am force-stop \"$pkg\""));
        assertTrue(s.contains("printf '%s' '9' > \"$f\""));
        assertTrue(s.contains("am start -a android.intent.action.MAIN"));
        assertTrue(s.indexOf("am force-stop") < s.indexOf("printf '%s' '9'"));
        assertTrue(s.indexOf("printf '%s' '9'") < s.indexOf("MCC_REFILL_P09_SEQUENCE_PASS"));
        assertFalse(s.contains("[ \"$v\" = '0' ]"));
    }
    @Test public void automaticSequenceRefusesNonzero() {
        String s = RootRefillCommand.script(true);
        assertTrue(s.contains("[ \"$v\" = '0' ]"));
        assertTrue(s.indexOf("[ \"$v\" = '0' ]") < s.indexOf("printf '%s' '9'"));
    }
    @Test public void accountScopeAndNoNetworkOrRewardFabrication() {
        String s = RootRefillCommand.script(true);
        assertTrue(s.contains("*-DiamondBalance"));
        assertTrue(s.contains("[ \"$count\" -eq 1 ]"));
        assertTrue(s.contains("[ ! -L \"$candidate\" ]"));
        assertTrue(s.contains("[ \"$bytes\" -eq 1 ]"));
        assertTrue(s.contains("MCC_REFILL_VERIFY_FAILED"));
        for (String unsafe : new String[] {"curl ", "wget ", "http:", "https:",
                "LastSyncAzure", "LastSyncHasura", "ad_id=", "onRewardedAdUserEarnedReward"}) {
            assertFalse(unsafe, s.contains(unsafe));
        }
    }
}
