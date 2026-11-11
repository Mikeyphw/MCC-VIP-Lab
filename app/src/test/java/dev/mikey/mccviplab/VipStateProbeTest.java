package dev.mikey.mccviplab;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class VipStateProbeTest {
    @Test public void reportsMissingEntitlementWithoutInventingVip() {
        Map<String,Object> original = new LinkedHashMap<>();
        Map<String,Object> entitlements = new LinkedHashMap<>();
        entitlements.put("active", Collections.emptyMap());
        entitlements.put("all", Collections.emptyMap());
        original.put("entitlements", entitlements);
        String report = VipStateProbe.describe(original);
        assertTrue(report.contains("listed_active=false"));
        assertTrue(report.contains("listed_all=false"));
        assertTrue(report.contains("isActive=unknown"));
    }
    @Test public void reportsSyntheticFixtureAsActiveButNeverVerifiedPurchase() {
        String report = VipStateProbe.describe(VipPayload.fixture());
        assertTrue(report.contains("listed_active=true"));
        assertTrue(report.contains("isActive=true"));
        assertTrue(report.contains("verification=VERIFIED"));
    }
    @Test public void unexpectedShapeIsSafe() {
        assertEquals("shape=non_map", VipStateProbe.describe(null));
        assertEquals("shape=no_entitlements", VipStateProbe.describe(new LinkedHashMap<>()));
    }
}
