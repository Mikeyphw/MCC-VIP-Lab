package dev.mikey.mccviplab;

import static org.junit.Assert.*;
import java.io.File;
import org.junit.Test;

public class DiamondFileIOTraceTest {
    @Test public void recognizesOnlyTargetDataBasenames() {
        assertEquals("DiamondBalance", DiamondFileLabel.label(new File(
                "/data/user/0/br.art.code.meucronogramacapilar/databases/accountkey-DiamondBalance")));
        assertEquals("LastSyncAzure", DiamondFileLabel.label(
                "/data/data/br.art.code.meucronogramacapilar/databases/accountkey-LastSyncAzure"));
        assertEquals("LastSyncHasura", DiamondFileLabel.label(
                "/data/data/br.art.code.meucronogramacapilar/databases/accountkey-LastSyncHasura"));
        assertNull(DiamondFileLabel.label("/data/user/0/other/files/accessToken"));
        assertNull(DiamondFileLabel.label("/data/user/0/other/databases/ads.db"));
        assertNull(DiamondFileLabel.label(new Object()));
    }
}
