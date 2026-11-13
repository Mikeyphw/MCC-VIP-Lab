package dev.mikey.mccviplab;

import static org.junit.Assert.*;
import org.junit.Test;

public class RootRefillCommandTest {
    @Test public void manualMatchesProvenP09WriteAndVerify() {
        String s = RootRefillCommand.script(false);
        assertTrue(s.contains("am force-stop"));
        assertTrue(s.contains("printf '%s' '9' > \"$f\""));
        assertTrue(s.contains("MCC_REFILL_WRITE_VERIFIED"));
        assertTrue(s.indexOf("am force-stop") < s.indexOf("printf '%s' '9'"));
        assertFalse(s.contains("am start"));
        assertFalse(s.contains("getAccessToken"));
    }
    @Test public void automaticRefusesNonzero() {
        String s = RootRefillCommand.script(true);
        assertTrue(s.contains("MCC_REFILL_AUTO_SKIP_NONZERO"));
        assertTrue(s.contains("exit 63"));
        assertFalse(RootRefillCommand.script(false).contains("exit 63"));
    }
    @Test public void rootIdentityAndAccountAmbiguityAreGuarded() {
        String s = RootRefillCommand.script(false);
        assertTrue(s.contains("id -u"));
        assertTrue(s.contains("exit 70"));
        assertTrue(s.contains("exit 53"));
        assertTrue(s.contains("exit 52"));
        assertTrue(s.contains("exit 59"));
    }
}
