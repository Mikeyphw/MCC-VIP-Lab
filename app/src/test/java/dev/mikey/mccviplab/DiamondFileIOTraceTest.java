package dev.mikey.mccviplab;

import static org.junit.Assert.*;
import java.io.File;
import org.junit.Test;

public class DiamondFileIOTraceTest {
    @Test public void recognizesOnlyTargetDataBasenames() {
        assertEquals("DiamondBalance", DiamondFileIOTrace.label(new File(
                "/data/user/0/br.art.code.meucronogramacapilar/databases/accountkey-DiamondBalance")));
        assertEquals("LastSyncAzure", DiamondFileIOTrace.label(
                "/data/data/br.art.code.meucronogramacapilar/databases/accountkey-LastSyncAzure"));
        assertEquals("LastSyncHasura", DiamondFileIOTrace.label(
                "/data/data/br.art.code.meucronogramacapilar/databases/accountkey-LastSyncHasura"));
        assertNull(DiamondFileIOTrace.label("/data/user/0/other/files/accessToken"));
        assertNull(DiamondFileIOTrace.label("/data/user/0/other/databases/ads.db"));
        assertNull(DiamondFileIOTrace.label(new Object()));
    }
}
