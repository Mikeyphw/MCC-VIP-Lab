package dev.mikey.mccviplab;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class VipPayloadTest {
    @SuppressWarnings("unchecked")
    @Test public void injectsEntitlementButDoesNotMutateOriginal() {
        Map<String,Object> original=new LinkedHashMap<>();
        Map<String,Object> ents=new LinkedHashMap<>();
        ents.put("all",Collections.emptyMap());
        ents.put("active",Collections.emptyMap());
        original.put("entitlements",ents);
        original.put("activeSubscriptions",Collections.emptyList());
        original.put("allPurchasedProductIdentifiers",Collections.emptyList());
        original.put("allPurchaseDates",Collections.emptyMap());
        original.put("allExpirationDates",Collections.emptyMap());
        original.put("firstSeen","2025-01-01T01:00:00Z");
        original.put("originalAppUserId","original-user-id");
        Map<String,Object> patched=VipPayload.patch(original);
        assertNotNull(patched);
        assertEquals("original-user-id",patched.get("originalAppUserId"));
        Map<String,Object> newEnts=(Map<String,Object>)patched.get("entitlements");
        Map<String,Object> active=(Map<String,Object>)newEnts.get("active");
        assertTrue(active.containsKey("premium_noads"));
        assertEquals(Boolean.TRUE,((Map<String,Object>)active.get("premium_noads")).get("isActive"));
        assertTrue(((List<?>)patched.get("activeSubscriptions")).contains("premium_noads"));
        assertFalse(((Map<?,?>)ents.get("active")).containsKey("premium_noads"));
        assertTrue(((Map<?,?>)patched.get("allExpirationDates")).containsKey("premium_noads"));
    }

    @Test public void offlineFixtureHasRequiredCustomerInfoFields() {
        Map<String,Object> fixture=VipPayload.fixture();
        assertNotNull(fixture.get("entitlements"));
        for(String key : new String[]{"firstSeen","originalAppUserId","requestDate","allPurchaseDates",
           "allExpirationDates","nonSubscriptionTransactions","activeSubscriptions","allPurchasedProductIdentifiers"})
            assertNotNull(key,fixture.get(key));
        assertNull(VipPayload.patch("not-a-map"));
        assertNull(VipPayload.patch(Collections.singletonMap("unrelated",true)));
    }
}
